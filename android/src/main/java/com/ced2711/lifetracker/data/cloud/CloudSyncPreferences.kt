package com.ced2711.lifetracker.data.cloud

import android.content.Context
import com.ced2711.lifetracker.cloudsync.LocalCloudSyncState
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class AndroidCloudSyncSettings(
    val enabled: Boolean,
    val automaticSync: Boolean,
    val attention: CloudSyncAttention?,
    val state: LocalCloudSyncState,
    val provider: CloudProvider = CloudProvider.GOOGLE_DRIVE,
    val gitHubClientId: String = "",
    // owner/name of the private repository used when [provider] is GitHub.
    val gitHubRepository: String = "",
    /** Why the last sync failed, in English, so the screen can say more than "failed". */
    val lastError: String? = null,
)

enum class CloudProvider {
    GOOGLE_DRIVE,
    GITHUB,
}

enum class CloudSyncAttention {
    CONFLICT,
    VAULT_UNLOCK,
    GOOGLE_CONSENT,
    GITHUB_SIGN_IN,
    FAILED,
}

class CloudSyncPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Emits once at start and after every change, from any writer (app, worker, sync button). */
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    @Synchronized
    fun read(): AndroidCloudSyncSettings {
        val deviceId = preferences.getString(KEY_DEVICE_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString().also {
                preferences.edit().putString(KEY_DEVICE_ID, it).apply()
            }
        return AndroidCloudSyncSettings(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            automaticSync = preferences.getBoolean(KEY_AUTOMATIC, true),
            attention = preferences.getString(KEY_ATTENTION, null)?.let { saved ->
                CloudSyncAttention.entries.firstOrNull { it.name == saved }
            },
            state = LocalCloudSyncState(
                deviceId = deviceId,
                lastRevisionId = preferences.getString(KEY_LAST_REVISION, null),
                lastContentFingerprint = preferences.getString(KEY_LAST_FINGERPRINT, null),
                lastSyncAt = preferences.getLong(KEY_LAST_SYNC_AT, -1L).takeIf { it >= 0L },
            ),
            provider = preferences.getString(KEY_PROVIDER, null)
                ?.let { saved -> CloudProvider.entries.firstOrNull { it.name == saved } }
                ?: CloudProvider.GOOGLE_DRIVE,
            gitHubClientId = preferences.getString(KEY_GITHUB_CLIENT_ID, null).orEmpty(),
            gitHubRepository = preferences.getString(KEY_GITHUB_REPOSITORY, null).orEmpty(),
            lastError = preferences.getString(KEY_LAST_ERROR, null),
        )
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    @Synchronized
    fun setProvider(provider: CloudProvider, gitHubClientId: String = "", gitHubRepository: String = "") {
        check(
            preferences.edit()
                .putString(KEY_PROVIDER, provider.name)
                .putString(KEY_GITHUB_CLIENT_ID, gitHubClientId)
                .putString(KEY_GITHUB_REPOSITORY, gitHubRepository)
                .commit(),
        ) { "Could not save the cloud provider." }
    }

    @Synchronized
    fun setAutomaticSync(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOMATIC, enabled).apply()
    }

    @Synchronized
    fun recordSuccessfulSync(revisionId: String, fingerprint: String, syncedAt: Long) {
        preferences.edit()
            .putString(KEY_LAST_REVISION, revisionId)
            .putString(KEY_LAST_FINGERPRINT, fingerprint)
            .putLong(KEY_LAST_SYNC_AT, syncedAt)
            .remove(KEY_ATTENTION)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    @Synchronized
    fun setAttention(attention: CloudSyncAttention?, error: String? = null) {
        preferences.edit().apply {
            if (attention == null) remove(KEY_ATTENTION) else putString(KEY_ATTENTION, attention.name)
            if (error == null) remove(KEY_LAST_ERROR) else putString(KEY_LAST_ERROR, error)
        }.apply()
    }

    /** A newly saved password or renewed account grant must establish a fresh cloud lineage. */
    @Synchronized
    fun resetSyncLineage() {
        check(
            preferences.edit()
                .remove(KEY_LAST_REVISION)
                .remove(KEY_LAST_FINGERPRINT)
                .remove(KEY_LAST_SYNC_AT)
                .remove(KEY_ATTENTION)
                .remove(KEY_LAST_ERROR)
                .commit(),
        ) { "Could not reset cloud sync lineage." }
    }

    @Synchronized
    fun clearConnection() {
        preferences.edit()
            .putBoolean(KEY_ENABLED, false)
            .remove(KEY_LAST_REVISION)
            .remove(KEY_LAST_FINGERPRINT)
            .remove(KEY_LAST_SYNC_AT)
            .remove(KEY_ATTENTION)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "life_tracker_cloud_sync"
        const val KEY_ENABLED = "enabled"
        const val KEY_AUTOMATIC = "automatic"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LAST_REVISION = "last_revision"
        const val KEY_LAST_FINGERPRINT = "last_fingerprint"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_ATTENTION = "attention"
        const val KEY_PROVIDER = "provider"
        const val KEY_GITHUB_CLIENT_ID = "github_client_id"
        const val KEY_GITHUB_REPOSITORY = "github_repository"
        const val KEY_LAST_ERROR = "last_error"
    }
}
