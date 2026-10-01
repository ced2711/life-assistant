package com.ced2711.lifetracker.ui.vault

import com.ced2711.lifetracker.data.vault.VaultSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultBackupSessionLeaseOwnerTest {
    @Test
    fun leaseCanBeAcquiredOnlyOnceAndClosesIndependently() {
        VaultSession(ByteArray(32) { it.toByte() }).use { source ->
            val owner = VaultBackupSessionLeaseOwner(initialGeneration = 10L)
            val generation = owner.beginFreshAuthentication()
            assertTrue(owner.markAuthenticated(generation, source))

            val lease = owner.acquire(source)

            assertNotNull(lease)
            assertNull(owner.acquire(source))
            lease!!.close()
            assertTrue(source.useKey { it.encoded.isNotEmpty() })
        }
    }

    @Test
    fun newFreshAuthenticationInvalidatesOldGenerationAndSessionEligibility() {
        VaultSession(ByteArray(32) { 1 }).use { oldSession ->
            VaultSession(ByteArray(32) { 2 }).use { newSession ->
                val owner = VaultBackupSessionLeaseOwner(initialGeneration = 20L)
                val oldGeneration = owner.beginFreshAuthentication()
                assertTrue(owner.markAuthenticated(oldGeneration, oldSession))

                val newGeneration = owner.beginFreshAuthentication()

                assertNull(owner.acquire(oldSession))
                assertFalse(owner.markAuthenticated(oldGeneration, oldSession))
                assertTrue(owner.markAuthenticated(newGeneration, newSession))
                owner.acquire(newSession)!!.close()
            }
        }
    }
}
