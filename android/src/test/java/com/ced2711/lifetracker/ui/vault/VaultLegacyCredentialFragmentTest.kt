package com.ced2711.lifetracker.ui.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultLegacyCredentialFragmentTest {
    @Test
    fun requestCodeUsesOnlyLowerSixteenBits() {
        assertEquals(0, VaultLegacyCredentialFragment.CREDENTIAL_REQUEST_CODE and 0xffff0000.toInt())
    }

    @Test
    fun resultIsBoundToRequestThatActuallyLaunched() {
        val buffer = VaultLegacyCredentialResultBuffer()
        assertTrue(buffer.begin(41L))

        val result = buffer.complete(authenticated = true)

        assertEquals(VaultLegacyCredentialResult(41L, true), result)
    }

    @Test
    fun inFlightAndBufferedMetadataSurviveBridgeRecreation() {
        val first = VaultLegacyCredentialResultBuffer()
        assertTrue(first.begin(73L))

        val afterConfigurationChange = VaultLegacyCredentialResultBuffer(first.metadata())
        val result = requireNotNull(afterConfigurationChange.complete(authenticated = false))
        afterConfigurationChange.buffer(result)
        val afterSecondConfigurationChange = VaultLegacyCredentialResultBuffer(
            afterConfigurationChange.metadata(),
        )

        assertEquals(VaultLegacyCredentialResult(73L, false), afterSecondConfigurationChange.takePendingResult())
        assertEquals(null, afterSecondConfigurationChange.takePendingResult())
        assertFalse(afterSecondConfigurationChange.begin(73L))
    }
}
