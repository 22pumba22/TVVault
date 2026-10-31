package android.content;
import java.util.Set;
public interface SharedPreferences {
 String getString(String k,String d); boolean getBoolean(String k,boolean d); int getInt(String k,int d); long getLong(String k,long d); boolean contains(String k); Set<String> getStringSet(String k,Set<String>d); Editor edit();
 interface Editor { Editor putString(String k,String v); Editor putBoolean(String k,boolean v); Editor putInt(String k,int v); Editor putLong(String k,long v); Editor putStringSet(String k,Set<String>v); Editor clear(); boolean commit(); void apply(); }
}