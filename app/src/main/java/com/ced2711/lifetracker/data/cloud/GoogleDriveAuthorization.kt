package com.ced2711.lifetracker.data.cloud

import android.app.PendingIntent
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.ced2711.lifetracker.cloudsync.AccessTokenProvider
import com.ced2711.lifetracker.cloudsync.CloudAuthorizationException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

sealed interface GoogleDriveAuthorizationResult {
    data class Authorized(val accessToken: String) : GoogleDriveAuthorizationResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : GoogleDriveAuthorizationResult
}

/** A safe, user-actionable classification of an authorization failure. */
enum class GoogleDriveAuthorizationFailureKind {
    CANCELLED,
    CONFIGURATION,
    NETWORK,
    SIGN_IN,
    PERMISSION_DENIED,
    UNKNOWN,
}

class GoogleDriveAuthorizationFailure(
    val kind: GoogleDriveAuthorizationFailureKind,
    val statusCode: Int? = null,
    message: String,
    cause: Throwable? = null,
) : CloudAuthorizationException(message, cause)

/** Maps Google Play services status codes without exposing exception text or account data. */
internal fun classifyGoogleDriveAuthorizationStatus(statusCode: Int):
    GoogleDriveAuthorizationFailureKind = when (statusCode) {
    7 -> GoogleDriveAuthorizationFailureKind.NETWORK
    4, 12500 -> GoogleDriveAuthorizationFailureKind.SIGN_IN
    10 -> GoogleDriveAuthorizationFailureKind.CONFIGURATION
    16 -> GoogleDriveAuthorizationFailureKind.CANCELLED
    else -> GoogleDriveAuthorizationFailureKind.UNKNOWN
}

internal fun classifyConsentResultWithoutIntent(
    resultCode: Int,
): GoogleDriveAuthorizationFailureKind =
    if (resultCode == Activity.RESULT_CANCELED) {
        GoogleDriveAuthorizationFailureKind.CANCELLED
    } else {
        GoogleDriveAuthorizationFailureKind.UNKNOWN
    }

class GoogleDriveAuthorization(context: Context) : AccessTokenProvider {
    private val client = Identity.getAuthorizationClient(context.applicationContext)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        .build()

    suspend fun authorize(): GoogleDriveAuthorizationResult {
        val result = try {
            client.authorize(request).await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            throw error.toAuthorizationFailure()
        }
        return result.toCloudResult()
    }

    /**
     * Parses the returned Intent even when Android reports RESULT_CANCELED. Google can put an
     * ApiException/status in that Intent, and collapsing it into cancellation hides bad OAuth
     * configuration (the common status 10 failure).
     */
    fun completeConsent(
        data: Intent?,
        resultCode: Int = Activity.RESULT_OK,
    ): GoogleDriveAuthorizationResult {
        if (data == null) {
            val kind = if (resultCode == Activity.RESULT_OK) {
                GoogleDriveAuthorizationFailureKind.UNKNOWN
            } else {
                classifyConsentResultWithoutIntent(resultCode)
            }
            val resultDetail = if (resultCode == Activity.RESULT_OK) {
                "Google Drive authorization returned no result."
            } else {
                "Google Drive authorization returned no result (result code $resultCode)."
            }
            throw authorizationFailure(
                kind,
                null,
                resultDetail.takeUnless {
                    kind == GoogleDriveAuthorizationFailureKind.CANCELLED
                },
            )
        }
        val result = try {
            client.getAuthorizationResultFromIntent(data)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            throw error.toAuthorizationFailure()
        }
        return result.toCloudResult()
    }

    override suspend fun accessToken(): String = when (val result = authorize()) {
        is GoogleDriveAuthorizationResult.Authorized -> result.accessToken
        is GoogleDriveAuthorizationResult.NeedsConsent -> throw CloudConsentRequiredException()
    }

    suspend fun disconnect() {
        val token = runCatching {
            (authorize() as? GoogleDriveAuthorizationResult.Authorized)?.accessToken
        }.getOrNull() ?: return
        runCatching {
            client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
        }
    }

    private fun AuthorizationResult.toCloudResult(): GoogleDriveAuthorizationResult {
        pendingIntent?.let { return GoogleDriveAuthorizationResult.NeedsConsent(it) }
        val token = accessToken?.takeIf(String::isNotBlank)
            ?: throw authorizationFailure(
                GoogleDriveAuthorizationFailureKind.PERMISSION_DENIED,
                null,
                "Google Drive permission was not granted.",
            )
        if (grantedScopes.none { it.toString() == DRIVE_APPDATA_SCOPE }) {
            throw authorizationFailure(
                GoogleDriveAuthorizationFailureKind.PERMISSION_DENIED,
                null,
                "Google Drive app-data access was not granted.",
            )
        }
        return GoogleDriveAuthorizationResult.Authorized(token)
    }

    private fun Throwable.toAuthorizationFailure(): GoogleDriveAuthorizationFailure {
        if (this is GoogleDriveAuthorizationFailure) return this
        val statusCode = (this as? ApiException)?.statusCode
        val kind = statusCode?.let(::classifyGoogleDriveAuthorizationStatus)
            ?: GoogleDriveAuthorizationFailureKind.UNKNOWN
        return authorizationFailure(kind, statusCode, cause = this)
    }
}

private fun authorizationFailure(
    kind: GoogleDriveAuthorizationFailureKind,
    statusCode: Int?,
    message: String? = null,
    cause: Throwable? = null,
): GoogleDriveAuthorizationFailure {
    val code = statusCode?.let { " (code $it)" }.orEmpty()
    val safeMessage = message ?: when (kind) {
        GoogleDriveAuthorizationFailureKind.CANCELLED ->
            "Google Drive authorization was cancelled or closed. If you did not cancel, check the app's Google Cloud setup$code."
        GoogleDriveAuthorizationFailureKind.CONFIGURATION ->
            "Google Drive setup is incomplete$code. Check Drive API, package name, signing certificate SHA-1, and OAuth project."
        GoogleDriveAuthorizationFailureKind.NETWORK ->
            "Google Drive could not reach the network."
        GoogleDriveAuthorizationFailureKind.SIGN_IN ->
            "Google account sign-in failed."
        GoogleDriveAuthorizationFailureKind.PERMISSION_DENIED ->
            "Google Drive permission was not granted."
        GoogleDriveAuthorizationFailureKind.UNKNOWN ->
            "Google Drive authorization failed$code."
    }
    return GoogleDriveAuthorizationFailure(kind, statusCode, safeMessage, cause)
}

class CloudConsentRequiredException : CloudAuthorizationException(
    "Open Life Tracker to finish connecting Google Drive.",
)
