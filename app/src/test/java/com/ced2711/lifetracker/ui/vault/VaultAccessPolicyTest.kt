package com.ced2711.lifetracker.ui.vault

import androidx.biometric.BiometricPrompt
import com.ced2711.lifetracker.data.vault.VaultCorruptKeyEnvelopeException
import com.ced2711.lifetracker.data.vault.VaultKeyInvalidatedException
import com.ced2711.lifetracker.data.vault.VaultKeyMissingException
import com.ced2711.lifetracker.data.vault.VaultKeyOperationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultAccessPolicyTest {
    @Test
    fun biometricLockoutsUseTheFallbackCapableUnavailablePath() {
        assertTrue(isVaultAuthenticationUnavailableError(BiometricPrompt.ERROR_LOCKOUT))
        assertTrue(isVaultAuthenticationUnavailableError(BiometricPrompt.ERROR_LOCKOUT_PERMANENT))
        assertFalse(isVaultAuthenticationUnavailableError(BiometricPrompt.ERROR_TIMEOUT))
    }

    @Test
    fun legacyDevicePrefersBiometricButKeepsCredentialRecoveryRoute() {
        val routes = vaultAccessRoutes(
            VaultAccessFacts(
                sdkInt = 29,
                hasVault = true,
                hasModernEnvelope = false,
                hasLegacyBiometricEnvelope = true,
                hasLegacyCredentialEnvelope = true,
                canUseStrongBiometric = true,
            ),
        )

        assertEquals(
            listOf(VaultAccessRoute.LEGACY_BIOMETRIC, VaultAccessRoute.LEGACY_CREDENTIAL),
            routes,
        )
        assertEquals(
            listOf(VaultAccessRoute.LEGACY_CREDENTIAL),
            routesAfter(routes, VaultAccessRoute.LEGACY_BIOMETRIC),
        )
    }

    @Test
    fun upgradedDeviceFallsBackFromModernThroughLegacyEnvelopes() {
        assertEquals(
            listOf(
                VaultAccessRoute.MODERN,
                VaultAccessRoute.LEGACY_BIOMETRIC,
                VaultAccessRoute.LEGACY_CREDENTIAL,
            ),
            vaultAccessRoutes(
                VaultAccessFacts(
                    sdkInt = 30,
                    hasVault = true,
                    hasModernEnvelope = true,
                    hasLegacyBiometricEnvelope = true,
                    hasLegacyCredentialEnvelope = true,
                    canUseStrongBiometric = true,
                ),
            ),
        )
    }

    @Test
    fun unavailableBiometricIsSkippedWithoutDiscardingCredentialRecovery() {
        assertEquals(
            listOf(VaultAccessRoute.LEGACY_CREDENTIAL),
            vaultAccessRoutes(
                VaultAccessFacts(
                    sdkInt = 29,
                    hasVault = true,
                    hasModernEnvelope = false,
                    hasLegacyBiometricEnvelope = true,
                    hasLegacyCredentialEnvelope = true,
                    canUseStrongBiometric = false,
                ),
            ),
        )
    }

    @Test
    fun freshVaultHasNoFallbackThatCouldCreateASecondDek() {
        assertEquals(
            listOf(VaultAccessRoute.CREATE_MODERN),
            vaultAccessRoutes(
                VaultAccessFacts(
                    sdkInt = 36,
                    hasVault = false,
                    hasModernEnvelope = false,
                    hasLegacyBiometricEnvelope = false,
                    hasLegacyCredentialEnvelope = false,
                    canUseStrongBiometric = true,
                ),
            ),
        )
    }

    @Test
    fun onlyPermanentEnvelopeFailuresTriggerAutomaticRecovery() {
        assertTrue(isRecoverablePreferredEnvelopeFailure(VaultKeyInvalidatedException()))
        assertTrue(isRecoverablePreferredEnvelopeFailure(VaultKeyMissingException("missing")))
        assertTrue(isRecoverablePreferredEnvelopeFailure(VaultCorruptKeyEnvelopeException()))
        assertFalse(
            isRecoverablePreferredEnvelopeFailure(
                VaultKeyOperationException("Temporary Keystore failure."),
            ),
        )
    }
}
