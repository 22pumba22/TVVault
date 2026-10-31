// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.view.View;
import android.view.autofill.AutofillId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BwFieldCollection {
    public final List<BwField> fields = new ArrayList<>();
    public final List<AutofillId> autofillIds = new ArrayList<>();
    public final List<AutofillId> ignoredIds = new ArrayList<>();
    public final Set<String> hints = new HashSet<>();
    public final Set<String> focusedHints = new HashSet<>();

    public void add(BwField field) {
        if (field == null || field.autofillId == null) return;
        if (!field.visible || isNonLogin(field)) { ignoredIds.add(field.autofillId); return; }
        fields.add(field);
        autofillIds.add(field.autofillId);
        for (String h : field.hints) {
            hints.add(h);
            if (field.focused) focusedHints.add(h);
        }
    }

    private boolean isNonLogin(BwField field) {
        String t = field.searchableText();
        for (String h : field.hints) {
            if (h.contains("newpassword") || h.contains("newusername") || h.contains("otp")) return true;
        }
        return t.contains("search") || t.contains("otp") || t.contains("verification") || t.contains("confirm_password");
    }

    public List<BwField> passwordFields() {
        ArrayList<BwField> result = new ArrayList<>();

        for (BwField f : fields) {
            if (f.hasHint(View.AUTOFILL_HINT_PASSWORD)) result.add(f);
        }
        if (!result.isEmpty()) return result;

        for (BwField f : fields) {
            if (f.isPasswordByInputType()) result.add(f);
        }
        if (!result.isEmpty()) return result;

        for (BwField f : fields) {
            String t = f.searchableText();
            if (t.contains("password") || t.contains("passwd") || t.contains("pswd")) {
                result.add(f);
            }
        }
        return result;
    }

    public List<BwField> usernameFields() {
        ArrayList<BwField> result = new ArrayList<>();

        for (BwField f : fields) {
            if (f.hasHint(View.AUTOFILL_HINT_EMAIL_ADDRESS)
                    || f.hasHint(View.AUTOFILL_HINT_USERNAME)
                    || f.hasHint(View.AUTOFILL_HINT_PHONE)) {
                result.add(f);
            }
        }
        if (!result.isEmpty()) return result;

        // Bitwarden fallback: field immediately preceding a password field.
        for (BwField pw : passwordFields()) {
            int idx = fields.indexOf(pw);
            if (idx > 0) {
                BwField candidate = fields.get(idx - 1);
                if (!candidate.isPasswordByInputType() && !isNonLogin(candidate) && !result.contains(candidate)) result.add(candidate);
            }
        }
        if (!result.isEmpty()) return result;

        for (BwField f : fields) {
            String t = f.searchableText();
            if (t.contains("email")
                    || t.contains("phone")
                    || t.contains("username")
                    || t.contains("login")
                    || t.contains("user")) {
                result.add(f);
            }
        }
        return result;
    }

    public boolean fillableForLogin() {
        for (String h : focusedHints) {
            if (View.AUTOFILL_HINT_USERNAME.equals(h)
                    || View.AUTOFILL_HINT_EMAIL_ADDRESS.equals(h)
                    || View.AUTOFILL_HINT_PASSWORD.equals(h)) {
                return true;
            }
        }

        for (BwField f : usernameFields()) if (f.focused) return true;
        for (BwField f : passwordFields()) if (f.focused) return true;

        /*
         * Selective mode:
         * do NOT treat every generic text field as login.
         * This avoids showing TV Vault on search boxes, comments, names, etc.
         *
         * A field is fillable only when we have an actual login signal:
         * - Android autofill hint (username/email/password)
         * - password inputType
         * - semantic id/hint text recognised by usernameFields/passwordFields
         */
        if (!usernameFields().isEmpty() || !passwordFields().isEmpty()) {
            return true;
        }

        return false;
    }
}
