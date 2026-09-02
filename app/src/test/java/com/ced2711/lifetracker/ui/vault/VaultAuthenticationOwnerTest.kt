package com.ced2711.lifetracker.ui.vault

import androidx.biometric.BiometricManager
import com.ced2711.lifetracker.data.vault.VaultKeyManager
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultAuthenticationOwnerTest {
    @Test
    fun invalidationBeforePublicationRejectsAndClosesPreparedRequest() {
        val owner = VaultAuthenticationOwner<CloseSpy>(initialGeneration = 100L)
        val requestId = owner.nextId()
        val obsolete = CloseSpy()

        owner.invalidate()
        val published = owner.publish(requestId, eligible = true, prepared = obsolete)

        assertFalse(published)
        assertTrue(obsolete.closed)
        assertNull(owner.take(requestId))
    }

    @Test
    fun oldCompletionGenerationCannotPublishAfterNewRequestStarts() {
        val owner = VaultAuthenticationOwner<CloseSpy>(initialGeneration = 200L)
        val requestId = owner.nextId()
        val request = CloseSpy()
        assertTrue(owner.publish(requestId, eligible = true, prepared = request))
        assertEquals(request, owner.take(requestId))
        val completionGeneration = owner.generation

        owner.nextId()

        assertFalse(owner.isCurrent(completionGeneration))
    }

    @Test
    fun resetAuthenticationUsesCredentialOnlyBeforeApi30AndModernSystemAuthAfterward() {
        assertEquals(
            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            resetAuthenticatorsForSdk(26),
        )
        assertEquals(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            resetAuthenticatorsForSdk(30),
        )
        val request = resetAuthenticationRequest(id = 301L, sdkInt = 26)
        assertEquals(301L, request.id)
        assertEquals(VaultAuthenticationPurpose.RESET, request.purpose)
        assertTrue(request.usesExternalDeviceCredentialPrompt(sdkInt = 26))
        assertFalse(request.usesExternalDeviceCredentialPrompt(sdkInt = 30))
    }

    @Test
    fun onlyPre30ResetSystemAuthenticationUsesExternalCredentialActivity() {
        val nonReset = VaultAuthenticationRequest.SystemAuthentication(
            id = 401L,
            purpose = VaultAuthenticationPurpose.ACCESS,
            allowedAuthenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )

        assertFalse(nonReset.usesExternalDeviceCredentialPrompt(sdkInt = 26))
        assertFalse(nonReset.usesExternalDeviceCredentialPrompt(sdkInt = 30))
    }

    @Test
    fun legacyCredentialAlwaysUsesExternalCredentialActivity() {
        val request = VaultAuthenticationRequest.LegacyCredential(
            id = 501L,
            purpose = VaultAuthenticationPurpose.ACCESS,
            preparation = VaultKeyManager.LegacyCredentialPreparation(
                allowedAuthenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                mode = VaultKeyManager.Mode.WRAP,
                key = SecretKeySpec(ByteArray(32), "AES"),
                envelope = null,
                proposedDek = ByteArray(32),
                vaultWasInitialized = false,
            ),
        )

        assertTrue(request.usesExternalDeviceCredentialPrompt(sdkInt = 26))
        assertTrue(request.usesExternalDeviceCredentialPrompt(sdkInt = 30))
        request.close()
    }

    private class CloseSpy : AutoCloseable {
        var closed = false
        override fun close() {
            closed = true
        }
    }
}
