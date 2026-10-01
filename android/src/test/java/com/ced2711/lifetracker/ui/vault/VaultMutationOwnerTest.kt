package com.ced2711.lifetracker.ui.vault

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultMutationOwnerTest {
    @Test
    fun onlyOneSaveOrDeleteCanOwnTheMutationSlot() {
        val owner = VaultMutationOwner()
        val save = requireNotNull(owner.begin())

        assertNull(owner.begin())
        assertTrue(owner.isCurrent(save))
        assertTrue(owner.finish(save))

        val delete = requireNotNull(owner.begin())
        assertTrue(delete > save)
        assertTrue(owner.isCurrent(delete))
    }

    @Test
    fun invalidationRejectsStaleCompletionWithoutClearingNewOwner() {
        val owner = VaultMutationOwner()
        val stale = requireNotNull(owner.begin())
        owner.invalidate()
        val current = requireNotNull(owner.begin())

        assertFalse(owner.finish(stale))
        assertTrue(owner.isCurrent(current))
        assertTrue(owner.finish(current))
    }

    @Test
    fun concurrentStartsStillProduceOnlyOneOwner() {
        val owner = VaultMutationOwner()
        val start = CountDownLatch(1)
        val winners = ConcurrentLinkedQueue<Long>()
        val callers = List(32) {
            thread(start = true) {
                start.await()
                owner.begin()?.let(winners::add)
            }
        }

        start.countDown()
        callers.forEach { it.join() }

        assertEquals(1, winners.size)
    }
}
