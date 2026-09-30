package com.ced2711.lifetracker.data.cloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class CloudSyncIndicatorState { SYNCING, NEEDS_ATTENTION, PENDING, UP_TO_DATE }

data class CloudSyncIndicator(val state: CloudSyncIndicatorState, val lastSyncAt: Long?)

/** Null hides the indicator: sync is not connected. */
fun cloudSyncIndicator(
    connected: Boolean,
    running: Boolean,
    needsAttention: Boolean,
    lastSyncAt: Long?,
    lastLocalChangeAt: Long?,
): CloudSyncIndicator? {
    if (!connected) return null
    val state = when {
        running -> CloudSyncIndicatorState.SYNCING
        needsAttention -> CloudSyncIndicatorState.NEEDS_ATTENTION
        lastSyncAt == null -> CloudSyncIndicatorState.PENDING
        lastLocalChangeAt != null && lastLocalChangeAt > lastSyncAt -> CloudSyncIndicatorState.PENDING
        else -> CloudSyncIndicatorState.UP_TO_DATE
    }
    return CloudSyncIndicator(state, lastSyncAt)
}

/** App-wide sync status for the top-bar indicator, including syncs the background worker runs. */
class CloudSyncStatusMonitor(
    private val engine: AndroidCloudSyncEngine,
    private val preferences: CloudSyncPreferences,
    // Same rule as the Backup screen: enabled and the sync password is still stored.
    private val hasSyncPassword: () -> Boolean,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastLocalChangeAt = MutableStateFlow<Long?>(null)
    private var seenFinishedSyncs = engine.finishedSyncs.value
    @Volatile private var lastSyncEndedAt = 0L

    /** Database writes made by a restore arrive during or just after the sync; they are not local edits. */
    fun markLocalChange() {
        val now = clock()
        if (engine.running.value || now - lastSyncEndedAt < RESTORE_WRITE_GRACE_MILLIS) return
        lastLocalChangeAt.value = now
    }

    val indicator: StateFlow<CloudSyncIndicator?> = combine(
        engine.running,
        engine.finishedSyncs,
        lastLocalChangeAt,
        preferences.changes(),
    ) { running, finished, changedAt, _ ->
        if (finished != seenFinishedSyncs) {
            seenFinishedSyncs = finished
            lastSyncEndedAt = clock()
        }
        val settings = preferences.read()
        cloudSyncIndicator(
            connected = settings.enabled && hasSyncPassword(),
            running = running,
            needsAttention = settings.attention != null,
            lastSyncAt = settings.state.lastSyncAt,
            lastLocalChangeAt = changedAt,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private companion object {
        const val RESTORE_WRITE_GRACE_MILLIS = 3_000L
    }
}
