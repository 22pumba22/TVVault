// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;
/** Test-only localized host, never included in the production application. */
public final class LocalePreviewActivity extends MainActivity {
    static String language="en";
    @Override protected void attachBaseContext(android.content.Context base){
        android.content.res.Configuration config=new android.content.res.Configuration(base.getResources().getConfiguration());
        config.setLocale(java.util.Locale.forLanguageTag(language));
        super.attachBaseContext(base.createConfigurationContext(config));
    }
}
