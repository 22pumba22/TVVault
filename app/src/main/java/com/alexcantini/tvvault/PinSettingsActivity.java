// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.app.AlertDialog;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class PinSettingsActivity extends LockedActivity {
    private enum Mode { OVERVIEW, CHECK_CHANGE, CHECK_DISABLE, NEW_PIN }
    private Mode mode = Mode.OVERVIEW;
    private boolean busy;
    private int generation;
    private static final int BG = Color.rgb(8,15,24);
    @Override protected void onUnlocked() { mode = Mode.OVERVIEW; generation++; busy = false; render(); }
    @Override protected void onVaultLocked() { mode = Mode.OVERVIEW; generation++; busy = false; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout root;
    private TextView message;

    private void render() {
        PinStore pin = new PinStore(this);
        boolean enabled = pin.isEnabled();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28),dp(18),dp(28),dp(18));
        root.setBackgroundColor(BG);
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.addView(label(getString(R.string.security_settings),26));
        root.addView(label(getString(enabled ? R.string.pin_on : R.string.pin_off),18));
        root.addView(label(getString(R.string.pin_description),16));
        message = label("",16);
        View first;
        if (mode == Mode.CHECK_CHANGE || mode == Mode.CHECK_DISABLE) {
            root.addView(label(getString(R.string.current_pin_prompt),18));
            EditText current = field(R.string.current_pin);
            root.addView(current);
            root.addView(message);
            Button verify = button(R.string.continue_action);
            verify.setOnClickListener(v -> verifyCurrent(current, verify));
            root.addView(verify);
            first = current;
        } else if (!enabled || mode == Mode.NEW_PIN) {
            EditText next = field(R.string.new_pin), confirm = field(R.string.confirm_pin);
            root.addView(next); root.addView(confirm); root.addView(message);
            Button save = button(enabled ? R.string.save_pin : R.string.enable_pin);
            save.setOnClickListener(v -> {
                if (busy || !isVaultUnlocked()) return;
                String value = next.getText().toString();
                if (!PinStore.valid(value)) { message.setText(R.string.pin_format); return; }
                if (!value.equals(confirm.getText().toString())) { message.setText(R.string.pin_mismatch); return; }
                next.setText(""); confirm.setText("");
                // NEW_PIN is reachable for an existing PIN only after current-PIN verification.
                mutate(() -> pin.set(value));
            });
            root.addView(save);
            first = next;
        } else {
            Button change = button(R.string.change_pin);
            change.setOnClickListener(v -> { if (!busy) { mode = Mode.CHECK_CHANGE; render(); } });
            root.addView(change);
            Button disable = button(R.string.disable_pin);
            disable.setOnClickListener(v -> { if (!busy) { mode = Mode.CHECK_DISABLE; render(); } });
            root.addView(disable);
            root.addView(message);
            first = change;
        }
        Button info = button(R.string.security_info);
        info.setOnClickListener(v -> showSecureDialog(new AlertDialog.Builder(this).setTitle(R.string.security_info)
                .setMessage(getString(R.string.security_with_credit, getString(R.string.security_details), getString(R.string.credit)))
                .setPositiveButton(android.R.string.ok, null)));
        root.addView(info);
        Button back = button(R.string.back);
        back.setOnClickListener(v -> onBackPressed());
        root.addView(back);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        first.requestFocus();
    }
    private void verifyCurrent(EditText input, Button button) {
        if (busy || !isVaultUnlocked()) return;
        String value = input.getText().toString(); input.setText("");
        if (!PinStore.valid(value)) { message.setText(R.string.pin_format); return; }
        busy = true; button.setEnabled(false);
        PinStore pin = new PinStore(this);
        message.setText(pin.isLegacy() ? R.string.upgrading_pin : R.string.checking_pin);
        int request = generation;
        Mode requestedMode = mode;
        new Thread(() -> {
            boolean accepted = false, fault = false;
            try { accepted = pin.verify(value); } catch (Exception error) { fault = true; }
            final boolean ok = accepted, failed = fault;
            runOnUiThread(() -> {
                if (!isVaultUnlocked() || isFinishing() || isDestroyed() || request != generation) return;
                busy = false; button.setEnabled(true);
                if (failed) message.setText(R.string.pin_error);
                else if (!ok) {
                    long wait = pin.remainingSeconds();
                    message.setText(wait > 0 ? getString(R.string.pin_wait, wait) : getString(R.string.pin_wrong));
                } else if (requestedMode == Mode.CHECK_DISABLE) {
                    mutate(pin::disable);
                } else { mode = Mode.NEW_PIN; render(); }
            });
        }, "TVVault-check-current-PIN").start();
    }
    private interface Operation { void run() throws Exception; }
    private void mutate(Operation operation) {
        if (busy || !isVaultUnlocked()) return;
        busy = true; message.setText(R.string.saving);
        for (int i=0;i<root.getChildCount();i++) root.getChildAt(i).setEnabled(false);
        int request = generation;
        new Thread(() -> {
            boolean success;
            try { operation.run(); success = true; } catch (Exception error) { success = false; }
            boolean ok = success;
            runOnUiThread(() -> {
                if (!isVaultUnlocked() || isFinishing() || isDestroyed() || request != generation) return;
                busy = false;
                if (ok) { mode = Mode.OVERVIEW; render(); }
                else {
                    for(int i=0;i<root.getChildCount();i++) root.getChildAt(i).setEnabled(true);
                    message.setText(R.string.pin_error);
                }
            });
        }, "TVVault-PIN-update").start();
    }
    private TextView label(String value,int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(Color.WHITE);
        view.setPadding(0,dp(4),0,dp(8)); return view;
    }
    private Button button(int text) {
        Button button = new Button(this); button.setText(text); button.setAllCaps(false); button.setTextSize(17);
        button.setTextColor(Color.WHITE); button.setMinHeight(dp(46));
        button.setPadding(dp(14),dp(4),dp(14),dp(4));
        button.setBackground(background(false));
        button.setOnFocusChangeListener((v,focus)->v.setBackground(background(focus)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,dp(48)); params.topMargin=dp(6);
        button.setLayoutParams(params); return button;
    }
    private GradientDrawable background(boolean focused) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(focused ? Color.rgb(49,68,92) : Color.rgb(18,30,44));
        shape.setCornerRadius(dp(8)); shape.setStroke(dp(focused?2:1),focused?Color.rgb(31,132,255):Color.rgb(60,83,108)); return shape;
    }
    private EditText field(int hint) {
        SecretInput input = new SecretInput(this,true); input.setHint(hint); input.setTextSize(19);
        input.setTextColor(Color.WHITE); input.setHintTextColor(Color.LTGRAY);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        input.setBackgroundTintList(ColorStateList.valueOf(Color.LTGRAY));
        return input;
    }
    @Override public void onBackPressed() {
        if (busy) return;
        if (!isVaultUnlocked() || mode == Mode.OVERVIEW) finish();
        else { generation++; mode = Mode.OVERVIEW; render(); }
    }
}
