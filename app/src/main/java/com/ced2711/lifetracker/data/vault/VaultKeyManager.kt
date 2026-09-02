package com.ced2711.lifetracker.data.vault

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VaultSession internal constructor(keyBytes: ByteArray) : AutoCloseable {
    private val keyBytes = keyBytes.clone()
    private var closed = false

    internal fun <T> useKey(block: (SecretKey) -> T): T {
        check(!closed) { "Vault session is closed." }
        return block(SecretKeySpec(keyBytes, "AES"))
    }

    internal fun copyKeyBytes(): ByteArray {
        check(!closed) { "Vault session is closed." }
        return keyBytes.clone()
    }

    /** Creates a caller-owned session without exposing the underlying key bytes. */
    internal fun fork(): VaultSession {
        val copiedKey = copyKeyBytes()
        return try {
            VaultSession(copiedKey)
        } finally {
            copiedKey.fill(0)
        }
    }

    override fun close() {
        if (!closed) {
            keyBytes.fill(0)
            closed = true
        }
    }
}

sealed class VaultKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)
class VaultNotInitializedException : VaultKeyException("The vault has not been initialized.")
class VaultKeyMissingException(alias: String) :
    VaultKeyException("The vault key '$alias' is missing. Reset the vault to start over.")
class VaultKeyInvalidatedException(cause: Throwable? = null) :
    VaultKeyException("The vault key was invalidated by a security-setting change.", cause)
class VaultAuthenticationRequiredException(cause: Throwable? = null) :
    VaultKeyException("User authentication is required to unlock the vault.", cause)
class VaultCorruptKeyEnvelopeException(cause: Throwable? = null) :
    VaultKeyException("The encrypted vault key is missing or corrupt.", cause)
class VaultKeyStateChangedException :
    VaultKeyException("The vault key state changed while authentication was in progress.")
class VaultUnsupportedDeviceException(message: String) : VaultKeyException(message)
class VaultKeyOperationException(message: String, cause: Throwable? = null) :
    VaultKeyException(message, cause)

/**
 * Owns the random 256-bit vault data-encryption key (DEK) envelopes. It never stores the raw DEK.
 * Authentication itself is intentionally performed by the UI with AndroidX BiometricPrompt.
 */
class VaultKeyManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val random = SecureRandom()

    class ModernPreparation internal constructor(
        val allowedAuthenticators: Int,
        internal val mode: Mode,
        internal val key: SecretKey,
        internal val envelope: Envelope?,
        internal val proposedDek: ByteArray?,
        internal val vaultWasInitialized: Boolean,
    ) : AutoCloseable {
        internal var consumed = false
        override fun close() {
            proposedDek?.fill(0)
            consumed = true
        }
    }

    class LegacyBiometricPreparation internal constructor(
        val allowedAuthenticators: Int,
        val cipher: Cipher,
        internal val mode: Mode,
        internal val envelope: Envelope?,
        internal val proposedDek: ByteArray?,
        internal val vaultWasInitialized: Boolean,
    ) : AutoCloseable {
        internal var consumed = false
        override fun close() {
            proposedDek?.fill(0)
            consumed = true
        }
    }

    class LegacyCredentialPreparation internal constructor(
        val allowedAuthenticators: Int,
        internal val mode: Mode,
        internal val key: SecretKey,
        internal val envelope: Envelope?,
        internal val proposedDek: ByteArray?,
        internal val vaultWasInitialized: Boolean,
    ) : AutoCloseable {
        internal var consumed = false
        override fun close() {
            proposedDek?.fill(0)
            consumed = true
        }
    }

    @Synchronized
    fun hasVault(): Boolean = ENVELOPE_PREFIXES.any(::hasEnvelopeState)

    @Synchronized
    fun hasModernEnvelope(): Boolean = hasEnvelopeState(MODERN_PREFIX)

    @Synchronized
    fun hasLegacyBiometricEnvelope(): Boolean = hasEnvelopeState(LEGACY_BIOMETRIC_PREFIX)

    @Synchronized
    fun hasLegacyCredentialEnvelope(): Boolean = hasEnvelopeState(LEGACY_CREDENTIAL_PREFIX)

    /** Removes only an unusable modern envelope; legacy recovery envelopes remain untouched. */
    @Synchronized
    fun discardModernEnvelope() {
        discardEnvelope(MODERN_PREFIX, MODERN_ALIAS)
    }

    /** Removes only an unusable biometric envelope; the credential recovery envelope remains. */
    @Synchronized
    fun discardLegacyBiometricEnvelope() {
        discardEnvelope(LEGACY_BIOMETRIC_PREFIX, LEGACY_BIOMETRIC_ALIAS)
    }

    /**
     * API 30+ uses one authentication-bound KEK. The caller authenticates without a CryptoObject,
     * then immediately calls [completeModern] while the Keystore authorization is valid.
     */
    @Synchronized
    fun prepareModern(sessionForEnrollment: VaultSession? = null): ModernPreparation {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw VaultUnsupportedDeviceException("Modern vault unlock requires Android 11 or newer.")
        }
        val envelope = readEnvelope(MODERN_PREFIX)
        val initialized = hasVault()
        val mode = if (envelope == null) Mode.WRAP else Mode.UNWRAP
        val proposedDek = when {
            envelope != null -> null
            initialized -> sessionForEnrollment?.copyKeyBytes()
                ?: throw VaultAuthenticationRequiredException()
            else -> randomDek()
        }
        val key = if (envelope == null) {
            replaceWithModernKey()
        } else {
            loadExistingKey(MODERN_ALIAS)
        }
        return ModernPreparation(
            allowedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            mode = mode,
            key = key,
            envelope = envelope,
            proposedDek = proposedDek,
            vaultWasInitialized = initialized,
        )
    }

    @Synchronized
    fun completeModern(preparation: ModernPreparation): VaultSession {
        consume(preparation.consumed)
        preparation.consumed = true
        try {
            validateState(MODERN_PREFIX, preparation.envelope, preparation.vaultWasInitialized)
            return when (preparation.mode) {
                Mode.WRAP -> {
                    val dek = requireNotNull(preparation.proposedDek)
                    val envelope = encryptEnvelope(preparation.key, dek, MODERN_PREFIX)
                    writeEnvelope(MODERN_PREFIX, envelope)
                    VaultSession(dek)
                }
                Mode.UNWRAP -> VaultSession(
                    decryptEnvelope(
                        preparation.key,
                        requireNotNull(preparation.envelope),
                        MODERN_PREFIX,
                    ),
                )
            }
        } finally {
            preparation.proposedDek?.fill(0)
        }
    }

    /** Uses a CryptoObject cipher and therefore requires BIOMETRIC_STRONG on API 26-29. */
    @Synchronized
    fun prepareLegacyBiometric(
        sessionForEnrollment: VaultSession? = null,
    ): LegacyBiometricPreparation {
        val envelope = readEnvelope(LEGACY_BIOMETRIC_PREFIX)
        validateLegacyPreparationSupported(envelope != null, Build.VERSION.SDK_INT)
        val initialized = hasVault()
        val proposedDek = when {
            envelope != null -> null
            initialized -> sessionForEnrollment?.copyKeyBytes()
                ?: throw VaultAuthenticationRequiredException()
            else -> randomDek()
        }
        val key = if (envelope == null) {
            replaceWithLegacyBiometricKey()
        } else {
            loadExistingKey(LEGACY_BIOMETRIC_ALIAS)
        }
        val cipher = newCipher()
        try {
            if (envelope == null) {
                cipher.init(Cipher.ENCRYPT_MODE, key)
            } else {
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, envelope.iv))
            }
        } catch (error: GeneralSecurityException) {
            proposedDek?.fill(0)
            throw mapKeyError(error)
        }
        return LegacyBiometricPreparation(
            allowedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG,
            cipher = cipher,
            mode = if (envelope == null) Mode.WRAP else Mode.UNWRAP,
            envelope = envelope,
            proposedDek = proposedDek,
            vaultWasInitialized = initialized,
        )
    }

    @Synchronized
    fun completeLegacyBiometric(
        preparation: LegacyBiometricPreparation,
        authenticatedCipher: Cipher,
    ): VaultSession {
        consume(preparation.consumed)
        preparation.consumed = true
        try {
            if (authenticatedCipher !== preparation.cipher) {
                throw VaultKeyOperationException("BiometricPrompt returned an unexpected cipher.")
            }
            validateState(
                LEGACY_BIOMETRIC_PREFIX,
                preparation.envelope,
                preparation.vaultWasInitialized,
            )
            // Android 8 authorizes the CryptoObject only after biometric success. Supplying AAD
            // during preparation permanently marks the operation as unauthenticated.
            authenticatedCipher.updateAAD(envelopeAad(LEGACY_BIOMETRIC_PREFIX))
            return when (preparation.mode) {
                Mode.WRAP -> {
                    val dek = requireNotNull(preparation.proposedDek)
                    val ciphertext = authenticatedCipher.doFinal(dek)
                    writeEnvelope(
                        LEGACY_BIOMETRIC_PREFIX,
                        Envelope(authenticatedCipher.iv, ciphertext),
                    )
                    VaultSession(dek)
                }
                Mode.UNWRAP -> VaultSession(
                    requireDek(
                        authenticatedCipher.doFinal(
                            requireNotNull(preparation.envelope).ciphertext,
                        ),
                    ),
                )
            }
        } catch (error: GeneralSecurityException) {
            throw mapKeyError(error)
        } finally {
            preparation.proposedDek?.fill(0)
        }
    }

    /**
     * The caller prompts for DEVICE_CREDENTIAL without a CryptoObject, then calls complete while
     * the short Keystore authorization window is active.
     */
    @Synchronized
    fun prepareLegacyCredential(
        sessionForEnrollment: VaultSession? = null,
    ): LegacyCredentialPreparation {
        val envelope = readEnvelope(LEGACY_CREDENTIAL_PREFIX)
        validateLegacyPreparationSupported(envelope != null, Build.VERSION.SDK_INT)
        val initialized = hasVault()
        val proposedDek = when {
            envelope != null -> null
            initialized -> sessionForEnrollment?.copyKeyBytes()
                ?: throw VaultAuthenticationRequiredException()
            else -> randomDek()
        }
        val key = if (envelope == null) {
            replaceWithLegacyCredentialKey()
        } else {
            loadExistingKey(LEGACY_CREDENTIAL_ALIAS)
        }
        return LegacyCredentialPreparation(
            allowedAuthenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            mode = if (envelope == null) Mode.WRAP else Mode.UNWRAP,
            key = key,
            envelope = envelope,
            proposedDek = proposedDek,
            vaultWasInitialized = initialized,
        )
    }

    @Synchronized
    fun completeLegacyCredential(preparation: LegacyCredentialPreparation): VaultSession {
        consume(preparation.consumed)
        preparation.consumed = true
        try {
            validateState(
                LEGACY_CREDENTIAL_PREFIX,
                preparation.envelope,
                preparation.vaultWasInitialized,
            )
            return when (preparation.mode) {
                Mode.WRAP -> {
                    val dek = requireNotNull(preparation.proposedDek)
                    val envelope = encryptEnvelope(
                        preparation.key,
                        dek,
                        LEGACY_CREDENTIAL_PREFIX,
                    )
                    writeEnvelope(LEGACY_CREDENTIAL_PREFIX, envelope)
                    VaultSession(dek)
                }
                Mode.UNWRAP -> VaultSession(
                    decryptEnvelope(
                        preparation.key,
                        requireNotNull(preparation.envelope),
                        LEGACY_CREDENTIAL_PREFIX,
                    ),
                )
            }
        } finally {
            preparation.proposedDek?.fill(0)
        }
    }

    /** Clears envelopes and all associated Android Keystore keys. It does not clear Room rows. */
    @Synchronized
    fun reset() {
        if (!preferences.edit().clear().commit()) {
            throw VaultKeyOperationException("Could not clear the vault key envelopes.")
        }
        try {
            KEY_ALIASES.forEach { alias ->
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            }
        } catch (error: GeneralSecurityException) {
            throw VaultKeyOperationException("Could not delete the vault keys.", error)
        }
    }

    private fun randomDek(): ByteArray = ByteArray(DEK_BYTES).also(random::nextBytes)

    private fun requireDek(bytes: ByteArray): ByteArray {
        if (bytes.size != DEK_BYTES) {
            bytes.fill(0)
            throw VaultCorruptKeyEnvelopeException()
        }
        return bytes
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun replaceWithModernKey(): SecretKey {
        deleteAliasIfPresent(MODERN_ALIAS)
        val builder = baseKeyBuilder(MODERN_ALIAS)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                AUTHORIZATION_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        return generateKey(builder)
    }

    private fun replaceWithLegacyBiometricKey(): SecretKey {
        deleteAliasIfPresent(LEGACY_BIOMETRIC_ALIAS)
        val builder = baseKeyBuilder(LEGACY_BIOMETRIC_ALIAS)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(-1)
            .setInvalidatedByBiometricEnrollment(true)
        return generateKey(builder)
    }

    private fun replaceWithLegacyCredentialKey(): SecretKey {
        deleteAliasIfPresent(LEGACY_CREDENTIAL_ALIAS)
        val builder = baseKeyBuilder(LEGACY_CREDENTIAL_ALIAS)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(AUTHORIZATION_SECONDS)
            .setInvalidatedByBiometricEnrollment(false)
        return generateKey(builder)
    }

    private fun baseKeyBuilder(alias: String) = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
        .setKeySize(DEK_BITS)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)

    private fun generateKey(spec: KeyGenParameterSpec.Builder): SecretKey = try {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec.build())
            generateKey()
        }
    } catch (error: GeneralSecurityException) {
        throw mapKeyError(error)
    }

    private fun loadExistingKey(alias: String): SecretKey = try {
        (keyStore.getKey(alias, null) as? SecretKey) ?: throw VaultKeyMissingException(alias)
    } catch (error: VaultKeyException) {
        throw error
    } catch (error: GeneralSecurityException) {
        throw mapKeyError(error)
    }

    private fun deleteAliasIfPresent(alias: String) {
        try {
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        } catch (error: GeneralSecurityException) {
            throw VaultKeyOperationException("Could not replace an unused vault key.", error)
        }
    }

    private fun encryptEnvelope(key: SecretKey, dek: ByteArray, prefix: String): Envelope = try {
        val cipher = newCipher()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(envelopeAad(prefix))
        Envelope(cipher.iv, cipher.doFinal(dek))
    } catch (error: GeneralSecurityException) {
        throw mapKeyError(error)
    }

    private fun decryptEnvelope(key: SecretKey, envelope: Envelope, prefix: String): ByteArray = try {
        val cipher = newCipher()
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, envelope.iv))
        cipher.updateAAD(envelopeAad(prefix))
        requireDek(cipher.doFinal(envelope.ciphertext))
    } catch (error: VaultKeyException) {
        throw error
    } catch (error: GeneralSecurityException) {
        throw mapKeyError(error)
    }

    private fun newCipher(): Cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)

    private fun envelopeAad(prefix: String): ByteArray =
        "TaskLedger/Vault/DEK/$ENVELOPE_FORMAT_VERSION/$prefix".encodeToByteArray()

    private fun hasEnvelopeState(prefix: String): Boolean =
        preferences.contains("$prefix.version") ||
            preferences.contains("$prefix.iv") ||
            preferences.contains("$prefix.ciphertext")

    private fun readEnvelope(prefix: String): Envelope? {
        if (!hasEnvelopeState(prefix)) return null
        val version = preferences.getInt("$prefix.version", -1)
        val ivText = preferences.getString("$prefix.iv", null)
        val ciphertextText = preferences.getString("$prefix.ciphertext", null)
        if (version != ENVELOPE_FORMAT_VERSION || ivText == null || ciphertextText == null) {
            throw VaultCorruptKeyEnvelopeException()
        }
        return try {
            val iv = Base64.decode(ivText, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextText, Base64.NO_WRAP)
            if (iv.size != GCM_IV_BYTES || ciphertext.size <= GCM_TAG_BYTES) {
                throw VaultCorruptKeyEnvelopeException()
            }
            Envelope(iv, ciphertext)
        } catch (error: IllegalArgumentException) {
            throw VaultCorruptKeyEnvelopeException(error)
        }
    }

    private fun writeEnvelope(prefix: String, envelope: Envelope) {
        val committed = preferences.edit()
            .putInt("$prefix.version", ENVELOPE_FORMAT_VERSION)
            .putString("$prefix.iv", Base64.encodeToString(envelope.iv, Base64.NO_WRAP))
            .putString(
                "$prefix.ciphertext",
                Base64.encodeToString(envelope.ciphertext, Base64.NO_WRAP),
            )
            .commit()
        if (!committed) throw VaultKeyOperationException("Could not save the encrypted vault key.")
    }

    private fun discardEnvelope(prefix: String, alias: String) {
        val committed = preferences.edit()
            .remove("$prefix.version")
            .remove("$prefix.iv")
            .remove("$prefix.ciphertext")
            .commit()
        if (!committed) {
            throw VaultKeyOperationException("Could not remove an unusable vault key envelope.")
        }
        deleteAliasIfPresent(alias)
    }

    private fun validateState(prefix: String, expected: Envelope?, vaultWasInitialized: Boolean) {
        val actual = readEnvelope(prefix)
        val unchanged = when {
            expected == null -> actual == null
            actual == null -> false
            else -> expected.contentEquals(actual)
        }
        if (!unchanged || (!vaultWasInitialized && expected == null && hasVault())) {
            throw VaultKeyStateChangedException()
        }
    }

    private fun consume(alreadyConsumed: Boolean) {
        if (alreadyConsumed) throw VaultKeyOperationException("Unlock preparation was already used.")
    }

    private fun mapKeyError(error: GeneralSecurityException): VaultKeyException = when (error) {
        is UserNotAuthenticatedException -> VaultAuthenticationRequiredException(error)
        is KeyPermanentlyInvalidatedException -> VaultKeyInvalidatedException(error)
        is AEADBadTagException -> VaultCorruptKeyEnvelopeException(error)
        else -> VaultKeyOperationException("Vault key operation failed.", error)
    }

    internal data class Envelope(val iv: ByteArray, val ciphertext: ByteArray) {
        fun contentEquals(other: Envelope): Boolean =
            iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
    }

    internal enum class Mode { WRAP, UNWRAP }

    private companion object {
        const val PREFERENCES_NAME = "taskledger_vault_key_envelopes"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val DEK_BITS = 256
        const val DEK_BYTES = DEK_BITS / 8
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        const val GCM_IV_BYTES = 12
        const val AUTHORIZATION_SECONDS = 30
        const val ENVELOPE_FORMAT_VERSION = 1

        const val MODERN_ALIAS = "taskledger.vault.modern.v1"
        const val LEGACY_BIOMETRIC_ALIAS = "taskledger.vault.legacy.biometric.v1"
        const val LEGACY_CREDENTIAL_ALIAS = "taskledger.vault.legacy.credential.v1"
        const val MODERN_PREFIX = "modern"
        const val LEGACY_BIOMETRIC_PREFIX = "legacy_biometric"
        const val LEGACY_CREDENTIAL_PREFIX = "legacy_credential"

        val ENVELOPE_PREFIXES = listOf(
            MODERN_PREFIX,
            LEGACY_BIOMETRIC_PREFIX,
            LEGACY_CREDENTIAL_PREFIX,
        )
        val KEY_ALIASES = listOf(
            MODERN_ALIAS,
            LEGACY_BIOMETRIC_ALIAS,
            LEGACY_CREDENTIAL_ALIAS,
        )
    }
}

/** Existing legacy envelopes remain unlockable after an Android 8-10 device upgrades to 11+. */
internal fun validateLegacyPreparationSupported(envelopePresent: Boolean, sdkInt: Int) {
    if (!envelopePresent && sdkInt >= Build.VERSION_CODES.R) {
        throw VaultUnsupportedDeviceException(
            "New legacy vault envelopes cannot be created on Android 11 or newer.",
        )
    }
}
