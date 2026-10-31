// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real-window rendering and synthetic Autofill host tests, restricted to .preview UID. */
public final class UiProbe175 extends Instrumentation {
    private volatile Activity activity;
    private boolean cold;
    private int count;
    private final Bundle results=new Bundle();
    @Override public void onCreate(Bundle args){super.onCreate(args);cold=args!=null&&"cold".equals(args.getString("phase"));start();}
    private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);results.putString("check_"+(++count),name);Bundle status=new Bundle();status.putString("stream","PASS "+name+"\n");sendStatus(0,status);}
    private void ui(Runnable task){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{task.run();}catch(Throwable t){error.set(t);}});waitForIdleSync();if(error.get()!=null)throw new AssertionError(error.get());}
    private List<View> views(View root){List<View> all=new ArrayList<>();all.add(root);if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++)all.addAll(views(g.getChildAt(i)));}return all;}
    private View find(int id){String text=activity.getString(id);for(View v:views(activity.getWindow().getDecorView()))if(v instanceof TextView&&((TextView)v).getText().toString().equals(text)&&v.isShown())return v;throw new AssertionError("Missing view "+text);}
    private void click(int id){ui(()->{View v=find(id);while(!v.isClickable()&&v.getParent() instanceof View)v=(View)v.getParent();v.performClick();});}
    private List<EditText> fields(){List<EditText> list=new ArrayList<>();for(View v:views(activity.getWindow().getDecorView()))if(v instanceof EditText&&v.isShown())list.add((EditText)v);return list;}
    private void enter(String text){ui(()->fields().get(0).setText(text));}
    private void waitText(int id){long end=SystemClock.uptimeMillis()+8000;while(SystemClock.uptimeMillis()<end){AtomicReference<Boolean> ok=new AtomicReference<>(false);ui(()->{try{find(id);ok.set(true);}catch(AssertionError ignored){}});if(ok.get())return;SystemClock.sleep(80);}throw new AssertionError("Timed out "+activity.getString(id));}
    private void unlock(){enter("1234");click(R.string.unlock);}
    private void waitType(Class<?> type){long end=SystemClock.uptimeMillis()+8000;while(SystemClock.uptimeMillis()<end){if(type.isInstance(activity))return;SystemClock.sleep(80);}throw new AssertionError("Activity did not resume: "+type.getSimpleName());}
    private void main(){Context c=getTargetContext();activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));waitForIdleSync();waitText(R.string.unlock);check(activity instanceof MainActivity,"Opening vault still requires PIN independently of Autofill option");}
    private ProbeHostActivity host(){ProbeHostActivity h=(ProbeHostActivity)startActivitySync(new Intent(getTargetContext(),ProbeHostActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));waitForIdleSync();ui(h::requestFill);waitType(AutofillAuthActivity.class);return h;}
    private void selectDemo(){ui(()->{for(View v:views(activity.getWindow().getDecorView()))if(v instanceof android.widget.Button&&((TextView)v).getText().toString().startsWith("Demo\n")){v.performClick();return;}throw new AssertionError("Missing synthetic credential");});}
    private void dataset(ProbeHostActivity host){long end=SystemClock.uptimeMillis()+5000;while(host.resultCode==-999&&SystemClock.uptimeMillis()<end)SystemClock.sleep(80);check(host.resultCode==Activity.RESULT_OK&&host.returnedDataset,"Synthetic host receives real Autofill Dataset result");}
    private Bitmap pixels(){AtomicReference<Bitmap> image=new AtomicReference<>();CountDownLatch ready=new CountDownLatch(1);int[] result={-1};ui(()->{View root=activity.getWindow().getDecorView();Bitmap bmp=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);image.set(bmp);PixelCopy.request(activity.getWindow(),bmp,code->{result[0]=code;ready.countDown();},new Handler(Looper.getMainLooper()));});try{if(!ready.await(5,TimeUnit.SECONDS)||result[0]!=PixelCopy.SUCCESS)throw new AssertionError("PixelCopy failed "+result[0]);}catch(InterruptedException e){throw new AssertionError(e);}return image.get();}
    private Bitmap crop(Bitmap window,EditText input){int[] xy=new int[2];ui(()->input.getLocationInWindow(xy));return Bitmap.createBitmap(window,xy[0],xy[1],input.getWidth(),input.getHeight());}
    private boolean same(Bitmap a,Bitmap b){if(a.getWidth()!=b.getWidth()||a.getHeight()!=b.getHeight())return false;int[] pa=new int[a.getWidth()*a.getHeight()],pb=new int[pa.length];a.getPixels(pa,0,a.getWidth(),0,0,a.getWidth(),a.getHeight());b.getPixels(pb,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());return Arrays.equals(pa,pb);}
    private void save(Bitmap bitmap,String name){try(FileOutputStream file=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}catch(Exception e){throw new AssertionError(e);}}
    private void maskRendering(){
        EditText input=fields().get(0);
        // Clear capture protection ONLY on this synthetic preview window; never on the real app.
        ui(()->{activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);input.setCursorVisible(false);input.requestFocus();input.setText("");input.getText().append("7");});
        SystemClock.sleep(180);Bitmap first=crop(pixels(),input);
        SystemClock.sleep(300);Bitmap mid=crop(pixels(),input);
        check(same(first,mid),"Hardware-rendered single character remains visible before 1000 ms");
        SystemClock.sleep(750);Bitmap timed=crop(pixels(),input);save(timed,"mask-after-timeout");
        ui(()->{input.setText("");input.setText("7");((SecretInput)input).hideAll();});SystemClock.sleep(180);Bitmap reference=crop(pixels(),input);save(reference,"mask-reference");save(first,"mask-visible");
        check(!same(first,reference),"Hardware capture distinguishes visible digit from asterisk");
        check(same(timed,reference),"Hardware-rendered digit automatically becomes asterisk without another key");
        ui(()->{input.getText().append("8");check(input.getTransformationMethod().getTransformation(input.getText(),input).toString().equals("*8"),"Typing next character immediately masks the previous one");input.setCursorVisible(true);input.setText("");activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);});
    }
    private Object state(Activity a,String name) {try {java.lang.reflect.Field f=LockedActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    private void expireSettings(Activity a){try{java.lang.reflect.Field f=LockedActivity.class.getDeclaredField("settingsDeadline");f.setAccessible(true);f.setLong(a,0);}catch(Exception e){throw new AssertionError(e);}}
    private void screenOff(Activity a){((android.content.BroadcastReceiver)state(a,"screenReceiver")).onReceive(a,new Intent(Intent.ACTION_SCREEN_OFF));}
    private void systemBack(){check(getUiAutomation().performGlobalAction(1),"Android system Back action accepted");}
    private void settingsTrip(int invalidation) {
        MainActivity main=(MainActivity)activity;
        click(R.string.setup_autofill);SystemClock.sleep(900);
        check((Boolean)state(main,"autofillSettingsPending"),"Authenticated system settings request is tracked");
        try {java.lang.reflect.Field f=MainActivity.class.getDeclaredField("credentials");f.setAccessible(true);check(((java.util.List<?>)f.get(main)).isEmpty(),"Credentials cleared while system settings are open");}catch(Exception e){throw new AssertionError(e);}
        if(invalidation==1)ui(()->screenOff(main));
        if(invalidation==2)ui(()->expireSettings(main));
        systemBack();
        if(invalidation==0) {waitText(R.string.select_app);check(main.isVaultUnlocked(),"System Autofill return retains existing unlock without repeated PIN");}
        else {waitText(R.string.unlock);check(!main.isVaultUnlocked(),invalidation==1?"Screen-off revokes pending settings exception":"Expired settings exception cannot restore unlock");unlock();waitText(R.string.select_app);}
    }
    @Override public void onStart(){
        try {
            Context c=getTargetContext();check(c.getPackageName().equals("com.alexcantini.tvvault.preview"),"Separate preview UID only");
            ((Application)c.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){activity=a;}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}});
            PinStore pin=new PinStore(c);
            if(!cold){
                c.getSharedPreferences("tvvault_pin",0).edit().clear().commit();c.getSharedPreferences("tvvault_secure",0).edit().clear().commit();c.getSharedPreferences("tvvault_disclosure",0).edit().putBoolean("hide",true).commit();pin.set("1234");
                CryptoStore vault=new CryptoStore(c);vault.load();ArrayList<Credential> fake=new ArrayList<>();fake.add(new Credential("Demo","","demo@example.invalid","x"));fake.add(new Credential("Other","","other@example.invalid","y"));vault.save(fake);
                for(String lang:new String[]{"it","en"}) {android.content.res.Configuration config=new android.content.res.Configuration(c.getResources().getConfiguration());config.setLocale(new java.util.Locale(lang));Context localized=c.createConfigurationContext(config);check(localized.getString(R.string.home).equals("Home"),"Home label in "+lang);check(localized.getString(R.string.pin_wrong).equals(lang.equals("it")?"Pin errato":"Incorrect PIN"),"Wrong PIN message in "+lang);check(localized.getString(R.string.pin_format).equals(lang.equals("it")?"Formato Pin non valido":"Invalid PIN format"),"Invalid PIN format message in "+lang);}
                main();check(find(R.string.exit).isShown(),"Initial PIN gate has Exit");boolean home=false;try{find(R.string.home);home=true;}catch(AssertionError ignored){}check(!home,"Initial PIN gate has no Home");Activity closed=activity;click(R.string.exit);check(closed.isFinishing(),"Exit closes initial unlock activity");
                main();enter("12");click(R.string.unlock);waitText(R.string.pin_format);check(!((MainActivity)activity).isVaultUnlocked(),"Two-digit PIN shows invalid format without unlocking");enter("0000");click(R.string.unlock);waitText(R.string.pin_wrong);check(!((MainActivity)activity).isVaultUnlocked(),"Incorrect PIN shows distinct error without unlocking");unlock();waitText(R.string.select_app);
                click(R.string.vault);waitText(R.string.add_credential);click(R.string.home);waitText(R.string.select_app);check(true,"Vault Home returns to initial page");
                click(R.string.excluded_apps);waitText(R.string.exclusions_hint);click(R.string.home);waitText(R.string.select_app);check(true,"Excluded apps Home returns to initial page");
                click(R.string.select_app);waitText(R.string.all_apps);click(R.string.home);waitText(R.string.select_app);check(true,"App picker Home returns to initial page rather than editor");
                click(R.string.vault);waitText(R.string.add_credential);click(R.string.add_credential);waitText(R.string.save);click(R.string.home);waitText(R.string.select_app);check(new CryptoStore(c).load().size()==2,"Credential editor Home leaves synthetic vault unchanged");
                String provider=android.provider.Settings.Secure.getString(c.getContentResolver(),"autofill_service");settingsTrip(0);settingsTrip(1);settingsTrip(2);String after=android.provider.Settings.Secure.getString(c.getContentResolver(),"autofill_service");check(java.util.Objects.equals(provider,after),"Real TV Autofill provider unchanged by canceled settings tests");
                Activity main=activity;Activity other=startActivitySync(new Intent(c,ProbeHostActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));waitForIdleSync();ui(other::finish);waitType(MainActivity.class);waitText(R.string.unlock);check(!((MainActivity)activity).isVaultUnlocked(),"Ordinary background return still requires PIN");unlock();waitText(R.string.select_app);
                click(R.string.security_settings);waitType(PinSettingsActivity.class);waitText(R.string.unlock);check(find(R.string.home).isShown(),"Settings unlock keeps Home");unlock();waitText(R.string.require_autofill_pin);click(R.string.require_autofill_pin);SystemClock.sleep(300);check(!pin.isAutofillPinRequired(),"Autofill PIN option still changes without repeated PIN in settings visit");click(R.string.change_pin);waitText(R.string.save_pin);check(fields().size()==2,"PIN change still uses authorized visit");maskRendering();click(R.string.home);waitType(MainActivity.class);waitText(R.string.select_app);check(!((MainActivity)activity).isVaultUnlocked(),"Settings Home does not unlock main vault");
                click(R.string.security_settings);waitType(PinSettingsActivity.class);waitText(R.string.unlock);check(!((PinSettingsActivity)activity).isVaultUnlocked(),"Re-entering settings requires a new entry PIN");click(R.string.home);waitType(MainActivity.class);waitText(R.string.select_app);
            }else{check(pin.isEnabled()&&!pin.isAutofillPinRequired(),"PIN and independent Autofill choice persist across process restart");}
            ProbeHostActivity h=host();waitText(R.string.choose_credential);check(fields().isEmpty(),"Unchecked Autofill PIN option opens only Autofill without PIN");selectDemo();dataset(h);ui(h::finish);
            main();unlock();waitText(R.string.select_app);check(((MainActivity)activity).isVaultUnlocked(),"App entry remains protected independently of Autofill option");
            results.putString("result","PASS");results.putInt("checks_passed",count);
        }catch(Throwable error){results.putString("result","FAIL");results.putString("error",error.toString());StringBuilder stack=new StringBuilder();for(StackTraceElement e:error.getStackTrace())stack.append(e).append("\n");results.putString("stack",stack.toString());}
        finally{if(activity!=null)ui(()->activity.finish());finish(Activity.RESULT_OK,results);}
    }
}
