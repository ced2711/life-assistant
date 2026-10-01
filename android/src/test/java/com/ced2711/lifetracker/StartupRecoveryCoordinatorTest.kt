package com.ced2711.lifetracker

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupRecoveryCoordinatorTest {
    @Test
    fun repeatedStartRunsOneRecoveryAttempt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val coordinator = StartupRecoveryCoordinator(
            scope = scope,
            recoverPendingRestore = {
                calls.incrementAndGet()
                entered.complete(Unit)
                release.await()
                false
            },
        )

        coordinator.start()
        coordinator.start()
        withTimeout(2_000) { entered.await() }
        assertEquals(1, calls.get())
        assertTrue(coordinator.state.value is StartupRecoveryState.Recovering)

        release.complete(Unit)
        assertTrue(withTimeout(2_000) { coordinator.awaitReady() })
        assertEquals(1, calls.get())
        scope.cancel()
    }

    @Test
    fun failureKeepsGateClosedUntilSuccessfulRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = AtomicInteger()
        val readyCallback = CompletableDeferred<Boolean>()
        val coordinator = StartupRecoveryCoordinator(
            scope = scope,
            recoverPendingRestore = {
                if (calls.incrementAndGet() == 1) error("journal unavailable")
                true
            },
            onReady = { recovered ->
                readyCallback.complete(recovered)
                Unit
            },
        )

        coordinator.start()
        val failure = withTimeout(2_000) {
            coordinator.state.first { it is StartupRecoveryState.Failed }
        }
        assertTrue(failure is StartupRecoveryState.Failed)
        assertFalse(coordinator.awaitReady())

        coordinator.retry()
        assertTrue(withTimeout(2_000) { coordinator.awaitReady() })
        assertTrue(withTimeout(2_000) { readyCallback.await() })
        assertEquals(2, calls.get())
        scope.cancel()
    }

    @Test
    fun retryDuringActiveAttemptDoesNotStartAnotherRecovery() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val coordinator = StartupRecoveryCoordinator(
            scope = scope,
            recoverPendingRestore = {
                calls.incrementAndGet()
                release.await()
                false
            },
        )

        coordinator.start()
        coordinator.retry()
        release.complete(Unit)

        assertTrue(withTimeout(2_000) { coordinator.awaitReady() })
        assertEquals(1, calls.get())
        scope.cancel()
    }

    @Test
    fun readyCallbackRunsWhenThereWasNoPendingRestore() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val readyCallback = CompletableDeferred<Boolean>()
        val coordinator = StartupRecoveryCoordinator(
            scope = scope,
            recoverPendingRestore = { false },
            onReady = { recovered ->
                readyCallback.complete(recovered)
                Unit
            },
        )

        coordinator.start()

        assertTrue(withTimeout(2_000) { coordinator.awaitReady() })
        assertFalse(withTimeout(2_000) { readyCallback.await() })
        scope.cancel()
    }
}
