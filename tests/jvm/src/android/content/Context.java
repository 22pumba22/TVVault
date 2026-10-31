package android.content;
import java.util.*;
public class Context {
 public static final int MODE_PRIVATE=0;
 public final Map<String,Prefs> stores=new HashMap<>();
 public Context getApplicationContext(){return this;}
 public SharedPreferences getSharedPreferences(String name,int mode){return stores.computeIfAbsent(name,k->new Prefs());}
 public static class Prefs implements SharedPreferences {
  public Map<String,Object> data=new HashMap<>(); public boolean failWrites;
  public String getString(String k,String d){return (String)data.getOrDefault(k,d);}
  public boolean getBoolean(String k,boolean d){return (Boolean)data.getOrDefault(k,d);}
  public int getInt(String k,int d){return (Integer)data.getOrDefault(k,d);}
  public long getLong(String k,long d){return (Long)data.getOrDefault(k,d);}
  public boolean contains(String k){return data.containsKey(k);}
  @SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String>d){return (Set<String>)data.getOrDefault(k,d);}
  public Editor edit(){return new Edit();}
  class Edit implements Editor {
   Map<String,Object> next=new HashMap<>(data);
   public Editor putString(String k,String v){next.put(k,v);return this;}
   public Editor putBoolean(String k,boolean v){next.put(k,v);return this;}
   public Editor putInt(String k,int v){next.put(k,v);return this;}
   public Editor putLong(String k,long v){next.put(k,v);return this;}
   public Editor putStringSet(String k,Set<String>v){next.put(k,v);return this;}
   public Editor clear(){next.clear();return this;}
   public boolean commit(){if(failWrites)return false;data=next;return true;}
   public void apply(){commit();}
  }
 }
}