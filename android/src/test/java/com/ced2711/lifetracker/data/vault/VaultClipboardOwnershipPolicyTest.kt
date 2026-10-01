package com.ced2711.lifetracker.data.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultClipboardOwnershipPolicyTest {
    @Test
    fun `expired owned clip clears only after foreground reconciliation`() {
        val policy = VaultClipboardOwnershipPolicy()
        policy.claim(token = "owned-token", clearAtEpochMillis = 1_030L)

        assertEquals(
            true,
            policy.markDeadlineReached("owned-token"),
        )
        assertEquals(
            OwnedClipboardAction.CLEAR,
            policy.reconcile(currentClipboardToken = "owned-token", nowEpochMillis = 1_030L),
        )
        assertEquals("owned-token", policy.state?.token)
        policy.forget()
        assertEquals(null, policy.state)
    }

    @Test
    fun `later user clip is never cleared and releases ownership`() {
        val policy = VaultClipboardOwnershipPolicy()
        policy.claim(token = "taskledger-token", clearAtEpochMillis = 1_030L)
        policy.markDeadlineReached("taskledger-token")

        assertEquals(
            OwnedClipboardAction.LEAVE_UNCHANGED,
            policy.reconcile(
                currentClipboardToken = "later-user-token",
                nowEpochMillis = 1_030L,
            ),
        )
        assertEquals(null, policy.state)
    }

    @Test
    fun `older deadline cannot expire or release a newer owned clip`() {
        val policy = VaultClipboardOwnershipPolicy()
        policy.claim(token = "older-token", clearAtEpochMillis = 1_030L)
        policy.claim(token = "newer-token", clearAtEpochMillis = 1_040L)

        assertEquals(false, policy.markDeadlineReached("older-token"))
        assertEquals("newer-token", policy.state?.token)
        assertEquals(false, policy.state?.deadlineReached)
    }

    @Test
    fun `wall clock expiry clears after process restoration`() {
        val restored = OwnedClipboardState(
            token = "restored-token",
            clearAtEpochMillis = 2_000L,
            deadlineReached = false,
        )
        val policy = VaultClipboardOwnershipPolicy(restored)

        assertEquals(
            OwnedClipboardAction.CLEAR,
            policy.reconcile(currentClipboardToken = "restored-token", nowEpochMillis = 2_001L),
        )
        assertEquals("restored-token", policy.state?.token)
    }

    @Test
    fun `unexpired restored clip remains available for paste`() {
        val restored = OwnedClipboardState(
            token = "restored-token",
            clearAtEpochMillis = 2_000L,
            deadlineReached = false,
        )
        val policy = VaultClipboardOwnershipPolicy(restored)

        assertEquals(
            OwnedClipboardAction.LEAVE_UNCHANGED,
            policy.reconcile(currentClipboardToken = "restored-token", nowEpochMillis = 1_999L),
        )
        assertEquals("restored-token", policy.state?.token)
    }

    @Test
    fun `explicit expiry clears a matching owned clip when access returns`() {
        val policy = VaultClipboardOwnershipPolicy()
        policy.claim(token = "owned-token", clearAtEpochMillis = 10_000L)
        assertEquals(true, policy.expireOwnedClip())

        assertEquals(
            OwnedClipboardAction.CLEAR,
            policy.reconcile(currentClipboardToken = "owned-token", nowEpochMillis = 1_000L),
        )
    }

    @Test
    fun `explicit expiry never clears a different current clip`() {
        val policy = VaultClipboardOwnershipPolicy()
        policy.claim(token = "owned-token", clearAtEpochMillis = 10_000L)
        policy.expireOwnedClip()

        assertEquals(
            OwnedClipboardAction.LEAVE_UNCHANGED,
            policy.reconcile(currentClipboardToken = null, nowEpochMillis = 1_000L),
        )
        assertEquals(null, policy.state)
    }
}
