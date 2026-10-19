package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.data.backup.BackupAttachmentSource
import com.ced2711.lifetracker.data.backup.BackupCrypto
import com.ced2711.lifetracker.data.backup.BackupLimits
import com.ced2711.lifetracker.domain.model.AppLockTimeout
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopNewFeaturesTest {
    private fun password(): CharArray = "desktop-diary-26!".toCharArray()

    private fun <T> withTempDirectory(block: suspend (File) -> T): T = runBlocking {
        val root = Files.createTempDirectory("life-assistant-desktop-features").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun diaryPagesAreSavedPerDayAndBlankTextRemovesThem() = withTempDirectory { root ->
        val store = DesktopDataStore(root.resolve("app"))
        assertTrue(store.open(password()))
        assertTrue(store.upsertDiary(20_000, "First draft"))
        assertTrue(store.upsertDiary(20_000, "Rewritten ✓"))
        assertTrue(store.upsertDiary(20_001, "Next day"))
        assertEquals(2, store.currentSnapshot()!!.diaryEntries.size)
        assertEquals("Rewritten ✓", store.currentSnapshot()!!.diaryEntries.first { it.epochDay == 20_000L }.body)

        assertTrue(store.upsertDiary(20_001, "   "))
        assertEquals(listOf(20_000L), store.currentSnapshot()!!.diaryEntries.map { it.epochDay })
        store.close()

        val reopened = DesktopDataStore(root.resolve("app"))
        assertTrue(reopened.open(password()))
        assertEquals("Rewritten ✓", reopened.currentSnapshot()!!.diaryEntries.single().body)
        assertTrue(reopened.deleteDiary(20_000))
        assertTrue(reopened.currentSnapshot()!!.diaryEntries.isEmpty())
        reopened.close()
    }

    @Test
    fun dataWrittenByTheNotesReleaseCanGainDiaryPages() = withTempDirectory { root ->
        val appDirectory = root.resolve("app").apply { mkdirs() }
        val legacy = DesktopDataStore.defaultSnapshot().copy(formatVersion = BackupLimits.NOTES_SNAPSHOT_VERSION)
        appDirectory.resolve("life-tracker-local.tlb").outputStream().use { output ->
            BackupCrypto.encrypt(legacy, password(), output, BackupAttachmentSource { error("no attachments") })
        }

        val store = DesktopDataStore(appDirectory)
        assertTrue(store.open(password(), createIfMissing = false))
        assertEquals(BackupLimits.NOTES_SNAPSHOT_VERSION, store.currentSnapshot()!!.formatVersion)
        assertTrue(store.upsertDiary(20_000, "Upgraded"))
        assertEquals(BackupLimits.SNAPSHOT_VERSION, store.currentSnapshot()!!.formatVersion)
        store.close()
    }

    @Test
    fun appLockPasswordCheckMatchesOnlyTheOpenPassword() = withTempDirectory { root ->
        val store = DesktopDataStore(root.resolve("app"))
        assertFalse(store.verifyPassword(password()))
        assertTrue(store.open(password()))
        assertTrue(store.verifyPassword(password()))
        assertFalse(store.verifyPassword("desktop-diary-26?".toCharArray()))
        assertFalse(store.verifyPassword("desktop".toCharArray()))
        val typed = password()
        store.verifyPassword(typed)
        assertTrue(typed.all { it == '\u0000' })
        store.close()
    }

    @Test
    fun onlyTheNewestRecoveryCopiesAreKept() = withTempDirectory { root ->
        val directory = root.resolve("cloud-recovery").apply { mkdirs() }
        (1..8).forEach { index ->
            directory.resolve("copy-$index.tlb").apply { writeText("x"); setLastModified(index * 1_000L) }
        }
        directory.resolve("notes.txt").writeText("not a recovery copy")
        DesktopDataStore.pruneRecoveryCopies(directory, keep = 5)
        assertEquals((4..8).map { "copy-$it.tlb" }.toSet(), directory.list()!!.filter { it.endsWith(".tlb") }.toSet())
        assertTrue(directory.resolve("notes.txt").exists())
    }

    @Test
    fun configKeepsHiddenModulesAndAppLockLocally() = withTempDirectory { root ->
        val file = root.resolve("desktop.properties")
        val defaults = DesktopConfigStore(file).read()
        assertEquals(
            TopLevelDestination.entries.toSet() - setOf(TopLevelDestination.DIARY, TopLevelDestination.CONFESSIONAL),
            defaults.visibleDestinations,
        )
        assertFalse(defaults.appLockEnabled)
        assertEquals(AppLockTimeout.ONE_MINUTE, defaults.appLockTimeout)

        DesktopConfigStore(file).setVisibleDestinations(setOf(TopLevelDestination.TODO, TopLevelDestination.CONFESSIONAL))
        DesktopConfigStore(file).setAppLock(true, AppLockTimeout.FIVE_MINUTES)
        val saved = DesktopConfigStore(file).read()
        assertEquals(setOf(TopLevelDestination.TODO, TopLevelDestination.CONFESSIONAL), saved.visibleDestinations)
        assertTrue(saved.appLockEnabled)
        assertEquals(AppLockTimeout.FIVE_MINUTES, saved.appLockTimeout)

        DesktopConfigStore(file).setVisibleDestinations(TopLevelDestination.entries.toSet())
        assertEquals(TopLevelDestination.entries.toSet(), DesktopConfigStore(file).read().visibleDestinations)

        // A choice saved before Today existed does not list it as hidden, so Today shows.
        file.writeText("ui.hiddenDestinations=CONFESSIONAL\n")
        assertEquals(
            TopLevelDestination.entries.toSet() - setOf(TopLevelDestination.CONFESSIONAL),
            DesktopConfigStore(file).read().visibleDestinations,
        )
    }

    @Test
    fun sealedConfessionsStayEncryptedOutsideTheDataFile() = withTempDirectory { root ->
        val file = root.resolve("confessional/sealed.bin")
        // A reversible stand-in for DPAPI keeps the test portable while proving bytes are transformed.
        val protect: (ByteArray) -> ByteArray = { bytes -> bytes.map { (it.toInt() xor 0x5A).toByte() }.toByteArray() }
        fun store() = DesktopConfessionStore(file, protect, protect, clock = { 42L }, ioDispatcher = Dispatchers.Unconfined)

        val first = store()
        first.load()
        first.seal("I never told anyone")
        first.seal("第二件事")
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("never told"))

        val second = store()
        second.load()
        assertEquals(listOf("第二件事", "I never told anyone"), second.entries.value!!.map { it.text })

        second.burn(second.entries.value!!.first().id)
        assertEquals(listOf("I never told anyone"), second.entries.value!!.map { it.text })
        second.burnAll()
        assertFalse(file.exists())
        assertNull(store().entries.value)
    }
}
