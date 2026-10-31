package android.app.assist;
import android.view.autofill.AutofillId;
public class AssistStructure { public static class ViewNode {
 public int inputType=1; public boolean focused; public int visibility=0; public String id="",hint=""; public String[] hints;
 public AutofillId getAutofillId(){return new AutofillId();} public int getAutofillType(){return 1;} public int getInputType(){return inputType;}
 public boolean isFocused(){return focused;} public boolean isSelected(){return false;} public boolean isClickable(){return true;}
 public int getVisibility(){return visibility;} public String getIdEntry(){return id;} public String getHint(){return hint;}
 public String getClassName(){return "android.widget.EditText";} public String[] getAutofillHints(){return hints;}
} }