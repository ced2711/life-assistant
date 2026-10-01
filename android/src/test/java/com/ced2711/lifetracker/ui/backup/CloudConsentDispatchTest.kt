package com.ced2711.lifetracker.ui.backup

import com.ced2711.lifetracker.data.cloud.GoogleDriveAuthorizationFailure
import com.ced2711.lifetracker.data.cloud.GoogleDriveAuthorizationFailureKind
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudConsentDispatchTest {
    @Test
    fun consentRequestIsDispatchedOnlyOnceForItsNonce() {
        assertTrue(shouldDispatchCloudConsent(42L, expectedRequestId = 42L, alreadyDispatched = false))
        assertFalse(shouldDispatchCloudConsent(42L, expectedRequestId = 42L, alreadyDispatched = true))
        assertFalse(shouldDispatchCloudConsent(41L, expectedRequestId = 42L, alreadyDispatched = false))
        assertFalse(shouldDispatchCloudConsent(null, expectedRequestId = 42L, alreadyDispatched = false))
    }

    @Test
    fun setupAndNetworkFailuresAreNotReportedAsCancellation() {
        val setup = cloudConnectionFailureMessage(
            GoogleDriveAuthorizationFailure(
                kind = GoogleDriveAuthorizationFailureKind.CONFIGURATION,
                statusCode = 10,
                message = "Google Drive setup is incomplete (code 10).",
            ),
            authorizationComplete = false,
        )
        val network = cloudConnectionFailureMessage(
            GoogleDriveAuthorizationFailure(
                kind = GoogleDriveAuthorizationFailureKind.NETWORK,
                message = "Google Drive could not reach the network.",
            ),
            authorizationComplete = false,
        )

        assertTrue(setup.contains("code 10"))
        assertTrue(network.contains("network"))
        assertNotEquals("Google Drive connection was cancelled.", setup)
        assertNotEquals("Google Drive connection was cancelled.", network)
    }

    @Test
    fun localCredentialFailureAfterAuthorizationIsDistinctFromCancellation() {
        assertEquals(
            "Google Drive connection failed while saving local credentials.",
            cloudConnectionFailureMessage(IOException("keystore unavailable"), authorizationComplete = true),
        )
    }
}
