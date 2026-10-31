// SPDX-License-Identifier: GPL-3.0-only
// Test-only, independent .preview application. Never part of the shipped APK.
package com.alexcantini.tvvault;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.ArrayList;

public final class ProbeHostActivity extends Activity {
    public volatile int resultCode = -999;
    public volatile boolean returnedDataset;
    private EditText username, password;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        // Do not invoke any real enabled Autofill provider in this synthetic host.
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        username=new EditText(this);password=new EditText(this);root.addView(username);root.addView(password);
        setContentView(root);
    }
    public void requestFill() {
        resultCode=-999;returnedDataset=false;
        Intent intent=new Intent(this,AutofillAuthActivity.class);
        intent.putExtra(AutofillAuthActivity.EXTRA_PACKAGE,"synthetic.login.app");
        ArrayList<AutofillId> ids=new ArrayList<>();ids.add(username.getAutofillId());ids.add(password.getAutofillId());
        ArrayList<String> kinds=new ArrayList<>();kinds.add("username");kinds.add("password");
        intent.putParcelableArrayListExtra(AutofillAuthActivity.EXTRA_IDS,ids);
        intent.putStringArrayListExtra(AutofillAuthActivity.EXTRA_KINDS,kinds);
        startActivityForResult(intent,10);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        resultCode=result;
        returnedDataset=data!=null&&data.hasExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT);
    }
}
