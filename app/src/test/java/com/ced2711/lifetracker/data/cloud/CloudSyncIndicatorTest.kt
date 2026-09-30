package com.ced2711.lifetracker.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudSyncIndicatorTest {
    private fun state(
        connected: Boolean = true,
        running: Boolean = false,
        needsAttention: Boolean = false,
        lastSyncAt: Long? = 1_000,
        lastLocalChangeAt: Long? = null,
    ) = cloudSyncIndicator(connected, running, needsAttention, lastSyncAt, lastLocalChangeAt)?.state

    @Test
    fun hiddenWhenCloudSyncIsNotConnected() {
        assertNull(state(connected = false, running = true))
    }

    @Test
    fun upToDateAfterASyncWithNoLaterEdits() {
        assertEquals(CloudSyncIndicatorState.UP_TO_DATE, state())
        assertEquals(CloudSyncIndicatorState.UP_TO_DATE, state(lastLocalChangeAt = 900))
    }

    @Test
    fun anEditAfterTheLastSyncIsPending() {
        assertEquals(CloudSyncIndicatorState.PENDING, state(lastLocalChangeAt = 1_001))
        assertEquals(CloudSyncIndicatorState.PENDING, state(lastSyncAt = null))
    }

    @Test
    fun aRunningSyncWinsAndAttentionOutranksPending() {
        assertEquals(CloudSyncIndicatorState.SYNCING, state(running = true, needsAttention = true))
        assertEquals(CloudSyncIndicatorState.NEEDS_ATTENTION, state(needsAttention = true, lastLocalChangeAt = 2_000))
    }
}
