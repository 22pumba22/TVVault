// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** This runner exists ONLY in the separate .preview application, never in the deliverable. */
public class UiProbe extends Instrumentation {
    private volatile Activity activity;
    private volatile boolean activityStopped;
    private final Bundle results = new Bundle();
    private int checks;
    private boolean skipBench;
    private boolean coldPhase;
    @Override public void onCreate(Bundle args) { super.onCreate(args); skipBench = args != null && "true".equals(args.getString("fast"));coldPhase=args!=null&&"cold".equals(args.getString("phase")); start(); }
    private void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        results.putString("check_" + (++checks),message);
        Bundle status = new Bundle(); status.putString("stream","PASS " + message + "\n"); sendStatus(0,status);
    }
    private void metric(String key,long value) {
        results.putLong(key,value); Bundle status=new Bundle();status.putString("stream",key+"="+value+" ms\n");sendStatus(0,status);
    }
    private void ui(Runnable action) {
        AtomicReference<Throwable> error=new AtomicReference<>();
        runOnMainSync(()->{try{action.run();}catch(Throwable t){error.set(t);}});waitForIdleSync();
        if(error.get()!=null)throw new AssertionError(error.get());
    }
    private List<View> all(View root) {
        List<View> out = new ArrayList<>(); out.add(root);
        if (root instanceof ViewGroup) { ViewGroup group=(ViewGroup)root; for(int i=0;i<group.getChildCount();i++)out.addAll(all(group.getChildAt(i))); }
        return out;
    }
    private View find(int id) {
        String text = activity.getString(id);
        for(View v:all(activity.getWindow().getDecorView()))
            if(v instanceof TextView && ((TextView)v).getText().toString().equals(text) && v.isShown()) return v;
        throw new AssertionError("Missing view: " + text);
    }
    private void click(int id) { ui(()-> {
        View view=find(id);
        while(!view.isClickable() && view.getParent() instanceof View) view=(View)view.getParent();
        if(!view.isClickable()) throw new AssertionError("View is not clickable: "+activity.getString(id));
        view.performClick();
    }); }
    private List<EditText> fields() {
        ArrayList<EditText> out=new ArrayList<>();
        for(View v:all(activity.getWindow().getDecorView()))if(v instanceof EditText && v.isShown())out.add((EditText)v);
        return out;
    }
    private void enter(int index,String value) { ui(()->fields().get(index).setText(value)); }
    private void launch() {
        Intent i=new Intent(getTargetContext(),MainActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=startActivitySync(i); waitForIdleSync(); SystemClock.sleep(200);
    }
    private void shot(String name) {
        ui(()-> {
            try {
                View view=activity.getWindow().getDecorView();
                Bitmap bmp=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
                view.draw(new Canvas(bmp));
                try(FileOutputStream file=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){bmp.compress(Bitmap.CompressFormat.PNG,100,file);}
                bmp.recycle();
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private void waitForText(int id) {
        long end=SystemClock.uptimeMillis()+15000;
        while(SystemClock.uptimeMillis()<end) {
            AtomicReference<Boolean> found=new AtomicReference<>(false);
            ui(()->{try{find(id);found.set(true);}catch(AssertionError e){}});
            if(found.get())return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out: "+activity.getString(id));
    }
    private void screenEvent(Context context,String action) {
        ui(()-> {
            try {
                java.lang.reflect.Field field=LockedActivity.class.getDeclaredField("screenReceiver");field.setAccessible(true);
                ((android.content.BroadcastReceiver)field.get(activity)).onReceive(context,new Intent(action));
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private void runColdPhase(Context context) {
        require(new PinStore(context).isEnabled(),"PIN stays enabled after full process stop/restart");
        require(context.getSharedPreferences("tvvault_disclosure",0).getBoolean("hide",false),"Disclaimer hide preference persists independently");
        launch();waitForText(R.string.unlock);require(fields().size()==1,"Cold process launch requires PIN despite hidden disclaimer");
        shot("cold-start-unlock");
        enter(0,"1234");click(R.string.unlock);waitForText(R.string.select_app);
        require(find(R.string.home_pin_active).isShown(),"Correct PIN unlocks after full process restart");
        click(R.string.vault);
        require(all(activity.getWindow().getDecorView()).stream().anyMatch(v->v instanceof TextView&&((TextView)v).getText().toString().equals("Demo")),"Encrypted synthetic credentials survive process restart");
    }
    @Override public void onStart() {
        try {
            Context context=getTargetContext();
            ((android.app.Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
                public void onActivityCreated(Activity a,Bundle b){}
                public void onActivityStarted(Activity a){}
                public void onActivityResumed(Activity a){activity=a;activityStopped=false;}
                public void onActivityPaused(Activity a){}
                public void onActivityStopped(Activity a){if(a==activity)activityStopped=true;}
                public void onActivitySaveInstanceState(Activity a,Bundle b){}
                public void onActivityDestroyed(Activity a){}
            });
            require(context.getPackageName().equals("com.alexcantini.tvvault.preview"),"Independent preview UID/package; no real vault access");
            if(coldPhase) { runColdPhase(context); results.putInt("checks_passed",checks);results.putString("result","PASS");return; }
            SharedPreferences prefs=context.getSharedPreferences("tvvault_pin",0);
            prefs.edit().clear().commit();
            PinStore pin=new PinStore(context);
            long then;
            if (!skipBench) {
            byte[] salt=new byte[32]; salt[0]=42;
            then=SystemClock.elapsedRealtime();
            PBEKeySpec spec=new PBEKeySpec("1234".toCharArray(),salt,210000,256);
            byte[] hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();spec.clearPassword();
            metric("legacy_derive_ms",SystemClock.elapsedRealtime()-then);
            prefs.edit().putBoolean("enabled",true).putString("salt",Base64.encodeToString(salt,2)).putString("hash",Base64.encodeToString(hash,2)).commit();
            then=SystemClock.elapsedRealtime();require(pin.verify("1234"),"Existing PIN migration works on Android Keystore");
            metric("legacy_unlock_and_migrate_ms",SystemClock.elapsedRealtime()-then);
            then=SystemClock.elapsedRealtime();pin.set("1234");metric("new_pin_save_ms",SystemClock.elapsedRealtime()-then);
            then=SystemClock.elapsedRealtime();require(pin.verify("1234"),"New PIN verifies on actual Android Keystore");metric("new_pin_verify_ms",SystemClock.elapsedRealtime()-then);
            pin.disable();
            }
            context.getSharedPreferences("tvvault_secure",0).edit().clear().commit();
            context.getSharedPreferences("tvvault_disclosure",0).edit().clear().commit();
            CryptoStore store=new CryptoStore(context);store.load();
            then=SystemClock.elapsedRealtime();store.save(java.util.Collections.singletonList(new Credential("Demo", "", "demo@example.invalid", "x")));
            metric("credential_save_ms",SystemClock.elapsedRealtime()-then);
            String archive=context.getSharedPreferences("tvvault_secure",0).getString("vault","");
            launch();shot("disclaimer");click(R.string.refuse);
            SystemClock.sleep(300);
            require(activity.isFinishing(),"Decline exits application");
            require(archive.equals(context.getSharedPreferences("tvvault_secure",0).getString("vault","")),"Decline preserves encrypted vault");
            launch();click(R.string.dont_show_again);click(R.string.accept);
            require(context.getSharedPreferences("tvvault_disclosure",0).getBoolean("hide",false),"Accept remembers checkbox choice");
            Activity savedMain=activity;
            shot("home");
            require(find(R.string.home_pin_unset).isShown(),"Home clearly reports PIN not configured");
            click(R.string.exit);SystemClock.sleep(300);launch();
            require(find(R.string.select_app).isShown(),"PIN-disabled app remains optional and accessible");
            savedMain=activity;
            ui(()-> {
                View root=activity.getWindow().getDecorView();
                require(all(root).stream().noneMatch(v->v instanceof ScrollView),"Home contains no scrolling container");
                View exit=find(R.string.exit); int[] xy=new int[2];exit.getLocationOnScreen(xy);
                require(xy[0]>root.getWidth()/2 && xy[1]<root.getHeight()/3,"Exit is at top right");
                for(int id:new int[]{R.string.select_app,R.string.vault,R.string.excluded_apps,R.string.security_settings,R.string.setup_autofill}) {
                    View v=find(id);Rect rect=new Rect();require(v.getGlobalVisibleRect(rect)&&rect.height()==v.getHeight(),"Home item fully visible: "+activity.getString(id));
                }
                require(all(root).stream().noneMatch(v->v instanceof TextView&&((TextView)v).getText().toString().contains("Alex Cantini")),"No author footer on Home");
            });
            Intent settings=new Intent(context,PinSettingsActivity.class);settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(settings);waitForIdleSync();
            require(fields().size()==2,"PIN-off settings show new/confirm fields");
            ui(()->{EditText f=fields().get(0);f.requestFocus();f.getText().append("1");
                require(f.getTransformationMethod().getTransformation(f.getText(),f).toString().equals("1"),"Newly typed character briefly visible");});
            SystemClock.sleep(800);
            ui(()->{EditText f=fields().get(0);require(f.getTransformationMethod().getTransformation(f.getText(),f).toString().equals("*"),"Character becomes an asterisk");});
            enter(0,"1234");enter(1,"1234");click(R.string.enable_pin);waitForText(R.string.change_pin);
            require(fields().isEmpty(),"Active PIN hides new/confirm fields");shot("pin-active");
            click(R.string.change_pin);require(fields().size()==1,"Change requires current PIN first");
            enter(0,"0000");click(R.string.continue_action);waitForText(R.string.pin_wrong);
            require(pin.verify("1234"),"Wrong current PIN leaves existing PIN unchanged");
            enter(0,"1234");click(R.string.continue_action);waitForText(R.string.save_pin);
            require(fields().size()==2,"New fields appear only after correct current PIN");
            enter(0,"5678");enter(1,"5678");click(R.string.save_pin);waitForText(R.string.change_pin);
            require(pin.verify("5678")&&!pin.verify("1234"),"Change installs new PIN and rejects old PIN");
            click(R.string.disable_pin);require(fields().size()==1,"Disable requires current PIN");
            enter(0,"1234");click(R.string.continue_action);waitForText(R.string.pin_wrong);
            require(pin.isEnabled(),"Wrong current PIN cannot disable protection");
            enter(0,"5678");click(R.string.continue_action);waitForText(R.string.enable_pin);
            require(!pin.isEnabled(),"Correct current PIN disables protection");shot("pin-disabled");
            ui(()->activity.finish());SystemClock.sleep(300);
            activity=savedMain;waitForIdleSync();click(R.string.vault);
            View demo=null;for(View v:all(activity.getWindow().getDecorView()))if(v instanceof TextView&&((TextView)v).getText().toString().equals("Demo"))demo=v;
            require(demo!=null,"Saved synthetic credential remains after UI tests");
            View row=(View)demo.getParent().getParent();ui(row::performClick);
            ui(()-> {
                for(EditText f:fields())if(f instanceof SecretInput)require(f.getTransformationMethod().getTransformation(f.getText(),f).toString().equals("*"),"Stored password masked immediately on edit");
            });
            shot("editor");
            ui(()-> {
                android.content.res.Configuration config=new android.content.res.Configuration(activity.getResources().getConfiguration());
                config.setLocale(java.util.Locale.ENGLISH);
                activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());
                ((MainActivity)activity).onUnlocked();
                require(((TextView)find(R.string.security_settings)).getText().toString().equals("Security and PIN"),"English UI resources render correctly");
            });
            shot("home-english");
            pin.set("1234");
            ui(()->activity.finish());SystemClock.sleep(200);launch();
            require(fields().size()==1,"Enabled PIN gates new entry");
            enter(0,"1234");click(R.string.unlock);waitForText(R.string.select_app);
            require(find(R.string.select_app).isShown(),"Correct PIN opens Home");
            ui(()->activity.moveTaskToBack(true));
            long stopDeadline=SystemClock.uptimeMillis()+10000;
            while(!activityStopped && SystemClock.uptimeMillis()<stopDeadline)SystemClock.sleep(100);
            require(activityStopped,"Preview activity reaches stopped/background state");
            ui(()->context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
            SystemClock.sleep(300);waitForText(R.string.unlock);
            require(fields().size()==1,"Background return requires PIN again");
            shot("unlock");
            ui(()-> {
                android.widget.Button unlock=(android.widget.Button)find(R.string.unlock);
                require(unlock.getTextColors().getDefaultColor()==android.graphics.Color.WHITE,"Unlock button text is white");
                require(unlock.getBackground() instanceof android.graphics.drawable.StateListDrawable,"Unlock button has visible focus/pressed states");
            });
            enter(0,"1234");click(R.string.unlock);waitForText(R.string.select_app);
            require(find(R.string.home_pin_active).isShown(),"Home clearly reports active PIN protection");
            final Activity reused=activity;
            ui(()->context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP)));
            waitForText(R.string.unlock);require(activity==reused,"Launcher reuses same activity for new-intent test");
            require(fields().size()==1,"New launcher intent relocks a reused activity");
            enter(0,"1234");click(R.string.unlock);waitForText(R.string.select_app);
            screenEvent(context,Intent.ACTION_SCREEN_OFF);
            require(!((LockedActivity)activity).isVaultUnlocked(),"Screen-off invalidates unlocked access without waiting for onStop");
            screenEvent(context,Intent.ACTION_SCREEN_ON);waitForText(R.string.unlock);
            require(fields().size()==1,"Screen-on requests PIN while activity remains alive");
            // Keep this synthetic PIN enabled so the separate cold-process phase can verify persistence.
            shot("standby-unlock");
            results.putInt("checks_passed",checks);results.putString("result","PASS");
        } catch(Throwable error) {
            results.putString("result","FAIL");results.putString("error",error.toString());
            StringBuilder stack=new StringBuilder();for(StackTraceElement e:error.getStackTrace())stack.append(e.toString()).append("\n");results.putString("stack",stack.toString());
            try{if(activity!=null)shot("failure");}catch(Throwable ignored){}
        } finally {
            if(activity!=null)ui(()->activity.finish());
            finish(Activity.RESULT_OK,results);
        }
    }
}

