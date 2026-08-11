package com.syncdeck.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureStore {
    private static final String KEY_ALIAS = "SyncDeck.ClientSecret.v1";
    private static final String PREFS = "syncdeck_secure";
    private static final String SECRET = "client_secret";
    private final SharedPreferences preferences;

    public SecureStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void putSecret(byte[] secret) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(secret);
        String value = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
                Base64.encodeToString(encrypted, Base64.NO_WRAP);
        if (!preferences.edit().putString(SECRET, value).commit()) throw new IllegalStateException("Falha ao armazenar a chave.");
    }

    public byte[] getSecret() {
        try {
            String value = preferences.getString(SECRET, null);
            if (value == null) return null;
            String[] parts = value.split("\\.", 2);
            if (parts.length != 2) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            byte[] secret = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP));
            return secret.length == 32 ? secret : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public void clear() {
        preferences.edit().remove(SECRET).apply();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {}
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
