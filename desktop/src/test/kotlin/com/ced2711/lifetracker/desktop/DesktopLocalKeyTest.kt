package com.ced2711.lifetracker.desktop

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLocalKeyTest {
    private val root: File = Files.createTempDirectory("life-assistant-local-key").toFile()
    private val data = root.resolve("data")
    private val credentials = DesktopCredentialStore(root.resolve("credentials"), XorProtection)
    private val config = DesktopConfigStore(root.resolve("desktop.properties"))

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun aNewPcOpensWithoutAskingAndKeepsItsOwnPassword() = runBlocking {
        DesktopDataStore(data).use { store ->
            assertTrue(DesktopLocalKey.openWithoutAsking(store, credentials, config))
            assertTrue(store.addCategory("First"))
        }
        assertFalse(config.passwordChosen())
        DesktopDataStore(data).use { store ->
            assertTrue(DesktopLocalKey.openWithoutAsking(store, credentials, config))
            assertEquals("First", store.currentSnapshot()!!.categories.single().name)
        }
    }

    @Test
    fun existingDataWithoutARememberedPasswordStillAsks() = runBlocking {
        DesktopDataStore(data).use { store -> assertTrue(store.open("chosen-password".toCharArray())) }
        DesktopDataStore(data).use { store ->
            assertFalse(DesktopLocalKey.openWithoutAsking(store, credentials, config))
        }
        // Installs from before this change chose their password themselves.
        assertTrue(config.passwordChosen())
    }

    @Test
    fun choosingAPasswordReencryptsDataAndDailyCopies() = runBlocking {
        val today = LocalDate.of(2026, 10, 2)
        DesktopDataStore(data).use { store ->
            assertTrue(DesktopLocalKey.openWithoutAsking(store, credentials, config))
            assertTrue(store.addCategory("Before"))
            assertTrue(store.backUpDaily(today))
            assertTrue(DesktopLocalKey.choose("shared-password".toCharArray(), store, credentials, config))
            assertTrue(store.verifyPassword("shared-password".toCharArray()))
            assertTrue(store.addCategory("After"))
            val backup = store.dailyBackups().single()
            assertEquals(LocalDate.of(2026, 10, 1), backup.day)
            assertTrue(store.restoreDailyBackup(backup) is DesktopReplaceResult.Applied)
            assertEquals(listOf("Before"), store.currentSnapshot()!!.categories.map { it.name })
            assertTrue(store.beforeRestoreFile.isFile)
        }
        assertTrue(config.passwordChosen())
        DesktopDataStore(data).use { store ->
            assertFalse(store.open("wrong-password".toCharArray()))
        }
        DesktopDataStore(data).use { store ->
            assertTrue(store.open("shared-password".toCharArray()))
        }
    }

    @Test
    fun dailyCopiesKeepYesterdayAndTheDayBefore() = runBlocking {
        DesktopDataStore(data).use { store ->
            assertTrue(store.open("daily-password".toCharArray()))
            assertTrue(store.backUpDaily(LocalDate.of(2026, 10, 1)))
            assertFalse("one copy per day", store.backUpDaily(LocalDate.of(2026, 10, 1)))
            assertTrue(store.backUpDaily(LocalDate.of(2026, 10, 2)))
            assertEquals(
                listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 30)),
                store.dailyBackups().map { it.day },
            )
            // On its third day the copy of Sep 30 goes.
            assertTrue(store.backUpDaily(LocalDate.of(2026, 10, 3)))
            assertEquals(
                listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)),
                store.dailyBackups().map { it.day },
            )
        }
    }

    /** Stands in for DPAPI or the Linux keyring. */
    private object XorProtection : DeviceProtection {
        override fun protect(bytes: ByteArray) = ByteArray(bytes.size) { (bytes[it].toInt() xor 0x5A).toByte() }
        override fun unprotect(bytes: ByteArray) = protect(bytes)
    }
}
