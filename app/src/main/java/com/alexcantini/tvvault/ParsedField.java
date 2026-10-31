// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.view.autofill.AutofillId;

public class ParsedField {
    public final AutofillId id;
    public final String kind; // username | password | generic

    public ParsedField(AutofillId id, String kind) {
        this.id = id;
        this.kind = kind;
    }
}
