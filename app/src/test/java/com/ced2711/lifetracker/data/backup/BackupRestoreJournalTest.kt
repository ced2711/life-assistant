package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupRestoreJournalTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `journal round trips restore token and all settings`() {
        val fixture = fixture()
        val newSettings = AppSettings(
            accentColor = AccentColor.ORANGE,
            notificationsEnabled = true,
            defaultAllDayReminderMinute = 777,
            defaultReminderOffsetsMinutes = setOf(0L, 60L, 1_440L),
            todoQuickAddFields = TodoQuickAddField.entries.toSet(),
        )
        val prepared = fixture.record.copy(newSettings = newSettings)

        fixture.journal.write(prepared)
        assertEquals(prepared, fixture.journal.read())
        fixture.journal.clear()
        assertNull(fixture.journal.read())
    }

    @Test
    fun `version two journal defaults missing accent colors to teal`() {
        val fixture = fixture()
        writeVersionTwoJournal(fixture.journalFile, fixture.record)

        val actual = fixture.journal.read()

        assertEquals(AccentColor.TEAL, actual?.oldSettings?.accentColor)
        assertEquals(AccentColor.TEAL, actual?.newSettings?.accentColor)
    }

    @Test
    fun `journal rejects a noncanonical restore token`() {
        val fixture = fixture()

        assertThrows(InvalidBackupException::class.java) {
            fixture.journal.write(fixture.record.copy(restoreToken = "not-a-uuid"))
        }
    }

    @Test
    fun `journal rejects a malformed persisted restore token`() {
        val fixture = fixture()
        fixture.journal.write(fixture.record)
        val bytes = fixture.journalFile.readBytes()
        bytes[12] = 'z'.code.toByte() // magic + version + token byte length
        fixture.journalFile.writeBytes(bytes)

        assertThrows(InvalidBackupException::class.java) { fixture.journal.read() }
    }

    @Test
    fun `journal rejects stage and old paths outside private attachment root`() {
        val fixture = fixture()
        val outside = temporaryFolder.newFile("outside.bin")

        assertThrows(InvalidBackupException::class.java) {
            fixture.journal.write(fixture.record.copy(stageRoot = outside.absolutePath))
        }
        assertThrows(InvalidBackupException::class.java) {
            fixture.journal.write(fixture.record.copy(oldAttachmentPaths = listOf(outside.absolutePath)))
        }
    }

    @Test
    fun `truncated journal is rejected rather than guessed`() {
        val fixture = fixture()
        fixture.journal.write(fixture.record)
        fixture.journalFile.writeBytes(fixture.journalFile.readBytes().copyOf(7))

        assertThrows(InvalidBackupException::class.java) { fixture.journal.read() }
    }

    @Test
    fun `startup recovery quarantines truncated journal without touching attachment paths`() {
        val fixture = fixture()
        fixture.journal.write(fixture.record)
        fixture.journalFile.writeBytes(fixture.journalFile.readBytes().copyOf(7))
        val stage = File(fixture.record.stageRoot)
        val oldAttachment = File(fixture.record.oldAttachmentPaths.single())

        assertEquals(RestoreJournalReadResult.Quarantined, fixture.journal.readForRecovery())

        assertFalse(fixture.journalFile.exists())
        assertTrue(File(fixture.journalFile.parentFile, "taskledger-restore.journal.quarantined").isFile)
        assertTrue(stage.isDirectory)
        assertTrue(oldAttachment.isFile)
        assertEquals(RestoreJournalReadResult.None, fixture.journal.readForRecovery())
    }

    @Test
    fun `startup recovery reads the exact version one journal layout`() {
        val fixture = fixture()
        val fingerprint = "ab".repeat(32)
        writeLegacyJournal(
            fixture.journalFile,
            phase = LegacyRestoreJournalPhase.ROOM_COMMITTED,
            fingerprint = fingerprint,
            oldSettings = fixture.record.oldSettings,
            newSettings = fixture.record.newSettings,
            stageRoot = fixture.record.stageRoot,
            oldPaths = fixture.record.oldAttachmentPaths,
        )

        val result = fixture.journal.readForRecovery() as RestoreJournalReadResult.Legacy

        assertEquals(LegacyRestoreJournalPhase.ROOM_COMMITTED, result.record.phase)
        assertEquals(fingerprint, result.record.expectedDatabaseFingerprint)
        assertEquals(fixture.record.oldSettings, result.record.oldSettings)
        assertEquals(fixture.record.newSettings, result.record.newSettings)
        assertEquals(fixture.record.stageRoot, result.record.stageRoot)
        assertEquals(fixture.record.oldAttachmentPaths, result.record.oldAttachmentPaths)
    }

    @Test
    fun `orphan restore directories are removed but referenced stage is retained`() {
        val attachments = temporaryFolder.newFolder("cleanup-attachments")
        val referenced = File(attachments, "taskledger-restore-referenced").apply { mkdir() }
        val referencedFile = File(referenced, "1.bin").apply { writeText("keep") }
        val orphan = File(attachments, "taskledger-restore-orphan").apply { mkdir() }
        File(orphan, "2.bin").writeText("remove")

        cleanupUnreferencedRestoreDirectories(attachments, setOf(referencedFile.absolutePath))

        assertTrue(referenced.isDirectory)
        assertTrue(referencedFile.isFile)
        assertFalse(orphan.exists())
    }

    private fun fixture(): Fixture {
        val files = temporaryFolder.newFolder("files-${System.nanoTime()}")
        val attachments = File(files, "attachments").apply { mkdir() }
        val stage = File(attachments, "taskledger-restore-test").apply { mkdir() }
        val old = File(attachments, "old.bin").apply { writeText("old") }
        val journalFile = File(files, "taskledger-restore.journal")
        val journal = BackupRestoreJournal(journalFile, files, attachments)
        val record = RestoreJournalRecord(
            restoreToken = "123e4567-e89b-12d3-a456-426614174000",
            oldSettings = AppSettings(),
            newSettings = AppSettings(notificationsEnabled = true),
            stageRoot = stage.absolutePath,
            oldAttachmentPaths = listOf(old.absolutePath),
        )
        return Fixture(journal, journalFile, record)
    }

    private fun writeLegacyJournal(
        destination: File,
        phase: LegacyRestoreJournalPhase,
        fingerprint: String,
        oldSettings: AppSettings,
        newSettings: AppSettings,
        stageRoot: String,
        oldPaths: List<String>,
    ) {
        DataOutputStream(destination.outputStream().buffered()).use { output ->
            output.writeInt(0x544C524A)
            output.writeInt(1)
            output.writeText(phase.name)
            output.writeText(fingerprint)
            output.writeSettings(oldSettings)
            output.writeSettings(newSettings)
            output.writeText(stageRoot)
            output.writeInt(oldPaths.size)
            oldPaths.forEach { output.writeText(it) }
        }
    }

    private fun writeVersionTwoJournal(
        destination: File,
        record: RestoreJournalRecord,
    ) {
        DataOutputStream(destination.outputStream().buffered()).use { output ->
            output.writeInt(0x544C524A)
            output.writeInt(2)
            output.writeText(record.restoreToken)
            output.writeSettings(record.oldSettings)
            output.writeSettings(record.newSettings)
            output.writeText(record.stageRoot)
            output.writeInt(record.oldAttachmentPaths.size)
            record.oldAttachmentPaths.forEach { output.writeText(it) }
        }
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.encodeToByteArray()
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeSettings(value: AppSettings) {
        writeText(value.themeMode.name)
        writeText(value.weekStart.name)
        writeText(value.timeFormat.name)
        writeText(value.dateFormat.name)
        writeBoolean(value.notificationsEnabled)
        writeInt(value.defaultAllDayReminderMinute)
        writeInt(value.defaultReminderOffsetsMinutes.size)
        value.defaultReminderOffsetsMinutes.sorted().forEach(::writeLong)
        val quickFields = TodoQuickAddField.entries.filter(value.todoQuickAddFields::contains)
        writeInt(quickFields.size)
        quickFields.forEach { writeText(it.name) }
        writeText(value.lastDestination.name)
    }

    private data class Fixture(
        val journal: BackupRestoreJournal,
        val journalFile: File,
        val record: RestoreJournalRecord,
    )
}
