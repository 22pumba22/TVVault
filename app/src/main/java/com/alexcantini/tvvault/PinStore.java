// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Optional UI access lock. Does not change or derive the existing vault key. */
public final class PinStore {
    private static final int ITERATIONS = 210000;
    private final SharedPreferences prefs;

    public PinStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("tvvault_pin", Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        // Incomplete/corrupt configurations must never silently disable the lock.
        try { return prefs.contains("hash") || prefs.contains("salt") || prefs.getBoolean("enabled", false); }
        catch (RuntimeException failure) { return true; }
    }

    public static boolean valid(String pin) { return pin != null && pin.matches("[0-9]{4,12}"); }

    private static byte[] derive(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }

    public synchronized long remainingSeconds() {
        long remaining = prefs.getLong("until", 0) - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    public boolean verify(String pin) throws Exception {
        synchronized (PinStore.class) {
        if (!isEnabled()) return true;
        if (remainingSeconds() > 0) return false;
        byte[] salt = Base64.decode(prefs.getString("salt", ""), Base64.NO_WRAP);
        byte[] expected = Base64.decode(prefs.getString("hash", ""), Base64.NO_WRAP);
        if (salt.length != 32 || expected.length != 32) throw new IllegalStateException("Invalid PIN configuration");
        byte[] actual = derive(pin, salt);
        boolean match;
        try { match = MessageDigest.isEqual(expected, actual); }
        finally { Arrays.fill(actual, (byte) 0); Arrays.fill(expected, (byte) 0); }
        SharedPreferences.Editor edit = prefs.edit();
        if (match) edit.putInt("failures", 0).putLong("until", 0);
        else {
            int failures = Math.min(20, prefs.getInt("failures", 0) + 1);
            long delay = failures < 5 ? 0 : Math.min(300, 30L << Math.min(4, failures - 5));
            edit.putInt("failures", failures).putLong("until", System.currentTimeMillis() + delay * 1000);
        }
        if (!edit.commit()) throw new IllegalStateException("Cannot persist PIN attempt");
        return match;
        }
    }

    public void set(String pin) throws Exception {
        synchronized (PinStore.class) {
        if (!valid(pin)) throw new IllegalArgumentException("PIN length");
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        byte[] hash = derive(pin, salt);
        try {
            if (!prefs.edit().putBoolean("enabled", true)
                    .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString("hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                    .putInt("failures", 0).putLong("until", 0).commit())
                throw new IllegalStateException("Cannot save PIN");
        } finally { Arrays.fill(hash, (byte) 0); }
        }
    }

    public void disable() {
        synchronized (PinStore.class) {
        if (!prefs.edit().clear().commit()) throw new IllegalStateException("Cannot disable PIN");
        }
    }
}
