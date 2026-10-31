// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
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
    private boolean gateVisible;
    private boolean receiverRegistered;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            if (!requiresPin()) return;
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) invalidateAccess();
            else if (foreground && !disclosurePending && !unlocked && !gateVisible) showGate();
        }
    };
    private static int foregroundActivities;
    private static boolean disclosureAccepted;
    private final java.util.ArrayList<AlertDialog> dialogs = new java.util.ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(TvTheme.BG);
        getWindow().setNavigationBarColor(TvTheme.BG);
        IntentFilter filter=new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver,filter);
        receiverRegistered=true;
    }

    protected boolean requiresPin() { return new PinStore(this).isEnabled(); }
    protected abstract void onUnlocked();
    protected void onVaultLocked() { }
    protected final boolean isVaultUnlocked() { return unlocked && foreground && !disclosurePending; }

    protected final void showSecureDialog(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create();
        dialogs.add(dialog);
        dialog.setOnDismissListener(d -> dialogs.remove(dialog));
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
        TvTheme.styleDialog(dialog);
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
        boolean enabled = requiresPin();
        if (enabled && !pinAtUnlock) { unlocked = false; onVaultLocked(); }
        if (!unlocked) {
            if (enabled) showGate();
            else grantAccess();
        }
    }

    private void grantAccess() {
        unlocked = true;
        gateVisible = false;
        pinAtUnlock = requiresPin();
        onUnlocked();
    }

    /** Lock before a resumed activity can be reused by a TV launcher or standby. */
    private void invalidateAccess() {
        generation++;
        checking=false;
        gateVisible=false;
        unlocked=false;
        onVaultLocked();
        if (!disclosurePending) {
            TextView locked=new TextView(this); locked.setText(R.string.locked);
            locked.setTextColor(TvTheme.TEXT);locked.setTextSize(24);locked.setBackgroundColor(TvTheme.BG);
            setContentView(locked);
        }
        for(AlertDialog dialog:new java.util.ArrayList<>(dialogs))dialog.dismiss();
        dialogs.clear();
    }
    @Override protected void onPause() {
        // Do not discard an idle PIN input when a TV keyboard briefly takes focus.
        // A pending verification must never unlock an activity after it has paused.
        if(requiresPin() && (unlocked || checking)) invalidateAccess();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if(foreground && !disclosurePending && !unlocked && !gateVisible && requiresPin())showGate();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(requiresPin()) {
            invalidateAccess();
            if(foreground && !disclosurePending)showGate();
        }
    }
    @Override protected void onStop() {
        super.onStop();
        foreground=false;
        foregroundActivities=Math.max(0,foregroundActivities-1);
        if(foregroundActivities==0)disclosureAccepted=false;
        if(requiresPin())invalidateAccess();
        else {
            generation++;checking=false;
            for(AlertDialog dialog:new java.util.ArrayList<>(dialogs))dialog.dismiss();
            dialogs.clear();
        }
    }
    @Override protected void onDestroy() {
        if(receiverRegistered)unregisterReceiver(screenReceiver);
        super.onDestroy();
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
        remember.setText(R.string.dont_show_again); TvTheme.styleCheckbox(remember);
        root.addView(remember);
        LinearLayout actions = new LinearLayout(this);
        Button accept = TvTheme.button(this,R.string.accept);
        Button refuse = TvTheme.button(this,R.string.refuse);
        LinearLayout.LayoutParams acceptParams=new LinearLayout.LayoutParams(0,-2,1f);acceptParams.rightMargin=TvTheme.dp(this,12);
        actions.addView(accept,acceptParams);
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
        gateVisible = true;
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
        input.setHintTextColor(TvTheme.SECONDARY);
        TvTheme.styleField(input);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        input.setSaveEnabled(false);
        root.addView(input);
        TextView error = new TextView(this);
        error.setTextColor(Color.WHITE);
        root.addView(error);
        Button unlock = TvTheme.button(this,R.string.unlock);
        root.addView(unlock);
        Button help = TvTheme.button(this,R.string.forgot_pin);
        help.setOnClickListener(v -> showSecureDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.forgot_pin).setMessage(R.string.forgot_pin_info)
                .setPositiveButton(android.R.string.ok, null)));
        root.addView(help);
        Button cancel = TvTheme.button(this,R.string.cancel);
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
