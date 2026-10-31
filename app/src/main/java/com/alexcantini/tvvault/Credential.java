// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

public class Credential {
    public final String label;
    public final String packageName;
    public final String username;
    public final String password;

    public Credential(String label, String packageName, String username, String password) {
        this.label = label == null ? "" : label;
        this.packageName = packageName == null ? "" : packageName;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
    }
}
