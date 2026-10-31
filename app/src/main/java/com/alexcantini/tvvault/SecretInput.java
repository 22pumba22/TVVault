// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.TransformationMethod;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/** Reveals one newly typed character for 650 ms; stored values and pasted text stay masked. */
public final class SecretInput extends EditText {
    private int visible = -1;
    private long until;
    private final Runnable mask = () -> { visible = -1; invalidate(); };

    public SecretInput(Context context) { this(context,false); }

    public SecretInput(Context context, boolean numeric) {
        super(context);
        setInputType(numeric ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        setTypeface(Typeface.DEFAULT);
        setSingleLine(true);
        setSaveEnabled(false);
        setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        setImeOptions(getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        setTransformationMethod(new TransformationMethod() {
            @Override public CharSequence getTransformation(CharSequence source, View view) {
                return new CharSequence() {
                    @Override public int length() { return source.length(); }
                    @Override public char charAt(int index) {
                        return index == visible && hasFocus() && SystemClock.uptimeMillis() < until
                                ? source.charAt(index) : '*';
                    }
                    @Override public CharSequence subSequence(int start, int end) {
                        StringBuilder text = new StringBuilder();
                        for (int i = start; i < end; i++) text.append(charAt(i));
                        return text.toString();
                    }
                    @Override public String toString() { return subSequence(0, length()).toString(); }
                };
            }
            @Override public void onFocusChanged(View view, CharSequence text, boolean focused, int direction, Rect rect) {
                if (!focused) hideAll();
            }
        });
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                removeCallbacks(mask);
                visible = count == 1 && hasFocus() ? start : -1;
                until = SystemClock.uptimeMillis() + 650;
                postDelayed(mask, 650);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
    }

    public void hideAll() { visible = -1; removeCallbacks(mask); invalidate(); }
    @Override protected void onDetachedFromWindow() { hideAll(); super.onDetachedFromWindow(); }
}
