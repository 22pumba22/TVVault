// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

public class AutofillAuthActivity extends LockedActivity {

    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_IDS = "ids";
    public static final String EXTRA_KINDS = "kinds";

    private String packageName = "";
    private ArrayList<AutofillId> ids = new ArrayList<>();
    private ArrayList<String> kinds = new ArrayList<>();
    private final ArrayList<Credential> matches = new ArrayList<>();

    @Override
    protected void onUnlocked() {
        matches.clear();

        Intent in = getIntent();
        packageName = in.getStringExtra(EXTRA_PACKAGE);
        if (packageName == null) packageName = "";

        ArrayList<AutofillId> inIds = in.getParcelableArrayListExtra(EXTRA_IDS);
        ArrayList<String> inKinds = in.getStringArrayListExtra(EXTRA_KINDS);
        if (inIds != null) ids = inIds;
        if (inKinds != null) kinds = inKinds;

        ArrayList<Credential> allCredentials = new ArrayList<>();
        try { allCredentials.addAll(new CryptoStore(this).load()); }
        catch (RuntimeException failure) {
            showSecureDialog(new android.app.AlertDialog.Builder(this).setTitle(R.string.vault_error_title)
                    .setMessage(R.string.vault_error).setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> finish()));
            return;
        }

        for (Credential c : allCredentials) {
            if (c.packageName.isEmpty() || c.packageName.equals(packageName)) {
                matches.add(c);
            }
        }

        // "Open vault" fallback: if there is no URI/package match, let the user
        // choose from the local vault instead of silently cancelling.
        if (matches.isEmpty()) {
            matches.addAll(allCredentials);
        }

        if (matches.isEmpty() || ids.isEmpty()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        if (matches.size() == 1) {
            chooseCredential(matches.get(0));
        } else {
            showPicker();
        }
    }

    private TextView text(String value, int sp) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        v.setPadding(24, 16, 24, 16);
        return v;
    }

    private Button tvButton(String value) {
        Button b=new Button(this);b.setText(value);TvTheme.styleButton(b);
        b.setMinHeight(TvTheme.dp(this,68));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);return b;
    }

    private void showPicker() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(42, 30, 42, 30);
        root.setBackgroundColor(TvTheme.BG);

        root.addView(text("TV Vault", 30));
        TextView subtitle = text(getString(R.string.choose_credential), 17);
        subtitle.setTextColor(TvTheme.SECONDARY);
        root.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        Button first = null;
        for (Credential c : matches) {
            String label = c.label.isEmpty() ? getString(R.string.credential) : c.label;
            Button row = tvButton(label + "\n" + c.username);
            row.setOnClickListener(v -> chooseCredential(c));
            list.addView(row);
            if (first == null) first = row;
        }

        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        Button cancel = tvButton(getString(R.string.cancel));
        cancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        root.addView(cancel);

        setContentView(root);
        if (first != null) first.requestFocus();
    }

    @Override protected void onVaultLocked() { matches.clear(); }

    private void chooseCredential(Credential c) {
        if (!isVaultUnlocked()) return;
        if (!c.packageName.isEmpty() && !c.packageName.equals(packageName)) {
            showSecureDialog(new android.app.AlertDialog.Builder(this).setTitle(R.string.other_app_title)
                    .setMessage(getString(R.string.other_app_warning, c.label, c.packageName, packageName))
                    .setNegativeButton(R.string.cancel, (dialog, which) -> { setResult(RESULT_CANCELED); finish(); })
                    .setPositiveButton(R.string.fill_anyway, (dialog, which) -> {
                        if (isVaultUnlocked()) returnCredential(c);
                    }));
        } else returnCredential(c);
    }

    private void returnCredential(Credential c) {
        if (!isVaultUnlocked()) return;
        RemoteViews presentation = new RemoteViews(
                getPackageName(),
                R.layout.autofill_listitem
        );
        presentation.setTextViewText(
                R.id.autofill_title,
                c.label.isEmpty() ? "TV Vault" : c.label
        );
        presentation.setTextViewText(
                R.id.autofill_subtitle,
                c.username == null ? "" : c.username
        );

        Dataset.Builder resultDataset = new Dataset.Builder(presentation);

        int filled = 0;
        for (int i = 0; i < ids.size(); i++) {
            String kind = i < kinds.size() ? kinds.get(i) : "";
            if (!"username".equals(kind) && !"password".equals(kind)) continue;
            String value = "password".equals(kind) ? c.password : c.username;
            if (value.isEmpty()) continue;
            resultDataset.setValue(ids.get(i), AutofillValue.forText(value), presentation);
            filled++;
        }
        if (filled == 0) { finish(); return; }

        Intent result = new Intent();
        result.putExtra(
                AutofillManager.EXTRA_AUTHENTICATION_RESULT,
                resultDataset.build()
        );

        setResult(RESULT_OK, result);
        finish();
    }
}
