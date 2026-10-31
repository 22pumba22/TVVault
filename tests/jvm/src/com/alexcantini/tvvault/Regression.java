package com.alexcantini.tvvault;
import android.content.Context;
import android.app.assist.AssistStructure.ViewNode;
import java.util.*;
import java.security.*;
import java.security.cert.Certificate;
import java.io.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import org.json.*;
public class Regression {
 static int checks;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 interface Action {void run() throws Exception;}
 static void rejects(Action action,String name)throws Exception {boolean threw=false;try{action.run();}catch(Exception e){threw=true;}check(threw,name);}
 public static class FakeProvider extends Provider {public FakeProvider(){super("TestKeystore", "1.0", "Test only; not Android hardware");put("KeyStore.AndroidKeyStore",FakeKeyStore.class.getName());}}
 public static class FakeKeyStore extends KeyStoreSpi {
  static boolean available=true;
  static final SecretKey key=new SecretKeySpec(new byte[32],"AES");
  public Key engineGetKey(String alias,char[] password){return available?key:null;}
  public Certificate[] engineGetCertificateChain(String alias){return null;}
  public Certificate engineGetCertificate(String alias){return null;}
  public Date engineGetCreationDate(String alias){return new Date(0);}
  public void engineSetKeyEntry(String a,Key k,char[] p,Certificate[] c){} public void engineSetKeyEntry(String a,byte[] k,Certificate[] c){}
  public void engineSetCertificateEntry(String a,Certificate c){} public void engineDeleteEntry(String a){}
  public Enumeration<String> engineAliases(){return Collections.enumeration(List.of("TVVaultLocalKeyV1"));}
  public boolean engineContainsAlias(String a){return available;} public int engineSize(){return available?1:0;}
  public boolean engineIsKeyEntry(String a){return available;} public boolean engineIsCertificateEntry(String a){return false;}
  public String engineGetCertificateAlias(Certificate c){return null;}
  public void engineStore(OutputStream s,char[] p){} public void engineLoad(InputStream s,char[] p){}
  public KeyStore.Entry engineGetEntry(String alias,KeyStore.ProtectionParameter p){return new KeyStore.SecretKeyEntry(key);}
 }
 static BwField field(String id,int type,String...hints){ViewNode n=new ViewNode();n.id=id;n.inputType=type;n.hints=hints;return new BwField(n);}
 public static void main(String[] args)throws Exception {
  Context context=new Context();PinStore pin=new PinStore(context, create -> new SecretKeySpec(new byte[32], "HmacSHA256"));
  check(!pin.isEnabled(),"PIN disabled on first use");
  rejects(()->pin.set("12"),"PIN rejects too-short values");rejects(()->pin.set("abcd"),"PIN accepts ASCII digits only");
  pin.set("012345");check(pin.isEnabled(),"PIN enabled");
  Context.Prefs p=context.stores.get("tvvault_pin");String salt=p.getString("salt","");
  check(!p.data.toString().contains("012345"),"PIN is not stored in cleartext");
  check(pin.verify("012345"),"Leading-zero PIN verifies");
  check(!pin.verify("012346"),"Wrong PIN rejected");
  pin.set("012345");check(!salt.equals(p.getString("salt","")),"Same PIN generates a new random salt");
  for(int i=0;i<5;i++)check(!pin.verify("111111"),"Wrong attempt "+(i+1));
  check(pin.remainingSeconds()>0,"Cooldown starts after five failures");
  PinStore reopened=new PinStore(context, create -> new SecretKeySpec(new byte[32], "HmacSHA256"));check(!reopened.verify("012345"),"Restart cannot bypass cooldown");
  p.edit().putLong("until",0).commit();check(reopened.verify("012345"),"Correct PIN accepted after cooldown");
  check(p.getInt("failures",-1)==0,"Successful unlock resets failure counter");
  reopened.disable();check(!reopened.isEnabled(),"PIN can be disabled");
  pin.set("1234");p.edit().putString("hash","bad").commit();check(pin.isEnabled(),"Corrupt PIN config stays enabled");rejects(()->pin.verify("1234"),"Corrupt PIN fails closed");
  pin.disable();p.failWrites=true;rejects(()->pin.set("1234"),"PIN write failure is surfaced");p.failWrites=false;
  Context migrationContext=new Context();LegacyPinStore legacyPin=new LegacyPinStore(migrationContext);legacyPin.set("9876");
  PinStore migrated=new PinStore(migrationContext,create->new SecretKeySpec(new byte[32],"HmacSHA256"));
  check(migrated.isLegacy(),"Legacy PIN format recognized");check(!migrated.verify("0000"),"Wrong legacy PIN rejected");
  check(migrated.isLegacy(),"Wrong guess cannot migrate legacy verifier");
  check(migrated.verify("9876"),"Existing PIN unlocks and migrates");check(!migrated.isLegacy(),"Successful legacy unlock upgrades format");
  check(migrated.verify("9876"),"Migrated PIN verifies using keyed HMAC");
  PinStore missingPinKey=new PinStore(migrationContext,create->{throw new IllegalStateException("missing key");});
  rejects(()->missingPinKey.verify("9876"),"Missing PIN keystore key fails closed");check(missingPinKey.isEnabled(),"Missing key never disables PIN");
  Context optionContext=new Context();PinStore options=new PinStore(optionContext,create->new SecretKeySpec(new byte[32],"HmacSHA256"));
  check(options.isAutofillPinRequired(),"Autofill PIN required by default");
  rejects(()->options.setAutofillPinRequired(false,"1234"),"Cannot configure Autofill protection without a PIN");
  options.set("1234");check(!options.setAutofillPinRequired(false,"0000"),"Wrong PIN cannot lower Autofill protection");
  check(options.isAutofillPinRequired(),"Wrong authorization preserves default protection");
  check(options.setAutofillPinRequired(false,"1234"),"Correct PIN can disable only Autofill gate");
  check(options.isEnabled()&&!options.isAutofillPinRequired(),"Vault PIN remains enabled when Autofill PIN is disabled");
  PinStore optionReloaded=new PinStore(optionContext,create->new SecretKeySpec(new byte[32],"HmacSHA256"));
  check(!optionReloaded.isAutofillPinRequired(),"Autofill choice persists on reload");
  options.set("5678");check(!options.isAutofillPinRequired(),"Changing vault PIN preserves Autofill choice");
  check(options.setAutofillPinRequired(true,"5678"),"Current PIN can restore Autofill protection");
  check(options.isAutofillPinRequired(),"Restored Autofill protection persists");
  options.disable();options.set("9012");check(options.isAutofillPinRequired(),"New PIN enrollment returns to protected Autofill default");
  Security.insertProviderAt(new FakeProvider(),1);
  Context vaultContext=new Context();LegacyCryptoStore legacy=new LegacyCryptoStore(vaultContext);
  List<Credential> old=List.of(new Credential("TV test","example.tv","local-user","secret-password"));legacy.save(old);
  String blob=vaultContext.stores.get("tvvault_secure").getString("vault","");
  check(!blob.contains("secret-password")&&!blob.contains("local-user"),"Original encrypted payload hides credentials");
  CryptoStore store=new CryptoStore(vaultContext);List<Credential> loaded=store.load();
  check(loaded.size()==1&&loaded.get(0).password.equals("secret-password"),"New store reads exact original-version encrypted format");
  store.save(loaded);check(legacy.load().get(0).username.equals("local-user"),"Original store reads new encrypted format");
  String good=vaultContext.stores.get("tvvault_secure").getString("vault","");
  JSONObject wrapper=new JSONObject(good);byte[] ct=java.util.Base64.getDecoder().decode(wrapper.getString("ct"));ct[0]^=1;wrapper.put("ct",java.util.Base64.getEncoder().encodeToString(ct));
  String corrupt=wrapper.toString();vaultContext.stores.get("tvvault_secure").edit().putString("vault",corrupt).commit();
  rejects(()->store.load(),"AES-GCM tampering is detected");rejects(()->store.save(List.of()),"Unreadable vault cannot be overwritten");
  check(vaultContext.stores.get("tvvault_secure").getString("vault","").equals(corrupt),"Corrupted original blob is preserved");
  vaultContext.stores.get("tvvault_secure").edit().putString("vault",good).commit();store.load();
  vaultContext.stores.get("tvvault_secure").edit().putString("vault","broken").commit();rejects(()->store.save(List.of()),"Save rechecks persisted data after earlier successful read");
  vaultContext.stores.get("tvvault_secure").edit().putString("vault",good).commit();FakeKeyStore.available=false;
  rejects(()->new CryptoStore(vaultContext).load(),"Missing vault key does not silently create a replacement");FakeKeyStore.available=true;
  check(!field("url",1|16).isPasswordByInputType(),"URL input is not mistaken for numeric password");
  check(field("pin",2|16).isPasswordByInputType(),"Numeric password input recognized");
  BwFieldCollection fields=new BwFieldCollection();fields.add(field("search",1));check(!fields.fillableForLogin(),"Search-only form rejected");
  BwFieldCollection login=new BwFieldCollection();BwField username=field("email",1,"username"),password=field("password",1|128,"password");login.add(username);login.add(password);login.add(field("otp",2));
  check(login.usernameFields().contains(username)&&login.passwordFields().contains(password),"Login username and password recognized");check(login.fields.size()==2,"OTP excluded from fill targets");
  BwFieldCollection uri=new BwFieldCollection();uri.add(field("url",1|16));check(!uri.fillableForLogin(),"URI-only form rejected");
  System.out.println("RESULT: "+checks+" regression checks passed. Android UI/Keystore hardware/device behavior require on-device tests.");
 }
}