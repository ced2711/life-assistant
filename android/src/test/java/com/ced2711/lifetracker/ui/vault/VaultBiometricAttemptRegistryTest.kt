package com.ced2711.lifetracker.ui.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultBiometricAttemptRegistryTest {
    @Test
    fun delayedOldCallbackCannotClearNewAttempt() {
        val registry = VaultBiometricAttemptRegistry()
        val oldAttempt = registry.start(requestId = 11L)
        val newAttempt = registry.start(requestId = 22L)

        assertFalse(registry.finish(oldAttempt))
        assertEquals(newAttempt, registry.active)
        assertTrue(registry.finish(newAttempt))
        assertNull(registry.active)
    }

    @Test
    fun generationProtectsReusedRequestIdAndInvalidatedAttempt() {
        val registry = VaultBiometricAttemptRegistry()
        val invalidated = registry.start(requestId = 91L)
        registry.invalidate()
        val replacement = registry.start(requestId = 91L)

        assertFalse(registry.finish(invalidated))
        assertEquals(replacement, registry.active)
        assertTrue(replacement.generation > invalidated.generation)
    }
}
