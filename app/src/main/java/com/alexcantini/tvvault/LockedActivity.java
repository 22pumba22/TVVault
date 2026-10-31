// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Both entry points must unlock before reading any vault contents. */
public abstract class LockedActivity extends Activity {
    private boolean unlocked;
    private boolean checking;
    private int generation;
    private boolean pinAtUnlock;
    private boolean foreground;
    private boolean disclosurePending;
    private static int foregroundActivities;
    private static boolean disclosureAccepted;
    private final java.util.ArrayList<AlertDialog> dialogs = new java.util.ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    protected abstract void onUnlocked();
    protected void onVaultLocked() { }
    protected final boolean isVaultUnlocked() { return unlocked && foreground && !disclosurePending; }

    protected final void showSecureDialog(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create();
        dialogs.add(dialog);
        dialog.setOnDismissListener(d -> dialogs.remove(dialog));
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
    }

    @Override protected void onStart() {
        super.onStart();
        foreground = true;
        foregroundActivities++;
        if (!disclosureAccepted && !getSharedPreferences("tvvault_disclosure", MODE_PRIVATE).getBoolean("hide", false)) {
            showDisclosure();
            return;
        }
        continueEntry();
    }

    private void continueEntry() {
        disclosurePending = false;
        boolean enabled = new PinStore(this).isEnabled();
        if (enabled && !pinAtUnlock) { unlocked = false; onVaultLocked(); }
        if (!unlocked) {
            if (enabled) showGate();
            else grantAccess();
        }
    }

    private void grantAccess() {
        unlocked = true;
        pinAtUnlock = new PinStore(this).isEnabled();
        onUnlocked();
    }

    @Override protected void onStop() {
        super.onStop();
        foreground = false;
        foregroundActivities = Math.max(0, foregroundActivities - 1);
        if (foregroundActivities == 0) disclosureAccepted = false;
        for (AlertDialog dialog : new java.util.ArrayList<>(dialogs)) dialog.dismiss();
        dialogs.clear();
        generation++;
        checking = false;
        if (new PinStore(this).isEnabled()) {
            unlocked = false;
            onVaultLocked();
            TextView locked = new TextView(this);
            locked.setText(R.string.locked);
            setContentView(locked);
        }
    }

    private void showDisclosure() {
        disclosurePending = true;
        unlocked = false;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(22 * getResources().getDisplayMetrics().density);
        root.setPadding(pad,pad,pad,pad);
        root.setBackgroundColor(Color.rgb(8,15,24));
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        TextView title = new TextView(this); title.setText(R.string.disclaimer_title);
        title.setTextSize(25); title.setTextColor(Color.WHITE); root.addView(title);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        TextView body = new TextView(this); body.setTextSize(17); body.setTextColor(Color.WHITE);
        body.setPadding(0,pad/2,0,pad/2);
        body.setText(getString(R.string.security_with_credit, getString(R.string.security_details), getString(R.string.credit)));
        scroll.addView(body); scroll.setFocusable(true);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        android.widget.CheckBox remember = new android.widget.CheckBox(this);
        remember.setText(R.string.dont_show_again); remember.setTextSize(17); remember.setTextColor(Color.WHITE);
        root.addView(remember);
        LinearLayout actions = new LinearLayout(this);
        Button accept = new Button(this); accept.setText(R.string.accept);
        Button refuse = new Button(this); refuse.setText(R.string.refuse);
        actions.addView(accept,new LinearLayout.LayoutParams(0,-2,1f));
        actions.addView(refuse,new LinearLayout.LayoutParams(0,-2,1f)); root.addView(actions);
        refuse.setOnClickListener(v -> finish());
        accept.setOnClickListener(v -> {
            if (!getSharedPreferences("tvvault_disclosure",MODE_PRIVATE).edit().putBoolean("hide",remember.isChecked()).commit()) {
                android.widget.Toast.makeText(this,R.string.disclaimer_save_error,android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            disclosureAccepted = true;
            continueEntry();
        });
        setContentView(root); scroll.requestFocus();
    }

    /** PIN access gate. */
    private void showGate() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(28 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(8, 15, 24));
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        TextView title = new TextView(this);
        title.setText(R.string.unlock_title);
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        root.addView(title);
        EditText input = new SecretInput(this,true);
        input.setHint(R.string.pin_hint);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        input.setSaveEnabled(false);
        input.setSingleLine(true);
        root.addView(input);
        TextView error = new TextView(this);
        error.setTextColor(Color.WHITE);
        root.addView(error);
        Button unlock = new Button(this);
        unlock.setText(R.string.unlock);
        root.addView(unlock);
        Button help = new Button(this);
        help.setText(R.string.forgot_pin);
        help.setOnClickListener(v -> showSecureDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.forgot_pin).setMessage(R.string.forgot_pin_info)
                .setPositiveButton(android.R.string.ok, null)));
        root.addView(help);
        Button cancel = new Button(this);
        cancel.setText(R.string.cancel);
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel);
        unlock.setOnClickListener(v -> {
            if (checking) return;
            PinStore pin = new PinStore(this);
            long seconds = pin.remainingSeconds();
            if (seconds > 0) { error.setText(getString(R.string.pin_wait, seconds)); return; }
            String value = input.getText().toString();
            input.setText("");
            if (!PinStore.valid(value)) { error.setText(R.string.pin_format); return; }
            checking = true;
            unlock.setEnabled(false);
            error.setText(pin.isLegacy() ? R.string.upgrading_pin : R.string.checking_pin);
            int attemptGeneration = generation;
            new Thread(() -> {
                boolean accepted = false;
                boolean failed = false;
                try { accepted = pin.verify(value); } catch (Exception e) { failed = true; }
                final boolean ok = accepted, fault = failed;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != attemptGeneration) return;
                    checking = false;
                    unlock.setEnabled(true);
                    if (ok) grantAccess();
                    else {
                        long wait = pin.remainingSeconds();
                        error.setText(fault ? getString(R.string.pin_error)
                                : wait > 0 ? getString(R.string.pin_wait, wait) : getString(R.string.pin_wrong));
                        input.requestFocus();
                    }
                });
            }, "TVVault-PIN").start();
        });
        setContentView(root);
        input.requestFocus();
    }
}
