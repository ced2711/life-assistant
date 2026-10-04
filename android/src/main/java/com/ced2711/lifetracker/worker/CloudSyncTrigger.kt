package com.ced2711.lifetracker.worker

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.room.InvalidationTracker
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps cloud sync close to real time without a push server:
 * - local edits schedule a sync shortly after the last change (debounced);
 * - opening the app syncs at once and then checks the cloud periodically while it stays open;
 * - leaving the app flushes a pending edit sync instead of waiting for it.
 * Background periodic work (every 15 minutes) still covers the time the app is closed.
 */
class CloudSyncTrigger(
    private val scope: CoroutineScope,
    private val automaticSyncEnabled: () -> Boolean,
    private val requestSync: () -> Unit,
    private val onLocalChangeObserved: () -> Unit = {},
    private val editDebounceMillis: Long = EDIT_DEBOUNCE_MILLIS,
    private val foregroundPollMillis: Long = FOREGROUND_POLL_MILLIS,
) : DefaultLifecycleObserver {
    private var pendingEditSync: Job? = null
    private var foregroundPoll: Job? = null

    private val tableObserver = object : InvalidationTracker.Observer(USER_TABLES) {
        override fun onInvalidated(tables: Set<String>) = onLocalChange()
    }

    fun start(database: TaskLedgerDatabase) {
        database.invalidationTracker.addObserver(tableObserver)
    }

    @Synchronized
    fun onLocalChange() {
        onLocalChangeObserved()
        if (!automaticSyncEnabled()) return
        pendingEditSync?.cancel()
        pendingEditSync = scope.launch {
            delay(editDebounceMillis)
            requestIfEnabled()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        synchronized(this) {
            foregroundPoll?.cancel()
            foregroundPoll = scope.launch {
                while (isActive) {
                    requestIfEnabled()
                    delay(foregroundPollMillis)
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        synchronized(this) {
            foregroundPoll?.cancel()
            foregroundPoll = null
            // Upload an edit made just before leaving now; the process may be stopped soon.
            if (pendingEditSync?.isActive == true) {
                pendingEditSync?.cancel()
                pendingEditSync = null
                requestIfEnabled()
            }
        }
    }

    private fun requestIfEnabled() {
        if (automaticSyncEnabled()) requestSync()
    }

    companion object {
        const val EDIT_DEBOUNCE_MILLIS = 8_000L
        const val FOREGROUND_POLL_MILLIS = 2 * 60_000L

        /** Everything that is part of a backup snapshot; restore bookkeeping is excluded. */
        val USER_TABLES = arrayOf(
            "categories", "todo_series", "todo_series_subtasks", "todo_occurrence_exceptions",
            "todos", "subtasks", "todo_reminders", "ledger_series", "ledger_occurrence_exceptions",
            "ledger_entries", "attachments", "note_folders", "notes", "vault_entries", "diary_entries",
            "daily_checklist_items", "daily_checklist_checks",
        )
    }
}
