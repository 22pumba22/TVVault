// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.app.assist.AssistStructure;
import android.text.InputType;
import android.view.View;
import android.view.autofill.AutofillId;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BwField {
    public final AutofillId autofillId;
    public final int autofillType;
    public final int inputType;
    public final boolean focused;
    public final boolean selected;
    public final boolean clickable;
    public final boolean visible;
    public final String idEntry;
    public final String hint;
    public final String className;
    public final List<String> hints = new ArrayList<>();

    public BwField(AssistStructure.ViewNode node) {
        autofillId = node.getAutofillId();
        autofillType = node.getAutofillType();
        inputType = node.getInputType();
        focused = node.isFocused();
        selected = node.isSelected();
        clickable = node.isClickable();
        visible = node.getVisibility() == View.VISIBLE;
        idEntry = node.getIdEntry() == null ? "" : node.getIdEntry();
        hint = node.getHint() == null ? "" : node.getHint();
        className = node.getClassName() == null ? "" : node.getClassName().toString();

        String[] rawHints = node.getAutofillHints();
        if (rawHints != null) {
            for (String h : rawHints) {
                if (h != null && !h.trim().isEmpty()) {
                    hints.add(h.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    public boolean hasHint(String wanted) {
        if (wanted == null) return false;
        String w = wanted.toLowerCase(Locale.ROOT);
        for (String h : hints) {
            if (w.equals(h)) return true;
        }
        return false;
    }

    public boolean isPasswordByInputType() {
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        return (inputClass == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    public String searchableText() {
        return (idEntry + " " + hint).toLowerCase(Locale.ROOT);
    }
}
