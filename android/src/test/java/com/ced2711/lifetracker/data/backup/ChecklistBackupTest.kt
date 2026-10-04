package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.local.ChecklistCheckEntity
import com.ced2711.lifetracker.data.local.ChecklistItemEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The daily checklist travels in backups and merges between devices like the other modules. */
class ChecklistBackupTest {
    private val empty = fullBackupSnapshot().copy(
        categories = emptyList(), todoSeries = emptyList(), todoSeriesSubtasks = emptyList(),
        todoOccurrenceExceptions = emptyList(), todos = emptyList(), subtasks = emptyList(),
        todoReminders = emptyList(), ledgerSeries = emptyList(), ledgerOccurrenceExceptions = emptyList(),
        ledgerEntries = emptyList(), attachments = emptyList(), vaultEntries = emptyList(),
        noteFolders = emptyList(), notes = emptyList(), diaryEntries = emptyList(),
        checklistItems = emptyList(), checklistChecks = emptyList(),
    )
    private val today = 20_000L

    private fun item(id: Long, title: String, created: Long, order: Long = 0, updated: Long = created) =
        ChecklistItemEntity(id = id, title = title, sortOrder = order, createdAt = created, updatedAt = updated)

    private fun merge(base: BackupSnapshot?, local: BackupSnapshot, remote: BackupSnapshot) =
        mergeSnapshots(base, local, remote, now = 50_000).snapshot

    private fun roundTrip(snapshot: BackupSnapshot): BackupSnapshot {
        val bytes = ByteArrayOutputStream().also { BackupCodec.write(snapshot, it, fullBackupAttachmentSource()) }.toByteArray()
        val parent = Files.createTempDirectory("lifeassistant-checklist-codec").toFile()
        return AttachmentRestoreStage.create(parent).use { stage -> BackupCodec.read(ByteArrayInputStream(bytes), stage) }
    }

    @Test
    fun theChecklistRoundTripsInTheCurrentFormat() {
        val expected = fullBackupSnapshot()
        assertEquals(2, expected.checklistItems.size)
        assertEquals(expected, roundTrip(expected))
    }

    @Test
    fun diaryVersionSnapshotsStillDecodeWithoutAChecklist() {
        val legacy = fullBackupSnapshot().copy(
            formatVersion = BackupLimits.DIARY_SNAPSHOT_VERSION,
            checklistItems = emptyList(),
            checklistChecks = emptyList(),
        )
        assertEquals(legacy, roundTrip(legacy))
    }

    @Test
    fun olderFormatsCannotClaimAChecklist() {
        val invalid = fullBackupSnapshot().copy(formatVersion = BackupLimits.DIARY_SNAPSHOT_VERSION)
        assertThrows(InvalidBackupException::class.java) { invalid.validate() }
    }

    @Test
    fun aTickWithoutItsItemIsRejected() {
        val invalid = empty.copy(checklistChecks = listOf(ChecklistCheckEntity(itemId = 5, epochDay = today, checkedAt = 1_000)))
        assertThrows(InvalidBackupException::class.java) { invalid.validate() }
    }

    @Test
    fun theSameItemAddedOnBothDevicesIsOneItemAndTicksOnEitherCount() {
        val local = empty.copy(
            checklistItems = listOf(item(1, "Brush teeth", created = 2_000)),
            checklistChecks = listOf(ChecklistCheckEntity(1, today, 2_100)),
        )
        val remote = empty.copy(
            checklistItems = listOf(item(1, "Shower", created = 3_000), item(2, "brush teeth ", created = 3_100, order = 1)),
            checklistChecks = listOf(ChecklistCheckEntity(2, today, 3_200), ChecklistCheckEntity(1, today, 3_300)),
        )
        val merged = merge(empty, local, remote)
        assertEquals(2, merged.checklistItems.size)
        val byTitle = merged.checklistItems.associateBy { it.title.trim().lowercase() }
        assertEquals(setOf("brush teeth", "shower"), byTitle.keys)
        val ticked = merged.checklistChecks.filter { it.epochDay == today }.map { it.itemId }.toSet()
        assertEquals(setOf(byTitle.getValue("brush teeth").id, byTitle.getValue("shower").id), ticked)
    }

    @Test
    fun removingAnItemOnOneDeviceRemovesItAndItsTicks() {
        val base = empty.copy(
            checklistItems = listOf(item(1, "Brush teeth", created = 1_000), item(2, "Shower", created = 1_100, order = 1)),
            checklistChecks = listOf(ChecklistCheckEntity(2, today - 1, 1_200)),
        )
        val local = base.copy(checklistItems = base.checklistItems.filter { it.id != 2L }, checklistChecks = emptyList())
        val remote = base.copy(checklistChecks = base.checklistChecks + ChecklistCheckEntity(1, today, 2_000))
        val merged = merge(base, local, remote)
        assertEquals(listOf("Brush teeth"), merged.checklistItems.map { it.title })
        assertEquals(listOf(ChecklistCheckEntity(1, today, 2_000)), merged.checklistChecks)
    }

    @Test
    fun untickingOnOneDeviceUnticks() {
        val base = empty.copy(
            checklistItems = listOf(item(1, "Brush teeth", created = 1_000)),
            checklistChecks = listOf(ChecklistCheckEntity(1, today, 1_500)),
        )
        val local = base.copy(checklistChecks = emptyList())
        assertTrue(merge(base, local, base).checklistChecks.isEmpty())
    }

    @Test
    fun aRenameAndAReorderOnDifferentDevicesAreBothKept() {
        val base = empty.copy(checklistItems = listOf(item(1, "Brush teeth", created = 1_000), item(2, "Shower", created = 1_100, order = 1)))
        val local = base.copy(
            checklistItems = listOf(item(1, "Brush teeth twice", created = 1_000, updated = 2_000), item(2, "Shower", created = 1_100, order = 1)),
        )
        val remote = base.copy(
            checklistItems = listOf(
                item(1, "Brush teeth", created = 1_000, order = 1, updated = 3_000),
                item(2, "Shower", created = 1_100, order = 0, updated = 3_000),
            ),
        )
        val merged = merge(base, local, remote).checklistItems.sortedBy { it.sortOrder }
        assertEquals(listOf("Shower", "Brush teeth twice"), merged.map { it.title })
    }
}
