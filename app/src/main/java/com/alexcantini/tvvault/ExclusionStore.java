// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class ExclusionStore {
    private static final String PREFS = "tvvault_exclusions";
    private static final String KEY = "packages";

    private final SharedPreferences prefs;

    public ExclusionStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Set<String> getAll() {
        return new HashSet<>(prefs.getStringSet(KEY, new HashSet<>()));
    }

    public boolean isExcluded(String packageName) {
        return packageName != null && getAll().contains(packageName);
    }

    public void setExcluded(String packageName, boolean excluded) {
        if (packageName == null || packageName.isEmpty()) return;

        Set<String> set = getAll();
        if (excluded) set.add(packageName);
        else set.remove(packageName);

        prefs.edit().putStringSet(KEY, set).apply();
    }
}
