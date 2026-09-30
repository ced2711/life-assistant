package com.ced2711.lifetracker.worker

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncTriggerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = AtomicInteger()
    private var enabled = true
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = error("not used by the trigger")
    }

    private fun trigger(poll: Long = 60_000) = CloudSyncTrigger(
        scope = scope,
        automaticSyncEnabled = { enabled },
        requestSync = { requests.incrementAndGet() },
        editDebounceMillis = 150,
        foregroundPollMillis = poll,
    )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun aBurstOfEditsRequestsOneSyncAfterTheQuietPeriod() {
        val trigger = trigger()
        repeat(5) { trigger.onLocalChange(); Thread.sleep(30) }
        assertEquals(0, requests.get())
        Thread.sleep(400)
        assertEquals(1, requests.get())
    }

    @Test
    fun leavingTheAppFlushesAPendingEditSyncImmediately() {
        val trigger = trigger()
        trigger.onLocalChange()
        trigger.onStop(owner)
        assertEquals(1, requests.get())
        Thread.sleep(400)
        assertEquals("the debounced request must not fire a second time", 1, requests.get())
    }

    @Test
    fun openingTheAppSyncsAtOnceAndThenPollsUntilItIsLeft() {
        val trigger = trigger(poll = 200)
        trigger.onStart(owner)
        Thread.sleep(100)
        assertEquals(1, requests.get())
        Thread.sleep(450)
        val whileOpen = requests.get()
        assertEquals(true, whileOpen >= 3)
        trigger.onStop(owner)
        Thread.sleep(450)
        assertEquals(whileOpen, requests.get())
    }

    @Test
    fun nothingIsRequestedWhenAutomaticSyncIsOff() {
        enabled = false
        val trigger = trigger(poll = 100)
        trigger.onLocalChange()
        trigger.onStart(owner)
        Thread.sleep(400)
        trigger.onStop(owner)
        assertEquals(0, requests.get())
    }
}
