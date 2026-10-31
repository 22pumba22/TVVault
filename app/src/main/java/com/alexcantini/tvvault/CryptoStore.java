// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CryptoStore {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "TVVaultLocalKeyV1";
    private static final String PREFS = "tvvault_secure";
    private static final String DATA = "vault";

    private final Context context;

    public CryptoStore(Context context) {
        this.context = context.getApplicationContext();
    }

    private boolean readable;

    private SecretKey getKey(boolean create) throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);

        if (ks.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry =
                    (KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }

        if (!create) throw new IllegalStateException("Vault key unavailable");

        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
        );

        kg.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());

        return kg.generateKey();
    }

    private String encrypt(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey(true));

        JSONObject wrapper = new JSONObject();
        wrapper.put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        wrapper.put("ct", Base64.encodeToString(
                cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)),
                Base64.NO_WRAP
        ));
        return wrapper.toString();
    }

    private String decrypt(String wrapped) throws Exception {
        JSONObject wrapper = new JSONObject(wrapped);

        byte[] iv = Base64.decode(wrapper.getString("iv"), Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(wrapper.getString("ct"), Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                getKey(false),
                new GCMParameterSpec(128, iv)
        );

        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    public synchronized List<Credential> load() {
        readable = false;
        ArrayList<Credential> result = new ArrayList<>();
        String wrapped = context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(DATA, null);

        if (wrapped == null) { readable = true; return result; }

        try {
            JSONArray array = new JSONArray(decrypt(wrapped));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                result.add(new Credential(
                        o.getString("label"),
                        o.getString("package"),
                        o.getString("username"),
                        o.getString("password")
                ));
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Vault unreadable; writes blocked", failure);
        }
        readable = true;
        return result;
    }

    public synchronized void save(List<Credential> credentials) {
        if (!readable) throw new IllegalStateException("Read vault successfully before saving");
        // Recheck the persisted archive before any replacement.
        load();
        try {
            JSONArray array = new JSONArray();
            for (Credential c : credentials) {
                JSONObject o = new JSONObject();
                o.put("label", c.label);
                o.put("package", c.packageName);
                o.put("username", c.username);
                o.put("password", c.password);
                array.put(o);
            }

            String encrypted = encrypt(array.toString());
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(DATA, encrypted).commit())
                throw new IllegalStateException("Vault write failed");

        } catch (Exception e) {
            throw new RuntimeException("Unable to save encrypted vault", e);
        }
    }
}
