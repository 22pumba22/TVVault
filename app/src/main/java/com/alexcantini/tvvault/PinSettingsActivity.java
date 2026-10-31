// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class PinSettingsActivity extends LockedActivity {
    private boolean saving;

    @Override protected void onUnlocked() { render(); }

    private void render() {
        PinStore pin = new PinStore(this);
        boolean enabled = pin.isEnabled();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 24, 32, 24);
        root.setBackgroundColor(Color.rgb(8, 15, 24));
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        TextView title = label(getString(R.string.security_settings), 26);
        root.addView(title);
        root.addView(label(getString(enabled ? R.string.pin_on : R.string.pin_off), 19));
        root.addView(label(getString(R.string.pin_description), 16));
        EditText first = pinField(R.string.new_pin);
        EditText confirm = pinField(R.string.confirm_pin);
        root.addView(first);
        root.addView(confirm);
        TextView message = label("", 16);
        root.addView(message);
        Button save = button(enabled ? R.string.change_pin : R.string.enable_pin);
        root.addView(save);
        Button disable = button(R.string.disable_pin);
        disable.setVisibility(enabled ? View.VISIBLE : View.GONE);
        root.addView(disable);
        Button info = button(R.string.security_info);
        info.setOnClickListener(v -> showSecureDialog(new AlertDialog.Builder(this).setTitle(R.string.security_info)
                .setMessage(R.string.security_details).setPositiveButton(android.R.string.ok, null)));
        root.addView(info);
        Button back = button(R.string.back);
        back.setOnClickListener(v -> { if (!saving) finish(); });
        root.addView(back);
        save.setOnClickListener(v -> {
            if (saving || !isVaultUnlocked()) return;
            String value = first.getText().toString();
            if (!PinStore.valid(value)) { message.setText(R.string.pin_format); return; }
            if (!value.equals(confirm.getText().toString())) { message.setText(R.string.pin_mismatch); return; }
            first.setText("");
            confirm.setText("");
            saving = true;
            save.setEnabled(false);
            disable.setEnabled(false);
            message.setText(R.string.saving);
            new Thread(() -> {
                boolean success;
                try { pin.set(value); success = true; } catch (Exception e) { success = false; }
                boolean ok = success;
                runOnUiThread(() -> {
                    saving = false;
                    if (isFinishing() || isDestroyed() || !isVaultUnlocked()) return;
                    if (ok) render();
                    else { save.setEnabled(true); disable.setEnabled(true); message.setText(R.string.pin_error); }
                });
            }, "TVVault-PIN-save").start();
        });
        disable.setOnClickListener(v -> {
            if (saving || !isVaultUnlocked()) return;
            showSecureDialog(new AlertDialog.Builder(this).setTitle(R.string.disable_pin).setMessage(R.string.disable_pin_confirm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.disable_pin, (dialog, which) -> {
                        if (!isVaultUnlocked()) return;
                        try { pin.disable(); render(); } catch (Exception e) { message.setText(R.string.pin_error); }
                    }));
        });
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        first.requestFocus();
    }

    private TextView label(String value, int size) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(Color.WHITE); v.setPadding(10, 10, 10, 10);
        return v;
    }

    private Button button(int text) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); return b; }

    private EditText pinField(int hint) {
        EditText input = new EditText(this);
        input.setHint(hint); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.LTGRAY);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        input.setSingleLine(true); input.setSaveEnabled(false);
        return input;
    }
}
