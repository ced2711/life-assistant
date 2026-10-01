package com.ced2711.lifetracker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface StartupRecoveryState {
    data object Recovering : StartupRecoveryState
    data object Ready : StartupRecoveryState
    data class Failed(val cause: Throwable) : StartupRecoveryState
}

/**
 * Resolves a pending restore once per process before any normal database reader or writer runs.
 * A failed attempt remains closed until the user explicitly retries it.
 */
class StartupRecoveryCoordinator internal constructor(
    private val scope: CoroutineScope,
    private val recoverPendingRestore: suspend () -> Boolean,
    private val onReady: suspend (recoveredRestore: Boolean) -> Unit = {},
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow<StartupRecoveryState>(
        StartupRecoveryState.Recovering,
    )
    val state: StateFlow<StartupRecoveryState> = mutableState.asStateFlow()

    private var started = false
    private var attemptRunning = false

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
            launchAttemptLocked()
        }
    }

    fun retry() {
        synchronized(lock) {
            if (mutableState.value !is StartupRecoveryState.Failed || attemptRunning) return
            launchAttemptLocked()
        }
    }

    /** Waits for the active attempt and returns false without touching app data after a failure. */
    suspend fun awaitReady(): Boolean =
        state.first { it !is StartupRecoveryState.Recovering } is StartupRecoveryState.Ready

    private fun launchAttemptLocked() {
        check(!attemptRunning)
        attemptRunning = true
        mutableState.value = StartupRecoveryState.Recovering
        scope.launch {
            val recovery = try {
                Result.success(recoverPendingRestore())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                Result.failure(failure)
            }

            val recoveredRestore = recovery.getOrNull()
            synchronized(lock) {
                attemptRunning = false
                mutableState.value = recovery.fold(
                    onSuccess = { StartupRecoveryState.Ready },
                    onFailure = { StartupRecoveryState.Failed(it) },
                )
            }
            if (recoveredRestore != null) {
                // Recovery is already resolved. Ancillary refresh failures must not close the gate.
                runCatching { onReady(recoveredRestore) }
            }
        }
    }
}
