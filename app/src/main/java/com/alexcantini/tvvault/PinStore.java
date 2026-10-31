// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Optional access gate; independent from the unchanged AES vault encryption. */
public final class PinStore {
    private static final int LEGACY_ITERATIONS = 210000;
    private static final String FORMAT = "keystore-hmac-v2";
    private static final String ALIAS = "TVVaultPinKeyV2";
    private final SharedPreferences prefs;
    interface KeySource { SecretKey get(boolean create) throws Exception; }
    private final KeySource keys;

    public PinStore(Context context) { this(context, PinStore::androidKey); }
    PinStore(Context context, KeySource keys) {
        prefs = context.getApplicationContext().getSharedPreferences("tvvault_pin", Context.MODE_PRIVATE);
        this.keys = keys;
    }
    private static SecretKey androidKey(boolean create) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        if (!create) throw new IllegalStateException("PIN key unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setKeySize(256).setDigests(KeyProperties.DIGEST_SHA256).build());
        return generator.generateKey();
    }
    public boolean isEnabled() {
        try { return prefs.contains("hash") || prefs.contains("salt") || prefs.contains("format") || prefs.getBoolean("enabled", false); }
        catch (RuntimeException failure) { return true; }
    }
    public boolean isLegacy() { return isEnabled() && !prefs.contains("format"); }
    public static boolean valid(String pin) { return pin != null && pin.matches("[0-9]{4,12}"); }
    private static byte[] legacy(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, LEGACY_ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    private byte[] tag(String pin, byte[] salt, boolean create) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(keys.get(create));
        mac.update("TVVault PIN v2\0".getBytes(StandardCharsets.UTF_8));
        mac.update(salt);
        byte[] value = pin.getBytes(StandardCharsets.UTF_8);
        try { return mac.doFinal(value); } finally { Arrays.fill(value, (byte) 0); }
    }
    public long remainingSeconds() {
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
            String format = prefs.getString("format", "");
            if (!format.isEmpty() && !FORMAT.equals(format)) throw new IllegalStateException("Unknown PIN format");
            byte[] actual = format.isEmpty() ? legacy(pin, salt) : tag(pin, salt, false);
            boolean match;
            try { match = MessageDigest.isEqual(expected, actual); }
            finally { Arrays.fill(actual, (byte) 0); Arrays.fill(expected, (byte) 0); }
            // One successful legacy check upgrades atomically; never change a PIN after a wrong guess.
            if (match && format.isEmpty()) { set(pin); return true; }
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
            byte[] salt = new byte[32]; new SecureRandom().nextBytes(salt);
            byte[] hash = tag(pin, salt, true);
            try {
                if (!prefs.edit().putBoolean("enabled", true).putString("format", FORMAT)
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
