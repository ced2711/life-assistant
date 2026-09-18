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

/** Stores the opt-in sync password encrypted by an app-owned Android Keystore key. */
class CloudSyncSecretStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    fun save(password: CharArray) {
        require(password.size >= 8)
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

    @Synchronized
    fun load(): CharArray? {
        val encodedIv = preferences.getString(KEY_IV, null) ?: return null
        val encodedCiphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        var iv = ByteArray(0)
        var ciphertext = ByteArray(0)
        return try {
            iv = encodedIv.decodeBase64()
            ciphertext = encodedCiphertext.decodeBase64()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            }
            val plaintext = cipher.doFinal(ciphertext)
            try {
                plaintext.toString(StandardCharsets.UTF_8).toCharArray()
            } finally {
                plaintext.fill(0)
            }
        } catch (_: Exception) {
            clear()
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
        runCatching { keyStore.deleteEntry(KEY_ALIAS) }
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
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
    }
}
