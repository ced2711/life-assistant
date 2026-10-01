package com.ced2711.lifetracker.data.cloud

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleDriveAuthorizationPolicyTest {
    @Test
    fun knownPlayServicesFailuresRemainActionable() {
        assertEquals(
            GoogleDriveAuthorizationFailureKind.NETWORK,
            classifyGoogleDriveAuthorizationStatus(7),
        )
        assertEquals(
            GoogleDriveAuthorizationFailureKind.SIGN_IN,
            classifyGoogleDriveAuthorizationStatus(4),
        )
        assertEquals(
            GoogleDriveAuthorizationFailureKind.CONFIGURATION,
            classifyGoogleDriveAuthorizationStatus(10),
        )
        assertEquals(
            GoogleDriveAuthorizationFailureKind.CANCELLED,
            classifyGoogleDriveAuthorizationStatus(16),
        )
    }

    @Test
    fun unknownStatusDoesNotPretendTheUserCancelled() {
        assertEquals(
            GoogleDriveAuthorizationFailureKind.UNKNOWN,
            classifyGoogleDriveAuthorizationStatus(9999),
        )
    }

    @Test
    fun onlyAndroidCancelledResultIsClassifiedAsCancellationWhenIntentIsMissing() {
        assertEquals(
            GoogleDriveAuthorizationFailureKind.CANCELLED,
            classifyConsentResultWithoutIntent(Activity.RESULT_CANCELED),
        )
        assertEquals(
            GoogleDriveAuthorizationFailureKind.UNKNOWN,
            classifyConsentResultWithoutIntent(Activity.RESULT_OK),
        )
        assertEquals(
            GoogleDriveAuthorizationFailureKind.UNKNOWN,
            classifyConsentResultWithoutIntent(Activity.RESULT_FIRST_USER),
        )
    }
}
