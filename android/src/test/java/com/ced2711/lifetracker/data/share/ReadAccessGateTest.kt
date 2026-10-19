package com.ced2711.lifetracker.data.share

import org.junit.Assert.assertThrows
import org.junit.Test

class ReadAccessGateTest {
    @Test
    fun `only Pocket AI may call`() {
        ReadAccessGate.checkCaller("com.ced2711.pocketai")
        listOf(null, "", "com.ced2711.pocketai.e2e", "com.ced2711.lifetracker", "com.example.other").forEach { caller ->
            assertThrows(SecurityException::class.java) { ReadAccessGate.checkCaller(caller) }
        }
    }

    @Test
    fun `reading needs the Settings switch`() {
        ReadAccessGate.checkEnabled(true)
        assertThrows(SecurityException::class.java) { ReadAccessGate.checkEnabled(false) }
    }
}
