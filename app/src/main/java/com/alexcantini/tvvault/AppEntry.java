// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.graphics.drawable.Drawable;

public class AppEntry {
    public final String label;
    public final String packageName;
    public final Drawable icon;

    public AppEntry(String label, String packageName, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
    }
}
