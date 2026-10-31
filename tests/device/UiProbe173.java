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
public final class UiProbe173 extends Instrumentation {
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
        SystemClock.sleep(650);Bitmap mid=crop(pixels(),input);
        check(same(first,mid),"Hardware-rendered single character remains visible before 1500 ms");
        SystemClock.sleep(950);Bitmap timed=crop(pixels(),input);save(timed,"mask-after-timeout");
        ui(()->{input.setText("");input.setText("7");((SecretInput)input).hideAll();});SystemClock.sleep(180);Bitmap reference=crop(pixels(),input);save(reference,"mask-reference");save(first,"mask-visible");
        check(!same(first,reference),"Hardware capture distinguishes visible digit from asterisk");
        check(same(timed,reference),"Hardware-rendered digit automatically becomes asterisk without another key");
        ui(()->{input.getText().append("8");check(input.getTransformationMethod().getTransformation(input.getText(),input).toString().equals("*8"),"Typing next character immediately masks the previous one");input.setCursorVisible(true);input.setText("");activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);});
    }
    @Override public void onStart(){
        try{
            Context c=getTargetContext();check(c.getPackageName().equals("com.alexcantini.tvvault.preview"),"Separate preview UID only");
            ((Application)c.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){activity=a;}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}});
            PinStore pin=new PinStore(c);
            if(!cold){
                c.getSharedPreferences("tvvault_pin",0).edit().clear().commit();c.getSharedPreferences("tvvault_secure",0).edit().clear().commit();c.getSharedPreferences("tvvault_disclosure",0).edit().putBoolean("hide",true).commit();
                pin.set("1234");CryptoStore vault=new CryptoStore(c);vault.load();ArrayList<Credential> fake=new ArrayList<>();fake.add(new Credential("Demo","","demo@example.invalid","x"));fake.add(new Credential("Other","","other@example.invalid","y"));vault.save(fake);
                check(pin.isAutofillPinRequired(),"Autofill PIN defaults to required");main();unlock();waitText(R.string.select_app);
                activity=startActivitySync(new Intent(c,PinSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));waitForIdleSync();waitText(R.string.unlock);unlock();waitText(R.string.require_autofill_pin);
                check(((CheckBox)find(R.string.require_autofill_pin)).isChecked(),"Settings checkbox reflects protected default");
                click(R.string.change_pin);maskRendering();click(R.string.back);waitText(R.string.require_autofill_pin);
                click(R.string.require_autofill_pin);waitText(R.string.continue_action);
                check(pin.isAutofillPinRequired(),"Checkbox change does not persist before authorization");
                enter("0000");click(R.string.continue_action);waitText(R.string.pin_wrong);check(pin.isAutofillPinRequired(),"Wrong PIN cannot disable Autofill gate");
                enter("1234");click(R.string.continue_action);waitText(R.string.require_autofill_pin);check(!((CheckBox)find(R.string.require_autofill_pin)).isChecked(),"Correct PIN saves unchecked option");
                check(pin.isEnabled(),"Disabling Autofill PIN preserves vault PIN");
            }else{
                check(pin.isEnabled()&&!pin.isAutofillPinRequired(),"Independent Autofill choice persists across full process restart");
            }
            ProbeHostActivity h=host();waitText(R.string.choose_credential);
            check(activity instanceof AutofillAuthActivity&&fields().isEmpty(),"Autofill opens credential picker without PIN when unchecked");
            check(h.resultCode==-999,"User credential selection is preserved");selectDemo();dataset(h);ui(h::finish);
            main();unlock();waitText(R.string.select_app);
            if(!cold){
                activity=startActivitySync(new Intent(c,PinSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));waitForIdleSync();waitText(R.string.unlock);unlock();waitText(R.string.require_autofill_pin);
                click(R.string.require_autofill_pin);waitText(R.string.continue_action);enter("1234");click(R.string.continue_action);waitText(R.string.require_autofill_pin);check(((CheckBox)find(R.string.require_autofill_pin)).isChecked(),"Autofill PIN can be restored with current PIN");
                ProbeHostActivity protectedHost=host();waitText(R.string.unlock);check(activity instanceof AutofillAuthActivity,"Checked option requires PIN for Autofill");unlock();waitText(R.string.choose_credential);selectDemo();dataset(protectedHost);ui(protectedHost::finish);
                check(pin.setAutofillPinRequired(false,"1234"),"Synthetic setup for restart phase is authorized");
            }
            results.putString("result","PASS");results.putInt("checks_passed",count);
        }catch(Throwable error){results.putString("result","FAIL");results.putString("error",error.toString());StringBuilder stack=new StringBuilder();for(StackTraceElement e:error.getStackTrace())stack.append(e).append("\n");results.putString("stack",stack.toString());}
        finally{if(activity!=null)ui(()->activity.finish());finish(Activity.RESULT_OK,results);}
    }
}
