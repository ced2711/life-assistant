package com.ced2711.lifetracker.desktop

import java.security.SecureRandom
import java.util.Base64

/**
 * Opens the data without asking when possible: with the password remembered on this PC, or, on
 * the very first start, with a new random password that the PC keeps for itself (protected like
 * every other saved secret, see [DesktopCredentialStore]). The user picks a real password only
 * when a feature needs one; until then [DesktopConfigStore.passwordChosen] is false.
 */
object DesktopLocalKey {
    /** True when the data is open; false means the user has to type the password. */
    suspend fun openWithoutAsking(
        dataStore: DesktopDataStore,
        credentials: DesktopCredentialStore,
        config: DesktopConfigStore,
    ): Boolean {
        credentials.load(DesktopCredentialStore.LOCAL_PASSWORD)?.let { saved ->
            return dataStore.open(saved)
        }
        if (dataStore.encryptedFile.isFile) return false
        val generated = newPassword()
        try {
            credentials.save(DesktopCredentialStore.LOCAL_PASSWORD, generated.copyOf())
            config.setPasswordChosen(false)
            return dataStore.open(generated.copyOf())
        } finally {
            generated.fill(' ')
        }
    }

    /**
     * Makes [password] the data password: re-encrypts the data and the daily copies, remembers it
     * on this PC and marks it as chosen by the user. The array is consumed.
     */
    suspend fun choose(
        password: CharArray,
        dataStore: DesktopDataStore,
        credentials: DesktopCredentialStore,
        config: DesktopConfigStore,
    ): Boolean {
        try {
            if (!dataStore.changePassword(password.copyOf())) return false
            credentials.save(DesktopCredentialStore.LOCAL_PASSWORD, password.copyOf())
            config.setPasswordChosen(true)
            return true
        } finally {
            password.fill(' ')
        }
    }

    private fun newPassword(): CharArray {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return try {
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toCharArray()
        } finally {
            bytes.fill(0)
        }
    }
}
