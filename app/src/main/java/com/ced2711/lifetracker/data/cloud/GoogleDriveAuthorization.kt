package com.ced2711.lifetracker.data.cloud

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ced2711.lifetracker.cloudsync.AccessTokenProvider
import com.ced2711.lifetracker.cloudsync.CloudAuthorizationException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

sealed interface GoogleDriveAuthorizationResult {
    data class Authorized(val accessToken: String) : GoogleDriveAuthorizationResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : GoogleDriveAuthorizationResult
}

class GoogleDriveAuthorization(context: Context) : AccessTokenProvider {
    private val client = Identity.getAuthorizationClient(context.applicationContext)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        .build()

    suspend fun authorize(): GoogleDriveAuthorizationResult {
        val result = client.authorize(request).await()
        return result.toCloudResult()
    }

    fun completeConsent(data: Intent?): GoogleDriveAuthorizationResult {
        if (data == null) throw CloudAuthorizationException("Google Drive authorization was cancelled.")
        return client.getAuthorizationResultFromIntent(data).toCloudResult()
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
            ?: throw CloudAuthorizationException("Google Drive did not return an access token.")
        if (grantedScopes.none { it.toString() == DRIVE_APPDATA_SCOPE }) {
            throw CloudAuthorizationException("Google Drive app-data access was not granted.")
        }
        return GoogleDriveAuthorizationResult.Authorized(token)
    }
}

class CloudConsentRequiredException : CloudAuthorizationException(
    "Open Life Tracker to finish connecting Google Drive.",
)
