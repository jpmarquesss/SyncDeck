package com.syncdeck.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class SecureStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun putSecret(secret: ByteArray) {
        require(secret.size == 32) { "Chave de pareamento inválida." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(secret)
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(preferences.edit().putString(SECRET, value).commit()) { "Falha ao armazenar a chave." }
    }

    fun getSecret(): ByteArray? {
        return runCatching {
            val value = preferences.getString(SECRET, null) ?: return null
            val parts = value.split('.', limit = 2)
            if (parts.size != 2) return null
            val key = getExistingKey() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).takeIf { it.size == 32 }
        }.getOrNull()
    }

    fun clear() {
        preferences.edit().remove(SECRET).commit()
        runCatching {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun getExistingKey(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return if (store.containsAlias(KEY_ALIAS)) store.getKey(KEY_ALIAS, null) as? SecretKey else null
    }

    private companion object {
        const val KEY_ALIAS = "SyncDeck.ClientSecret.v1"
        const val PREFS = "syncdeck_secure"
        const val SECRET = "client_secret"
    }
}

internal object ProtocolCrypto {
    const val ENCRYPTION_PROTOCOL = "aes-256-cbc-v1"
    private val encryptionContext = "SyncDeck.Encryption.v1".toByteArray(StandardCharsets.UTF_8)

    fun bodyHash(body: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(body)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun sign(secret: ByteArray, method: String, path: String, timestamp: Long, nonce: String, body: ByteArray): String {
        val canonical = "${method.uppercase()}\n$path\n$timestamp\n$nonce\n${bodyHash(body)}"
        return base64Url(hmac(secret, canonical.toByteArray(StandardCharsets.UTF_8)))
    }

    fun signResponse(secret: ByteArray, status: Int, nonce: String, body: ByteArray): String {
        val canonical = "RESPONSE\n$status\n$nonce\n${bodyHash(body)}"
        return base64Url(hmac(secret, canonical.toByteArray(StandardCharsets.UTF_8)))
    }

    fun encrypt(secret: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(16).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encryptionKey(secret), "AES"), IvParameterSpec(iv))
        return iv + cipher.doFinal(plaintext)
    }

    fun decrypt(secret: ByteArray, payload: ByteArray, maximumBytes: Int): ByteArray {
        require(payload.size >= 32 && payload.size % 16 == 0) { "Resposta criptografada inválida." }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(encryptionKey(secret), "AES"),
            IvParameterSpec(payload.copyOfRange(0, 16)),
        )
        return cipher.doFinal(payload, 16, payload.size - 16).also {
            require(it.size <= maximumBytes) { "Resposta descriptografada muito grande." }
        }
    }

    fun constantTimeEquals(expected: String?, supplied: String?): Boolean = MessageDigest.isEqual(
        (expected ?: "").toByteArray(StandardCharsets.US_ASCII),
        (supplied ?: "").toByteArray(StandardCharsets.US_ASCII),
    )

    fun base64Url(value: ByteArray): String = Base64.encodeToString(
        value,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private fun encryptionKey(secret: ByteArray): ByteArray = hmac(secret, encryptionContext)

    private fun hmac(secret: ByteArray, value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(secret, "HmacSHA256"))
        doFinal(value)
    }
}
