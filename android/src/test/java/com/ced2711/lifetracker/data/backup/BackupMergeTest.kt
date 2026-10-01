package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.DiaryEntryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {
    private val empty = fullBackupSnapshot().copy(
        categories = emptyList(), todoSeries = emptyList(), todoSeriesSubtasks = emptyList(),
        todoOccurrenceExceptions = emptyList(), todos = emptyList(), subtasks = emptyList(),
        todoReminders = emptyList(), ledgerSeries = emptyList(), ledgerOccurrenceExceptions = emptyList(),
        ledgerEntries = emptyList(), attachments = emptyList(), vaultEntries = emptyList(),
        noteFolders = emptyList(), notes = emptyList(), diaryEntries = emptyList(),
    )

    private fun todo(id: Long, title: String, created: Long = 1_000, updated: Long = created, description: String = "") =
        TodoEntity(id = id, title = title, description = description, createdAt = created, updatedAt = updated, customOrder = created)

    private fun note(id: Long, title: String, body: String, created: Long = 1_000, updated: Long = created) =
        NoteEntity(id = id, title = title, body = body, createdAt = created, updatedAt = updated)

    private fun ledger(id: Long, cents: Long, created: Long = 1_000, updated: Long = created) =
        LedgerEntryEntity(id = id, type = LedgerType.EXPENSE, amountCents = cents, epochDay = 20_000, minuteOfDay = 600, createdAt = created, updatedAt = updated)

    private fun merge(base: BackupSnapshot?, local: BackupSnapshot, remote: BackupSnapshot) =
        mergeSnapshots(base, local, remote, now = 50_000)

    @Test
    fun changesToDifferentRecordsAreBothKept() {
        val base = empty.copy(todos = listOf(todo(1, "Buy milk"), todo(2, "Call mom")))
        val local = base.copy(todos = listOf(todo(1, "Buy oat milk", updated = 2_000), todo(2, "Call mom")))
        val remote = base.copy(todos = listOf(todo(1, "Buy milk"), todo(2, "Call mom", updated = 3_000).copy(completedAt = 3_000)))

        val merged = merge(base, local, remote).snapshot.todos.associateBy(TodoEntity::id)
        assertEquals("Buy oat milk", merged.getValue(1).title)
        assertEquals(3_000L, merged.getValue(2).completedAt)
    }

    @Test
    fun differentFieldsOfTheSameRecordAreCombined() {
        val base = empty.copy(todos = listOf(todo(1, "Report")))
        val local = base.copy(todos = listOf(todo(1, "Quarterly report", updated = 2_000)))
        val remote = base.copy(todos = listOf(todo(1, "Report", updated = 3_000).copy(priority = TodoPriority.URGENT)))

        val merged = merge(base, local, remote).snapshot.todos.single()
        assertEquals("Quarterly report", merged.title)
        assertEquals(TodoPriority.URGENT, merged.priority)
        assertEquals(3_000L, merged.updatedAt)
    }

    @Test
    fun theNewerEditWinsWhenBothChangedTheSameField() {
        val base = empty.copy(ledgerEntries = listOf(ledger(1, 1_000)))
        val local = base.copy(ledgerEntries = listOf(ledger(1, 1_200, updated = 2_000)))
        val remote = base.copy(ledgerEntries = listOf(ledger(1, 1_500, updated = 3_000)))
        assertEquals(1_500L, merge(base, local, remote).snapshot.ledgerEntries.single().amountCents)
        assertEquals(1_500L, merge(base, remote, local).snapshot.ledgerEntries.single().amountCents)
    }

    @Test
    fun recordsAddedOnBothDevicesWithTheSameIdAreBothKeptWithReferences() {
        val base = empty.copy(todos = listOf(todo(1, "Old")))
        // Both devices numbered their new todo 2, with a subtask and an attachment each.
        val localAttachment = attachment(id = 1, owner = 2, created = 2_100, hash = 1)
        val local = base.copy(
            todos = base.todos + todo(2, "Phone todo", created = 2_000),
            subtasks = listOf(SubtaskEntity(1, 2, "phone step")),
            attachments = listOf(localAttachment),
        )
        val remoteAttachment = attachment(id = 1, owner = 2, created = 3_100, hash = 2)
        val remote = base.copy(
            todos = base.todos + todo(2, "PC todo", created = 3_000),
            subtasks = listOf(SubtaskEntity(1, 2, "pc step")),
            attachments = listOf(remoteAttachment),
        )

        val result = merge(base, local, remote)
        val todos = result.snapshot.todos
        assertEquals(setOf("Old", "Phone todo", "PC todo"), todos.map { it.title }.toSet())
        val phone = todos.single { it.title == "Phone todo" }
        val pc = todos.single { it.title == "PC todo" }
        assertEquals("phone step", result.snapshot.subtasks.single { it.todoId == phone.id }.description)
        assertEquals("pc step", result.snapshot.subtasks.single { it.todoId == pc.id }.description)
        val phoneAttachment = result.snapshot.attachments.single { it.ownerId == phone.id }
        val pcAttachment = result.snapshot.attachments.single { it.ownerId == pc.id }
        assertEquals(MergedAttachmentSource(MergeSide.LOCAL, 1), result.attachmentSources[phoneAttachment.id])
        assertEquals(MergedAttachmentSource(MergeSide.REMOTE, 1), result.attachmentSources[pcAttachment.id])
        assertEquals(attachmentArchivePath(phoneAttachment.id, phoneAttachment.sha256), phoneAttachment.archivePath)
    }

    @Test
    fun deletionOnOneSideRemovesTheRecordAndItsChildren() {
        val base = empty.copy(
            todos = listOf(todo(1, "Done with this"), todo(2, "Keep")),
            subtasks = listOf(SubtaskEntity(1, 1, "step")),
            attachments = listOf(attachment(id = 1, owner = 1, created = 1_100, hash = 3)),
        )
        val local = base.copy(todos = listOf(todo(2, "Keep")), subtasks = emptyList(), attachments = emptyList())
        val merged = merge(base, local, base).snapshot
        assertEquals(listOf("Keep"), merged.todos.map { it.title })
        assertTrue(merged.subtasks.isEmpty())
        assertTrue(merged.attachments.isEmpty())
    }

    @Test
    fun anEditBeatsADeletionOnTheOtherDevice() {
        val base = empty.copy(notes = listOf(note(1, "Ideas", "one")))
        val local = base.copy(notes = emptyList())
        val remote = base.copy(notes = listOf(note(1, "Ideas", "one, two", updated = 3_000)))
        assertEquals("one, two", merge(base, local, remote).snapshot.notes.single().body)
    }

    @Test
    fun textEditedOnBothDevicesKeepsBothVersions() {
        val base = empty.copy(notes = listOf(note(1, "Trip", "Pack bags")))
        val local = base.copy(notes = listOf(note(1, "Trip", "Pack bags and passport", updated = 2_000)))
        val remote = base.copy(notes = listOf(note(1, "Trip", "Book hotel", updated = 3_000)))
        val result = merge(base, local, remote)
        val body = result.snapshot.notes.single().body
        assertTrue(body.startsWith("Book hotel"))
        assertTrue(body.contains(MERGE_TEXT_MARKER))
        assertTrue(body.endsWith("Pack bags and passport"))
        assertEquals(1, result.textConflicts)
    }

    @Test
    fun diaryPagesForTheSameDayBecomeOnePage() {
        val local = empty.copy(diaryEntries = listOf(DiaryEntryEntity(1, 20_000, "Morning run", 2_000, 2_000)))
        val remote = empty.copy(diaryEntries = listOf(DiaryEntryEntity(1, 20_000, "Evening movie", 3_000, 3_000)))
        val page = merge(empty, local, remote).snapshot.diaryEntries.single()
        assertTrue(page.body.contains("Morning run") && page.body.contains("Evening movie"))
    }

    @Test
    fun firstConnectionWithoutABaseKeepsEverythingFromBothDevices() {
        val local = empty.copy(todos = listOf(todo(1, "Phone only", created = 2_000)), ledgerEntries = listOf(ledger(1, 500, created = 2_000)))
        val remote = empty.copy(todos = listOf(todo(1, "PC only", created = 3_000)), ledgerEntries = listOf(ledger(1, 700, created = 3_000)))
        val merged = merge(null, local, remote).snapshot
        assertEquals(setOf("Phone only", "PC only"), merged.todos.map { it.title }.toSet())
        assertEquals(setOf(500L, 700L), merged.ledgerEntries.map { it.amountCents }.toSet())
    }

    @Test
    fun theSameRecordOnBothSidesWithoutABaseIsNotDuplicated() {
        val shared = todo(1, "Shared", created = 1_500)
        val merged = merge(null, empty.copy(todos = listOf(shared)), empty.copy(todos = listOf(shared.copy(title = "Shared!", updatedAt = 4_000)))).snapshot
        assertEquals(listOf("Shared!"), merged.todos.map { it.title })
    }

    @Test
    fun categoriesCreatedOnBothDevicesWithTheSameNameAreOne() {
        val local = empty.copy(categories = listOf(CategoryEntity(1, "Work", null, 0, 2_000)), todos = listOf(todo(1, "A", created = 2_000).copy(categoryId = 1)))
        val remote = empty.copy(categories = listOf(CategoryEntity(1, "work", null, 0, 3_000)), todos = listOf(todo(1, "B", created = 3_000).copy(categoryId = 1)))
        val merged = merge(empty, local, remote).snapshot
        val category = merged.categories.single()
        assertTrue(merged.todos.all { it.categoryId == category.id })
    }

    @Test
    fun aDeletedCategoryLeavesTodosAddedMeanwhileUncategorized() {
        val base = empty.copy(categories = listOf(CategoryEntity(1, "Temp", null, 0, 500)))
        val local = base.copy(categories = emptyList())
        val remote = base.copy(todos = listOf(todo(1, "New", created = 3_000).copy(categoryId = 1)))
        val merged = merge(base, local, remote).snapshot
        assertTrue(merged.categories.isEmpty())
        assertNull(merged.todos.single().categoryId)
    }

    @Test
    fun subtasksAreMergedItemByItem() {
        val base = empty.copy(todos = listOf(todo(1, "Move")), subtasks = listOf(SubtaskEntity(1, 1, "Pack", false, 0), SubtaskEntity(2, 1, "Clean", false, 1)))
        val local = base.copy(subtasks = listOf(SubtaskEntity(5, 1, "Pack", true, 0), SubtaskEntity(6, 1, "Clean", false, 1)))
        val remote = base.copy(subtasks = listOf(SubtaskEntity(7, 1, "Pack", false, 0), SubtaskEntity(8, 1, "Clean", false, 1), SubtaskEntity(9, 1, "Return keys", false, 2)))
        val subtasks = merge(base, local, remote).snapshot.subtasks
        assertEquals(listOf("Pack" to true, "Clean" to false, "Return keys" to false), subtasks.map { it.description to it.isCompleted })
    }

    @Test
    fun vaultAndSettingsMergePerField() {
        val entry = VaultEntry("6f1c0d1e-2b8a-4c55-9a51-0d7a3b1c2e4f", "Bank", "me", "old", "", "", 1_000, 1_000)
        val base = empty.copy(vaultEntries = listOf(entry))
        val local = base.copy(vaultEntries = listOf(entry.copy(password = "new", updatedAt = 2_000)), settings = base.settings.copy(themeMode = ThemeMode.LIGHT))
        val remote = base.copy(vaultEntries = listOf(entry.copy(website = "bank.example", updatedAt = 3_000)), settings = base.settings.copy(weekStart = com.ced2711.lifetracker.domain.model.WeekStart.MONDAY))
        val merged = merge(base, local, remote).snapshot
        assertEquals("new" to "bank.example", merged.vaultEntries.single().let { it.password to it.website })
        assertEquals(ThemeMode.LIGHT, merged.settings.themeMode)
        assertEquals(com.ced2711.lifetracker.domain.model.WeekStart.MONDAY, merged.settings.weekStart)
    }

    @Test
    fun theFullFixtureMergesWithItself() {
        val full = fullBackupSnapshot()
        val result = merge(full, full, full)
        assertEquals(full.todos, result.snapshot.todos)
        assertEquals(full.attachments.map { it.id }, result.snapshot.attachments.map { it.id })
        assertEquals(0, result.textConflicts)
    }

    private fun attachment(id: Long, owner: Long, created: Long, hash: Int): BackupAttachment {
        val sha = ByteArray(32) { hash.toByte() }
        return BackupAttachment(
            id = id, ownerType = AttachmentOwnerType.TODO, ownerId = owner, archivePath = attachmentArchivePath(id, sha),
            originalName = "file$hash.txt", mimeType = "text/plain", sizeBytes = 3, sha256 = sha, createdAt = created, pendingDeleteAt = null,
        )
    }
}
