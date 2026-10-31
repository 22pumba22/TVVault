// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.autofill.AutofillManager;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends LockedActivity {

    private enum Screen {
        HOME,
        VAULT,
        EDITOR,
        APP_PICKER,
        EXCLUSIONS,
        ERROR
    }

    private static final int BG = Color.rgb(8, 15, 24);
    private static final int CARD = Color.rgb(18, 30, 44);
    private static final int CARD_FOCUS = TvTheme.FOCUS;
    private static final int BORDER = TvTheme.BORDER;
    private static final int ACCENT = TvTheme.EDGE;
    private static final int GREEN = Color.rgb(71, 201, 92);
    private static final int MUTED = TvTheme.SECONDARY;

    private final ArrayList<Credential> credentials = new ArrayList<>();
    private CryptoStore store;
    private LinearLayout listContainer;
    private Screen currentScreen = Screen.HOME;

    private String editorLabel = "";
    private String editorPackage = "";
    private String editorUsername = "";
    private String editorPassword = "";
    private int editorIndex = -1;

    @Override
    protected void onUnlocked() {
        store = new CryptoStore(this);
        credentials.clear();
        editorLabel = editorPackage = editorUsername = editorPassword = "";
        editorIndex = -1;
        try {
            credentials.addAll(store.load());
            showHome();
        } catch (RuntimeException failure) {
            showStoreError();
        }
    }

    @Override protected void onVaultLocked() {
        credentials.clear();
        editorLabel = editorPackage = editorUsername = editorPassword = "";
        editorIndex = -1;
    }

    private void showStoreError() {
        currentScreen = Screen.ERROR;
        LinearLayout root = baseRoot();
        root.addView(sectionTitle(getString(R.string.vault_error_title)));
        root.addView(sectionHint(getString(R.string.vault_error)));
        Button retry = tvButton(getString(R.string.retry));
        retry.setOnClickListener(v -> onUnlocked());
        root.addView(retry);
        Button exit = tvButton(getString(R.string.exit));
        exit.setOnClickListener(v -> finish());
        root.addView(exit);
        setContentView(root);
        retry.requestFocus();
    }

    private boolean persist(ArrayList<Credential> next) {
        try {
            store.save(next);
            credentials.clear();
            credentials.addAll(next);
            return true;
        } catch (RuntimeException failure) {
            showSecureDialog(new android.app.AlertDialog.Builder(this).setTitle(R.string.vault_error_title)
                    .setMessage(R.string.save_error).setPositiveButton(android.R.string.ok, null));
            return false;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int sp) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        return v;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp((int) radiusDp));
        return d;
    }

    private GradientDrawable cardBackground(boolean focused) {
        GradientDrawable d = rounded(focused ? CARD_FOCUS : CARD, 10);
        d.setStroke(dp(focused ? 3 : 2), focused ? ACCENT : BORDER);
        return d;
    }

    private void applyCardFocus(View view, boolean focused) {
        view.setBackground(cardBackground(focused));
        // Keep the exact rounded geometry when focused. Scaling could make
        // the outer rounded edge get clipped by the parent on Android TV.
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setClipToOutline(true);
        view.setAlpha(1f);
    }

    private Button tvButton(String value) {
        Button b=new Button(this); b.setText(value); TvTheme.styleButton(b); return b;
    }

    private void assignId(View v) {
        if (v.getId() == View.NO_ID) {
            v.setId(View.generateViewId());
        }
    }

    private void linkVertical(View upper, View lower) {
        assignId(upper);
        assignId(lower);
        upper.setNextFocusDownId(lower.getId());
        lower.setNextFocusUpId(upper.getId());
    }

    private void linkFocusChain(List<View> views) {
        if (views == null || views.isEmpty()) return;
        for (View v : views) assignId(v);
        for (int i = 0; i < views.size() - 1; i++) {
            linkVertical(views.get(i), views.get(i + 1));
        }
        View first = views.get(0);
        View last = views.get(views.size() - 1);
        first.setNextFocusUpId(first.getId());
        last.setNextFocusDownId(last.getId());
    }

    private LinearLayout baseRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(20), dp(28), dp(20));
        root.setBackgroundColor(BG);

        // GOLDEN v0.13 behavior: TV Vault must never Autofill its own fields.
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        return root;
    }

    private TextView sectionTitle(String title) {
        TextView v = text(title, 28);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(0, 0, 0, dp(6));
        return v;
    }

    private TextView sectionHint(String hint) {
        TextView v = text(hint, 15);
        v.setTextColor(MUTED);
        v.setPadding(0, 0, 0, dp(14));
        return v;
    }

    private TextView iconChip(String symbol, int color) {
        TextView icon = text(symbol, 17);
        icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setFocusable(false);
        icon.setBackground(rounded(color, 9));
        return icon;
    }

    private LinearLayout homeCard(
            String iconSymbol,
            int iconColor,
            String title,
            String subtitle,
            Runnable action
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        row.setBackground(cardBackground(false));
        row.setClipToOutline(true);

        TextView icon = iconChip(iconSymbol, iconColor);
        row.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(16), 0, dp(10), 0);

        TextView titleView = text(title, 20);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView subtitleView = text(subtitle, 14);
        subtitleView.setMaxLines(2);
        subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitleView.setTextColor(MUTED);
        subtitleView.setPadding(0, dp(2), 0, 0);
        copy.addView(titleView);
        copy.addView(subtitleView);

        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = text("›", 32);
        chevron.setTextColor(MUTED);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(42), dp(54)));

        row.setOnFocusChangeListener((v, hasFocus) -> applyCardFocus(v, hasFocus));
        row.setOnClickListener(v -> { if (isVaultUnlocked()) action.run(); else requestUnlock(); });
        return row;
    }

    @Override protected boolean exitFromGate() { return true; }
    @Override protected boolean showNavigationHome() { showHome(); return true; }

    private void showHome() {
        currentScreen = Screen.HOME;
        LinearLayout root = baseRoot();
        root.setPadding(dp(24),dp(16),dp(24),dp(16));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.tv_vault_icon);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout heading = new LinearLayout(this); heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(14),0,0,0);
        TextView title = text("TV VAULT",26); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        heading.addView(title); heading.addView(text(getString(R.string.tagline),14));
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1f));
        Button about = tvButton(getString(R.string.about)); about.setMinHeight(dp(44));
        about.setOnClickListener(v -> AboutDialog.show(this));
        LinearLayout.LayoutParams aboutParams = new LinearLayout.LayoutParams(dp(118),dp(46));
        aboutParams.rightMargin = dp(10);
        header.addView(about,aboutParams);
        Button exit = tvButton(getString(R.string.exit)); exit.setMinHeight(dp(44));
        exit.setOnClickListener(v -> finish());
        header.addView(exit,new LinearLayout.LayoutParams(dp(118),dp(46)));
        root.addView(header,marginBottom(dp(12)));

        LinearLayout select = homeCard("KEY",Color.rgb(20,106,224),getString(R.string.select_app),
                getString(R.string.select_app_hint),()-> {
                    editorIndex=-1; editorLabel=editorPackage=editorUsername=editorPassword=""; showAppPicker();
                });
        LinearLayout vault = homeCard("LOCK",Color.rgb(43,142,67),getString(R.string.vault),getString(R.string.vault_hint),this::showVault);
        LinearLayout excluded = homeCard("OFF",Color.rgb(109,55,177),getString(R.string.excluded_apps),getString(R.string.excluded_apps_hint),this::showExclusions);
        LinearLayout security = homeCard("PIN",Color.rgb(75,94,116),getString(R.string.security_settings),getString(new PinStore(this).isEnabled()?R.string.home_pin_active:R.string.home_pin_unset),
                ()->startActivity(new Intent(this,PinSettingsActivity.class)));
        // This activity has its own entry gate, including when the Home is locked.
        security.setOnClickListener(v -> startActivity(new Intent(this,PinSettingsActivity.class)));
        LinearLayout top = new LinearLayout(this), bottom = new LinearLayout(this);
        for(LinearLayout line : new LinearLayout[]{top,bottom}) line.setBaselineAligned(false);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0,-1,1f); left.rightMargin=dp(10);
        top.addView(select,left); top.addView(vault,new LinearLayout.LayoutParams(0,-1,1f));
        LinearLayout.LayoutParams leftBottom = new LinearLayout.LayoutParams(0,-1,1f); leftBottom.rightMargin=dp(10);
        bottom.addView(excluded,leftBottom); bottom.addView(security,new LinearLayout.LayoutParams(0,-1,1f));
        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(-1,0,1f); topParams.bottomMargin=dp(10);
        root.addView(top,topParams);
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(-1,0,1f); bottomParams.bottomMargin=dp(10);
        root.addView(bottom,bottomParams);
        LinearLayout autofill = autofillServiceCard(); root.addView(autofill,new LinearLayout.LayoutParams(-1,dp(76)));
        for(View view : new View[]{about,exit,select,vault,excluded,security,autofill}) assignId(view);
        select.setNextFocusRightId(vault.getId()); vault.setNextFocusLeftId(select.getId());
        excluded.setNextFocusRightId(security.getId()); security.setNextFocusLeftId(excluded.getId());
        linkVertical(select,excluded); linkVertical(vault,security);
        about.setNextFocusRightId(exit.getId()); exit.setNextFocusLeftId(about.getId());
        about.setNextFocusDownId(vault.getId());
        select.setNextFocusUpId(about.getId()); vault.setNextFocusUpId(exit.getId()); exit.setNextFocusDownId(vault.getId());
        excluded.setNextFocusDownId(autofill.getId()); security.setNextFocusDownId(autofill.getId()); autofill.setNextFocusUpId(security.getId());
        setContentView(root); select.requestFocus();
    }

    private boolean isTvVaultAutofillEnabled() {
        AutofillManager manager =
                getSystemService(AutofillManager.class);
        return manager != null && manager.hasEnabledAutofillServices();
    }

    private LinearLayout autofillServiceCard() {
        final boolean active = isTvVaultAutofillEnabled();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        row.setBackground(cardBackground(false));
        row.setClipToOutline(true);
        row.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);

        TextView icon = iconChip("OK", Color.rgb(24, 156, 166));
        row.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(16), 0, dp(10), 0);

        TextView titleView = text(getString(R.string.setup_autofill), 20);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView subtitleView = text(
                getString(R.string.setup_autofill_hint),
                14
        );
        subtitleView.setMaxLines(2);
        subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitleView.setTextColor(MUTED);
        subtitleView.setPadding(0, dp(2), 0, 0);
        copy.addView(titleView);
        copy.addView(subtitleView);

        row.addView(copy, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView status = text(active ? getString(R.string.active) : getString(R.string.not_active), 14);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setTextColor(
                active ? Color.rgb(71, 201, 92) : Color.rgb(255, 177, 66)
        );
        status.setGravity(Gravity.CENTER);
        row.addView(status, new LinearLayout.LayoutParams(dp(120), dp(54)));

        TextView chevron = text("›", 32);
        chevron.setTextColor(MUTED);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(42), dp(54)));

        row.setOnFocusChangeListener((v, hasFocus) -> applyCardFocus(v, hasFocus));
        row.setOnClickListener(v -> {
            if (!isVaultUnlocked()) { requestUnlock(); return; }
            AutofillManager manager = getSystemService(AutofillManager.class);
            boolean enabled =
                    manager != null && manager.hasEnabledAutofillServices();

            if (enabled) {
                // Android allows the Autofill provider app to disable its own
                // Autofill services directly.
                manager.disableAutofillServices();
                Toast.makeText(
                        this,
                        getString(R.string.autofill_disabled),
                        Toast.LENGTH_SHORT
                ).show();
                showHome();
            } else {
                // Enabling always requires Android system confirmation.
                Intent intent =
                        new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try {
                    openAutofillSettings(intent);
                } catch (Exception e) {
                    Toast.makeText(
                            this,
                            getString(R.string.autofill_settings_help),
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        });

        return row;
    }

    private LinearLayout.LayoutParams marginBottom(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, px);
        return lp;
    }

    private void showVault() {
        currentScreen = Screen.VAULT;
        LinearLayout root = baseRoot();
        root.addView(sectionTitle(getString(R.string.vault)));
        root.addView(sectionHint(getString(R.string.vault_local_hint)));

        ArrayList<View> focusables = new ArrayList<>();

        Button add = tvButton(getString(R.string.add_credential));
        add.setOnClickListener(v -> {
            editorIndex = -1;
            editorLabel = "";
            editorPackage = "";
            editorUsername = "";
            editorPassword = "";
            showEditor();
        });
        root.addView(add, marginBottom(dp(8)));
        focusables.add(add);

        ScrollView scroll = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(4), 0, dp(8));
        scroll.addView(listContainer);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        Button back = tvButton(getString(R.string.home));
        back.setOnClickListener(v -> showHome());
        root.addView(back);
        focusables.add(back);

        setContentView(root);
        renderVaultRows(focusables, back);
        linkFocusChain(focusables);
        add.requestFocus();
    }

    private void renderVaultRows(ArrayList<View> focusables, View back) {
        listContainer.removeAllViews();

        if (credentials.isEmpty()) {
            TextView empty = text(
                    getString(R.string.empty_vault),
                    16
            );
            empty.setTextColor(MUTED);
            empty.setPadding(dp(8), dp(14), dp(8), dp(14));
            listContainer.addView(empty);
            return;
        }

        // Insert credential rows before the bottom Back button in focus order.
        int insertion = Math.max(0, focusables.size() - 1);

        for (int i = 0; i < credentials.size(); i++) {
            final int index = i;
            Credential c = credentials.get(i);
            String label = c.label.isEmpty() ? getString(R.string.credential) : c.label;
            String pkg = c.packageName.isEmpty() ? getString(R.string.all_apps) : c.packageName;

            LinearLayout row = simpleInfoRow("LOCK", label, c.username + "  ·  " + pkg);
            row.setOnClickListener(v -> {
                editorIndex = index;
                editorLabel = c.label;
                editorPackage = c.packageName;
                editorUsername = c.username;
                editorPassword = c.password;
                showEditor();
            });
            listContainer.addView(row, marginBottom(dp(6)));
            focusables.add(insertion++, row);
        }
    }

    private LinearLayout simpleInfoRow(String symbol, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setMinimumHeight(dp(66));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        row.setBackground(cardBackground(false));
        row.setClipToOutline(true);

        TextView icon = iconChip(symbol, Color.rgb(43, 142, 67));
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);
        TextView t = text(title, 17);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView s = text(subtitle, 13);
        s.setTextColor(MUTED);
        copy.addView(t);
        copy.addView(s);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnFocusChangeListener((v, hasFocus) -> applyCardFocus(v, hasFocus));
        return row;
    }

    private EditText field(String hint, String value, int inputType) {
        boolean secret = (inputType & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD;
        EditText e = secret ? new SecretInput(this,false) : new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setTextSize(18);
        if (!secret) e.setSingleLine(true);
        e.setSaveEnabled(false);
        if (!secret) e.setInputType(inputType);
        if (secret) ((SecretInput)e).hideAll();
        e.setFocusable(true);
        e.setFocusableInTouchMode(true);
        e.setPadding(dp(14), dp(8), dp(14), dp(8));
        e.setBackgroundTintList(ColorStateList.valueOf(MUTED));
        e.setOnFocusChangeListener((v, hasFocus) -> {
            e.setBackgroundTintList(ColorStateList.valueOf(hasFocus ? ACCENT : MUTED));
            v.setAlpha(hasFocus ? 1f : 0.90f);
        });
        // GOLDEN v0.13 behavior: no Autofill inside TV Vault itself.
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        TvTheme.styleField(e);
        return e;
    }

    private void showEditor() {
        currentScreen = Screen.EDITOR;
        LinearLayout root = baseRoot();
        root.addView(sectionTitle(editorIndex < 0 ? getString(R.string.new_credential) : getString(R.string.edit_credential)));
        root.addView(sectionHint(getString(R.string.editor_hint)));

        EditText label = field(getString(R.string.label_hint), editorLabel, InputType.TYPE_CLASS_TEXT);
        root.addView(label, marginBottom(dp(7)));

        Button chooseApp = tvButton(
                editorPackage.isEmpty()
                        ? getString(R.string.choose_app)
                        : getString(R.string.app_prefix) + getReadableAppName(editorPackage) + "\n" + editorPackage
        );
        root.addView(chooseApp, marginBottom(dp(7)));

        EditText username = field(
                getString(R.string.username_hint),
                editorUsername,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );
        root.addView(username, marginBottom(dp(7)));

        EditText password = field(
                getString(R.string.password_hint),
                editorPassword,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        root.addView(password, marginBottom(dp(10)));

        Button save = tvButton(getString(R.string.save));
        Button delete = tvButton(getString(R.string.delete));
        Button cancel = tvButton(getString(R.string.home));

        root.addView(save, marginBottom(dp(6)));
        root.addView(delete, marginBottom(dp(6)));
        root.addView(cancel);

        assignId(label);
        assignId(chooseApp);
        assignId(username);
        assignId(password);
        assignId(save);
        assignId(delete);
        assignId(cancel);

        linkVertical(label, chooseApp);
        linkVertical(chooseApp, username);
        linkVertical(username, password);
        linkVertical(password, save);
        linkVertical(save, delete);
        linkVertical(delete, cancel);

        label.setNextFocusUpId(cancel.getId());
        cancel.setNextFocusDownId(label.getId());

        chooseApp.setOnClickListener(v -> {
            editorLabel = label.getText().toString();
            editorUsername = username.getText().toString();
            editorPassword = password.getText().toString();
            showAppPicker();
        });

        save.setOnClickListener(v -> {
            Credential next = new Credential(
                    label.getText().toString().trim(),
                    editorPackage,
                    username.getText().toString(),
                    password.getText().toString()
            );

            if (next.username.isEmpty() && next.password.isEmpty()) {
                Toast.makeText(this, getString(R.string.credential_required), Toast.LENGTH_SHORT).show();
                return;
            }

            ArrayList<Credential> updated = new ArrayList<>(credentials);
            if (editorIndex >= 0) updated.set(editorIndex, next);
            else updated.add(next);
            if (persist(updated)) showVault();
        });

        delete.setEnabled(editorIndex >= 0);
        delete.setOnClickListener(v -> {
            if (editorIndex >= 0) {
                ArrayList<Credential> updated = new ArrayList<>(credentials);
                updated.remove(editorIndex);
                if (persist(updated)) showVault();
            }
        });

        cancel.setOnClickListener(v -> showHome());

        setContentView(root);
        label.requestFocus();
    }

    private LinearLayout createAppRow(AppEntry app, String statusText, boolean positiveStatus) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setMinimumHeight(dp(68));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        row.setBackground(cardBackground(false));
        row.setClipToOutline(true);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setFocusable(false);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        iconLp.setMargins(0, 0, dp(14), 0);
        row.addView(icon, iconLp);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(app.label, 17);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView pkg = text(app.packageName, 13);
        pkg.setTextColor(MUTED);
        copy.addView(label);
        copy.addView(pkg);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView status = text(statusText, 14);
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        status.setTextColor(positiveStatus ? GREEN : Color.rgb(210, 216, 224));
        status.setPadding(dp(12), 0, 0, 0);
        status.setTag("status");
        row.addView(status, new LinearLayout.LayoutParams(dp(160), ViewGroup.LayoutParams.MATCH_PARENT));

        row.setOnFocusChangeListener((v, hasFocus) -> applyCardFocus(v, hasFocus));
        return row;
    }

    private LinearLayout createAllAppsRow(boolean selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setMinimumHeight(dp(68));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        row.setBackground(cardBackground(false));
        row.setClipToOutline(true);

        TextView icon = iconChip("ALL", Color.rgb(75, 94, 116));
        row.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);
        TextView label = text(getString(R.string.all_apps), 17);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView pkg = text(getString(R.string.no_package), 13);
        pkg.setTextColor(MUTED);
        copy.addView(label);
        copy.addView(pkg);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView status = text(selected ? getString(R.string.selected) : getString(R.string.select), 14);
        status.setTextColor(selected ? GREEN : Color.rgb(210, 216, 224));
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.addView(status, new LinearLayout.LayoutParams(dp(160), ViewGroup.LayoutParams.MATCH_PARENT));

        row.setOnFocusChangeListener((v, hasFocus) -> applyCardFocus(v, hasFocus));
        return row;
    }

    private void showAppPicker() {
        currentScreen = Screen.APP_PICKER;
        LinearLayout root = baseRoot();
        root.addView(sectionTitle(getString(R.string.choose_app)));
        root.addView(sectionHint(getString(R.string.app_picker_hint)));

        ArrayList<View> focusables = new ArrayList<>();

        LinearLayout allApps = createAllAppsRow(editorPackage.isEmpty());
        allApps.setOnClickListener(v -> {
            editorPackage = "";
            showEditor();
        });
        root.addView(allApps, marginBottom(dp(7)));
        focusables.add(allApps);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout appsContainer = new LinearLayout(this);
        appsContainer.setOrientation(LinearLayout.VERTICAL);

        List<AppEntry> apps = loadLaunchableApps();
        for (AppEntry app : apps) {
            boolean selected = app.packageName.equals(editorPackage);
            LinearLayout row = createAppRow(
                    app,
                    selected ? getString(R.string.selected) : getString(R.string.select),
                    selected
            );
            row.setOnClickListener(v -> {
                editorPackage = app.packageName;
                if (editorLabel.trim().isEmpty()) editorLabel = app.label;
                showEditor();
            });
            appsContainer.addView(row, marginBottom(dp(5)));
            focusables.add(row);
        }

        scroll.addView(appsContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        Button cancel = tvButton(getString(R.string.home));
        cancel.setOnClickListener(v -> showHome());
        root.addView(cancel);
        focusables.add(cancel);

        linkFocusChain(focusables);
        setContentView(root);
        allApps.requestFocus();
    }

    private void showExclusions() {
        currentScreen = Screen.EXCLUSIONS;
        LinearLayout root = baseRoot();
        root.addView(sectionTitle(getString(R.string.excluded_apps)));
        root.addView(sectionHint(
                getString(R.string.exclusions_hint)
        ));

        ExclusionStore exclusionStore = new ExclusionStore(this);
        ArrayList<View> focusables = new ArrayList<>();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout appsContainer = new LinearLayout(this);
        appsContainer.setOrientation(LinearLayout.VERTICAL);
        appsContainer.setImportantForAutofill(
                View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        );

        List<AppEntry> apps = loadLaunchableApps();
        View first = null;

        for (AppEntry app : apps) {
            boolean excluded = exclusionStore.isExcluded(app.packageName);
            LinearLayout row = createAppRow(
                    app,
                    excluded ? getString(R.string.excluded) : getString(R.string.included),
                    excluded
            );

            TextView status = (TextView) row.findViewWithTag("status");
            row.setOnClickListener(v -> {
                boolean nowExcluded = !exclusionStore.isExcluded(app.packageName);
                exclusionStore.setExcluded(app.packageName, nowExcluded);
                status.setText(nowExcluded ? getString(R.string.excluded) : getString(R.string.included));
                status.setTextColor(nowExcluded ? GREEN : Color.rgb(210, 216, 224));
                Toast.makeText(
                        this,
                        nowExcluded ? app.label + getString(R.string.excluded_suffix) : app.label + getString(R.string.included_suffix),
                        Toast.LENGTH_SHORT
                ).show();
            });

            appsContainer.addView(row, marginBottom(dp(5)));
            focusables.add(row);
            if (first == null) first = row;
        }

        scroll.addView(appsContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        Button back = tvButton(getString(R.string.home));
        back.setOnClickListener(v -> showHome());
        root.addView(back);
        focusables.add(back);

        linkFocusChain(focusables);
        setContentView(root);
        if (first != null) first.requestFocus();
        else back.requestFocus();
    }

    private List<AppEntry> loadLaunchableApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);

        List<ResolveInfo> leanback = pm.queryIntentActivities(intent, 0);

        Intent normal = new Intent(Intent.ACTION_MAIN);
        normal.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcher = pm.queryIntentActivities(normal, 0);

        ArrayList<ResolveInfo> merged = new ArrayList<>();
        merged.addAll(leanback);
        merged.addAll(launcher);

        Set<String> seen = new HashSet<>();
        ArrayList<AppEntry> result = new ArrayList<>();

        for (ResolveInfo ri : merged) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg == null || pkg.equals(getPackageName()) || seen.contains(pkg)) continue;

            seen.add(pkg);

            CharSequence labelCs = ri.loadLabel(pm);
            String label = labelCs == null ? pkg : labelCs.toString();

            Drawable icon;
            try {
                icon = ri.loadIcon(pm);
            } catch (Exception e) {
                icon = getDrawable(android.R.drawable.sym_def_app_icon);
            }

            result.add(new AppEntry(label, pkg, icon));
        }

        Collections.sort(result, Comparator.comparing(
                a -> a.label.toLowerCase(Locale.ROOT)
        ));

        return result;
    }

    private String getReadableAppName(String packageName) {
        if (packageName == null || packageName.isEmpty()) return "";
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isVaultUnlocked() && currentScreen == Screen.HOME) {
            showHome();
        }
    }

    @Override
    public void onBackPressed() {
        if (!isVaultUnlocked() || currentScreen == Screen.HOME || currentScreen == Screen.ERROR) {
            finish();
        } else {
            showHome();
        }
    }
}
