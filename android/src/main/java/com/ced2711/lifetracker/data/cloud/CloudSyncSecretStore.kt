package com.ced2711.lifetracker.data.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores one opt-in secret (the sync password, or a GitHub token) under an app-owned Keystore key. */
class CloudSyncSecretStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
    private val keyAlias: String = KEY_ALIAS,
    private val minimumLength: Int = 8,
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    fun save(password: CharArray) {
        require(password.size >= minimumLength)
        val plaintext = password.concatToString().toByteArray(StandardCharsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            }
            val ciphertext = cipher.doFinal(plaintext)
            try {
                check(
                    preferences.edit()
                        .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                        .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                        .commit(),
                ) { "Could not save the cloud sync password." }
            } finally {
                ciphertext.fill(0)
            }
        } finally {
            plaintext.fill(0)
            password.fill('\u0000')
        }
    }

    /**
     * The saved secret, or null when none is saved or it cannot be read right now. A failed read
     * never deletes anything: the Keystore sometimes refuses work for a moment (for example
     * right after the phone restarts at night), and deleting then would silently disconnect sync.
     * Only [clear] removes the secret.
     */
    @Synchronized
    fun load(): CharArray? {
        val encodedIv = preferences.getString(KEY_IV, null) ?: return null
        val encodedCiphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        repeat(READ_ATTEMPTS) { attempt ->
            decrypt(encodedIv, encodedCiphertext)?.let { return it }
            if (attempt < READ_ATTEMPTS - 1) Thread.sleep(RETRY_DELAY_MILLIS * (attempt + 1))
        }
        return null
    }

    private fun decrypt(encodedIv: String, encodedCiphertext: String): CharArray? {
        var iv = ByteArray(0)
        var ciphertext = ByteArray(0)
        return try {
            // A missing key cannot decrypt anything; never create a new one while reading.
            val key = keyStore.getKey(keyAlias, null) as? SecretKey ?: return null
            iv = encodedIv.decodeBase64()
            ciphertext = encodedCiphertext.decodeBase64()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            }
            val plaintext = cipher.doFinal(ciphertext)
            try {
                plaintext.toString(StandardCharsets.UTF_8).toCharArray()
            } finally {
                plaintext.fill(0)
            }
        } catch (_: Exception) {
            null
        } finally {
            iv.fill(0)
            ciphertext.fill(0)
        }
    }

    @Synchronized
    fun hasSecret(): Boolean = preferences.contains(KEY_IV) && preferences.contains(KEY_CIPHERTEXT)

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY_IV).remove(KEY_CIPHERTEXT).commit()
        runCatching { keyStore.deleteEntry(keyAlias) }
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val PREFERENCES_NAME = "life_tracker_cloud_sync_secret"
        const val KEY_IV = "iv"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_ALIAS = "life_tracker_cloud_sync_password_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val READ_ATTEMPTS = 3
        const val RETRY_DELAY_MILLIS = 250L
    }
}
