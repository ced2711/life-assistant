package com.ced2711.lifetracker.data.cloud

import android.content.Context
import com.ced2711.lifetracker.cloudsync.LocalCloudSyncState
import java.util.UUID

data class AndroidCloudSyncSettings(
    val enabled: Boolean,
    val automaticSync: Boolean,
    val attention: CloudSyncAttention?,
    val state: LocalCloudSyncState,
)

enum class CloudSyncAttention {
    CONFLICT,
    VAULT_UNLOCK,
    GOOGLE_CONSENT,
    FAILED,
}

class CloudSyncPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

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
        )
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
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
            .apply()
    }

    @Synchronized
    fun setAttention(attention: CloudSyncAttention?) {
        preferences.edit().apply {
            if (attention == null) remove(KEY_ATTENTION) else putString(KEY_ATTENTION, attention.name)
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
    }
}
