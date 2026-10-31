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

public class LegacyCryptoStore {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "TVVaultLocalKeyV1";
    private static final String PREFS = "tvvault_secure";
    private static final String DATA = "vault";

    private final Context context;

    public LegacyCryptoStore(Context context) {
        this.context = context.getApplicationContext();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);

        if (ks.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry =
                    (KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }

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
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());

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
                getOrCreateKey(),
                new GCMParameterSpec(128, iv)
        );

        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    public synchronized List<Credential> load() {
        ArrayList<Credential> result = new ArrayList<>();
        String wrapped = context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(DATA, null);

        if (wrapped == null || wrapped.isEmpty()) return result;

        try {
            JSONArray array = new JSONArray(decrypt(wrapped));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                result.add(new Credential(
                        o.optString("label"),
                        o.optString("package"),
                        o.optString("username"),
                        o.optString("password")
                ));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public synchronized void save(List<Credential> credentials) {
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

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(DATA, encrypt(array.toString()))
                    .apply();

        } catch (Exception e) {
            throw new RuntimeException("Unable to save encrypted vault", e);
        }
    }
}
