package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.local.CategoryEntity
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue
import java.nio.file.Files
import java.io.ByteArrayInputStream

class BackupValidationTest {
    @Test
    fun missingForeignKeyIsRejected() {
        val snapshot = fullBackupSnapshot()
        assertThrows(InvalidBackupException::class.java) {
            snapshot.copy(todos = snapshot.todos.map { it.copy(categoryId = 999) }).validate()
        }
    }

    @Test
    fun duplicateIdsAreRejected() {
        val snapshot = fullBackupSnapshot()
        assertThrows(InvalidBackupException::class.java) {
            snapshot.copy(todos = snapshot.todos + snapshot.todos.single()).validate()
        }
    }

    @Test
    fun categoryCyclesAreRejected() {
        val snapshot = fullBackupSnapshot()
        assertThrows(InvalidBackupException::class.java) {
            snapshot.copy(categories = listOf(CategoryEntity(1, "one", 2), CategoryEntity(2, "two", 1))).validate()
        }
    }

    @Test
    fun categoryValidationAllowsDeepLinearHierarchySupportedByLiveData() {
        val snapshot = fullBackupSnapshot()
        val allowed = (1..1_000).map { id ->
            CategoryEntity(id.toLong(), "category-$id", (id - 1).takeIf { it > 0 }?.toLong())
        }
        snapshot.copy(categories = allowed).validate()
    }

    @Test
    fun invalidRangesAreRejected() {
        val snapshot = fullBackupSnapshot()
        assertThrows(InvalidBackupException::class.java) {
            snapshot.copy(ledgerEntries = snapshot.ledgerEntries.map { it.copy(minuteOfDay = 1_440) }).validate()
        }
        assertThrows(InvalidBackupException::class.java) {
            snapshot.copy(todos = snapshot.todos.map { it.copy(deadlineEpochDay = BackupLimits.MAX_EPOCH_DAY + 1) }).validate()
        }
        assertThrows(InvalidBackupException::class.java) {
            snapshot.copy(todos = snapshot.todos.map { it.copy(updatedAt = it.createdAt - 1) }).validate()
        }
        assertThrows(InvalidBackupException::class.java) {
            snapshot.copy(todos = snapshot.todos.map { it.copy(deadlineEpochDay = BackupLimits.MIN_EPOCH_DAY - 1) }).validate()
        }
        assertThrows(InvalidBackupException::class.java) {
            snapshot.copy(todos = snapshot.todos.map { it.copy(updatedAt = Long.MAX_VALUE) }).validate()
        }
    }

    @Test
    fun fourDigitEditorDateEndpointsAreBackupSafe() {
        val snapshot = fullBackupSnapshot()
        snapshot.copy(
            todos = snapshot.todos.map { it.copy(deadlineEpochDay = BackupLimits.MIN_EPOCH_DAY) },
        ).validate()
        snapshot.copy(
            todos = snapshot.todos.map { it.copy(deadlineEpochDay = BackupLimits.MAX_EPOCH_DAY) },
        ).validate()
    }

    @Test
    fun traversalAndInvalidHashLengthAreRejected() {
        val snapshot = fullBackupSnapshot()
        assertThrows(InvalidBackupException::class.java) {
            snapshot.copy(attachments = snapshot.attachments.map { it.copy(archivePath = "attachments/../secret") }).validate()
        }
        assertThrows(InvalidBackupException::class.java) {
            snapshot.copy(attachments = snapshot.attachments.map { it.copy(sha256 = byteArrayOf(1)) }).validate()
        }
    }

    @Test
    fun emptyAttachmentRestoreStageIsRemovedOnClose() {
        val parent = Files.createTempDirectory("empty-backup-stage").toFile()
        AttachmentRestoreStage.create(parent).use { stage ->
            assertEquals(parent.canonicalFile, requireNotNull(stage.root.parentFile).canonicalFile)
            assertTrue(stage.root.isDirectory)
        }
        assertTrue(parent.listFiles().orEmpty().isEmpty())
        parent.delete()
    }

    @Test
    fun failedAttachmentStageWriteDeletesPartialFileAndCloseDeletesStage() {
        val parent = Files.createTempDirectory("failed-backup-stage").toFile()
        val attachment = fullBackupSnapshot().attachments.single()
        val stage = AttachmentRestoreStage.create(parent)

        assertThrows(InvalidBackupException::class.java) {
            stage.write(attachment, ByteArrayInputStream(fullBackupAttachmentBytes().copyOf(2)))
        }
        assertTrue(stage.root.listFiles().orEmpty().isEmpty())

        stage.close()
        assertTrue(parent.listFiles().orEmpty().isEmpty())
        parent.delete()
    }
}
