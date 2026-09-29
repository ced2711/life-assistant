package com.ced2711.lifetracker.cloudsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveErrorMappingTest {
    private fun body(reason: String) = """{"error":{"code":403,"errors":[{"reason":"$reason"}]}}"""

    @Test
    fun disabledApiIsASetupProblemNotAnExpiredSignIn() {
        val error = googleDriveFailure(403, driveErrorReason(body("accessNotConfigured")), "Forbidden")
        assertTrue(error is CloudTransportException)
        assertTrue(error.message!!.contains("not enabled"))
    }

    @Test
    fun rateLimitsAndFullStorageSayWhatHappened() {
        assertTrue(googleDriveFailure(403, "userRateLimitExceeded", "").message!!.contains("limiting"))
        assertTrue(googleDriveFailure(429, null, "").message!!.contains("limiting"))
        assertTrue(googleDriveFailure(403, "storageQuotaExceeded", "").message!!.contains("full"))
    }

    @Test
    fun onlyRealSignInProblemsAskToReconnect() {
        assertTrue(googleDriveFailure(401, null, "") is CloudAuthorizationException)
        assertTrue(googleDriveFailure(403, "insufficientPermissions", "") is CloudAuthorizationException)
        assertTrue(googleDriveFailure(403, "somethingElse", "") is CloudTransportException)
    }

    @Test
    fun reasonsAreReadFromBothGoogleErrorShapes() {
        assertEquals("accessNotConfigured", driveErrorReason(body("accessNotConfigured")))
        assertEquals("SERVICE_DISABLED", driveErrorReason("""{"error":{"status":"SERVICE_DISABLED"}}"""))
        assertEquals(null, driveErrorReason("not json"))
    }
}
