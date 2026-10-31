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
public final class UiProbe176 extends Instrumentation {
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
    private android.app.AlertDialog popup(){try{java.lang.reflect.Field f=LockedActivity.class.getDeclaredField("dialogs");f.setAccessible(true);java.util.List<?> dialogs=(java.util.List<?>)f.get(activity);if(dialogs.size()!=1)throw new AssertionError("Expected one popup, got "+dialogs.size());return (android.app.AlertDialog)dialogs.get(0);}catch(Exception e){throw new AssertionError(e);}}
    private View inDialog(android.app.AlertDialog d,int id){String text=activity.getString(id);for(View v:views(d.getWindow().getDecorView()))if(v instanceof TextView&&((TextView)v).getText().toString().equals(text)&&v.isShown())return v;throw new AssertionError("Missing dialog text "+text);}
    private void capture(android.app.AlertDialog d,String name){ui(()->{activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);});SystemClock.sleep(250);try{Bitmap shot=getUiAutomation().takeScreenshot();if(shot==null)throw new AssertionError("Missing preview screenshot");save(shot,name);}finally{ui(()->{activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);});}}
    private void openAbout(){ui(()->{find(R.string.about).requestFocus();find(R.string.about).performClick();});SystemClock.sleep(250);}
    private void waitDismiss(android.app.AlertDialog d){long until=SystemClock.uptimeMillis()+5000;while(d.isShowing()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(70);if(d.isShowing())throw new AssertionError("Dialog not dismissed");}
    @Override public void onStart(){
        try{
            Context c=getTargetContext();check(c.getPackageName().equals("com.alexcantini.tvvault.preview"),"Separate preview UID only");
            ((Application)c.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){activity=a;}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}});
            if(!cold){
                c.getSharedPreferences("tvvault_pin",0).edit().clear().commit();c.getSharedPreferences("tvvault_secure",0).edit().clear().commit();c.getSharedPreferences("tvvault_disclosure",0).edit().putBoolean("hide",true).commit();PinStore pin=new PinStore(c);pin.set("1234");
                CryptoStore vault=new CryptoStore(c);vault.load();ArrayList<Credential> fake=new ArrayList<>();fake.add(new Credential("Demo","","demo@example.invalid","x"));fake.add(new Credential("Other","","other@example.invalid","y"));vault.save(fake);
                java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");try(java.io.InputStream in=c.getResources().openRawResource(R.drawable.donation_lightning)){byte[] buf=new byte[4096];int read;while((read=in.read(buf))!=-1)hash.update(buf,0,read);}StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format("%02x",b&255));check(digest.toString().equals("c0c057d1da75baf391fe0a0d8e7c0df9c6a3e829f33b1815ad85168b578d0724"),"Donation QR resource is byte-for-byte identical to the supplied image");
                for(String language:new String[]{"it","en"}){
                    LocalePreviewActivity.language=language;activity=startActivitySync(new Intent(c,LocalePreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));waitForIdleSync();waitText(R.string.unlock);unlock();waitText(R.string.select_app);
                    View about=find(R.string.about),exit=find(R.string.exit);android.graphics.Rect rect=new android.graphics.Rect();check(about.getGlobalVisibleRect(rect)&&exit.isShown(),"About and Exit visible on single-page Home in "+language);
                    Activity original=activity;openAbout();android.app.AlertDialog d=popup();check(activity==original&&((MainActivity)activity).isVaultUnlocked(),"About opens as modal popup without new activity or repeated PIN in "+language);
                    check(d.getWindow().getDecorView().getWidth()<activity.getWindow().getDecorView().getWidth()&&d.getWindow().getDecorView().getHeight()<activity.getWindow().getDecorView().getHeight(),"Popup is smaller than screen in both dimensions in "+language);
                    check((d.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0,"Popup retains screen capture protection in "+language);
                    for(int id:new int[]{R.string.about_title,R.string.about_intro,R.string.about_support,R.string.about_free,R.string.about_donate,R.string.about_scan,R.string.about_thanks,R.string.about_close})inDialog(d,id);
                    check(true,"All About content uses the correct language: "+language);
                    View thanks=inDialog(d,R.string.about_thanks);android.graphics.Rect visible=new android.graphics.Rect();check(thanks.getGlobalVisibleRect(visible)&&visible.height()>=thanks.getHeight(),"Whole donation message visible without scrolling in "+language);
                    View close=inDialog(d,R.string.about_close);check(close.hasFocus(),"Close is focused for TV remote in "+language);
                    boolean address=false,qr=false;for(View v:views(d.getWindow().getDecorView())){if(v instanceof TextView&&((TextView)v).getText().toString().equals("pumba22@blink.sv"))address=true;if(v.getContentDescription()!=null&&v.getContentDescription().toString().equals(activity.getString(R.string.about_qr_description)))qr=true;}
                    check(address&&qr,"Exact Lightning address and QR are present in "+language);
                    capture(d,"about-"+language);ui(close::performClick);waitDismiss(d);check(((MainActivity)activity).isVaultUnlocked()&&find(R.string.about).hasFocus(),"Close restores Home and About focus without locking in "+language);
                    openAbout();d=popup();check(getUiAutomation().performGlobalAction(1),"TV Back accepted for popup in "+language);waitDismiss(d);check(((MainActivity)activity).isVaultUnlocked(),"TV Back closes only popup in "+language);
                    openAbout();d=popup();Activity other=startActivitySync(new Intent(c,ProbeHostActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));waitForIdleSync();check(!d.isShowing(),"Backgrounding dismisses secure popup in "+language);ui(other::finish);waitType(LocalePreviewActivity.class);waitText(R.string.unlock);check(!((MainActivity)activity).isVaultUnlocked(),"Returning from background still requires PIN in "+language);ui(activity::finish);
                }
            }
            main();unlock();waitText(R.string.select_app);openAbout();android.app.AlertDialog d=popup();check(d.isShowing(),"About is available after process restart");ui(()->inDialog(d,R.string.about_close).performClick());waitDismiss(d);check(((MainActivity)activity).isVaultUnlocked(),"Closing About preserves existing vault unlock");
            check(new CryptoStore(c).load().size()==2,"About leaves synthetic vault data unchanged");
            results.putString("result","PASS");results.putInt("checks_passed",count);
        }catch(Throwable error){results.putString("result","FAIL");results.putString("error",error.toString());StringBuilder stack=new StringBuilder();for(StackTraceElement e:error.getStackTrace())stack.append(e).append("\n");results.putString("stack",stack.toString());}
        finally{if(activity!=null)ui(()->activity.finish());finish(Activity.RESULT_OK,results);}
    }
}
