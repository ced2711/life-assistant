package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.local.DiaryEntryEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DiaryBackupCompatibilityTest {
    private fun roundTrip(snapshot: BackupSnapshot): BackupSnapshot {
        val bytes = ByteArrayOutputStream().also {
            BackupCodec.write(snapshot, it, fullBackupAttachmentSource())
        }.toByteArray()
        val parent = Files.createTempDirectory("lifeassistant-diary-codec").toFile()
        return AttachmentRestoreStage.create(parent).use { stage ->
            BackupCodec.read(ByteArrayInputStream(bytes), stage)
        }
    }

    private fun notesVersionSnapshot() = fullBackupSnapshot().copy(
        formatVersion = BackupLimits.NOTES_SNAPSHOT_VERSION,
        diaryEntries = emptyList(),
        checklistItems = emptyList(),
        checklistChecks = emptyList(),
    )

    @Test
    fun diaryEntriesRoundTripInTheCurrentFormat() {
        val expected = fullBackupSnapshot()
        assertEquals(BackupLimits.SNAPSHOT_VERSION, expected.formatVersion)
        assertEquals(1, expected.diaryEntries.size)
        assertEquals(expected, roundTrip(expected))
    }

    @Test
    fun notesVersionSnapshotsStillDecodeWithoutDiaryEntries() {
        val legacy = notesVersionSnapshot()
        assertEquals(legacy, roundTrip(legacy))
    }

    @Test
    fun olderFormatsNeverCarryNewModulesAsTheLastDestination() {
        // A version-4 reader would reject an unknown module name, so writers map it to Todo.
        val legacy = notesVersionSnapshot().let {
            it.copy(settings = it.settings.copy(lastDestination = TopLevelDestination.CONFESSIONAL))
        }
        assertEquals(TopLevelDestination.TODO, roundTrip(legacy).settings.lastDestination)

        val current = fullBackupSnapshot().let {
            it.copy(settings = it.settings.copy(lastDestination = TopLevelDestination.DIARY))
        }
        assertEquals(TopLevelDestination.DIARY, roundTrip(current).settings.lastDestination)
    }

    @Test
    fun olderFormatsCannotClaimDiaryEntries() {
        val invalid = fullBackupSnapshot().copy(formatVersion = BackupLimits.NOTES_SNAPSHOT_VERSION)
        assertThrows(InvalidBackupException::class.java) { invalid.validate() }
    }

    @Test
    fun diaryValidationRejectsDuplicateDaysBlankPagesAndBadTimestamps() {
        val base = fullBackupSnapshot()
        val entry = base.diaryEntries.single()

        assertThrows(InvalidBackupException::class.java) {
            base.copy(diaryEntries = listOf(entry, entry.copy(id = entry.id + 1))).validate()
        }
        assertThrows(InvalidBackupException::class.java) {
            base.copy(diaryEntries = listOf(entry.copy(body = "  \n "))).validate()
        }
        assertThrows(InvalidBackupException::class.java) {
            base.copy(diaryEntries = listOf(entry.copy(updatedAt = entry.createdAt - 1))).validate()
        }
        assertThrows(InvalidBackupException::class.java) {
            base.copy(diaryEntries = listOf(entry.copy(epochDay = BackupLimits.MAX_EPOCH_DAY + 1))).validate()
        }
    }

    @Test
    fun previewCountsDiaryEntries() {
        val snapshot = fullBackupSnapshot().copy(
            diaryEntries = listOf(
                DiaryEntryEntity(1, 19_000, "one", 1, 1),
                DiaryEntryEntity(2, 19_001, "two", 1, 2),
            ),
            attachments = fullBackupSnapshot().attachments.filter { it.ownerType != AttachmentOwnerType.NOTE },
        )
        assertEquals(2, buildBackupPreview(snapshot).diaryCount)
    }
}
