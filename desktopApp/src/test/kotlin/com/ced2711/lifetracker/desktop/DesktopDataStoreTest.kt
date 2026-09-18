package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.data.local.TodoSeriesEntity
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopDataStoreTest {
    @Test
    fun encryptedRoundTripKeepsRecordsAndAttachmentBytes() = runBlocking {
        val root = Files.createTempDirectory("life-tracker-desktop-test").toFile()
        val password = testPassword()
        try {
            val source = root.resolve("source.txt").apply { writeText("private attachment") }
            val store = DesktopDataStore(root.resolve("app"))
            assertTrue(store.open(password.copyOf()))
            assertTrue(
                store.upsertTodo(
                    id = null,
                    title = "",
                    description = "Prepare the release",
                    deadlineEpochDay = 21_000,
                    priority = TodoPriority.HIGH,
                    categoryId = null,
                    tagsCsv = "release, desktop",
                    completed = false,
                ),
            )
            assertTrue(store.addNoteFolder("Projects"))
            val folderId = store.currentSnapshot()!!.noteFolders.single().id
            assertTrue(store.upsertNote(null, folderId, "Plan", "Long form note", true))
            val noteId = store.currentSnapshot()!!.notes.single().id
            assertTrue(store.attachFile(AttachmentOwnerType.NOTE, noteId, source))
            assertTrue(store.upsertVault(null, "Mail", "person@example.invalid", "value", "", ""))
            assertTrue(store.upsertLedger(null, LedgerType.EXPENSE, 1250, 21_000, "Lunch", "Cafe", "food"))

            val attachmentId = store.currentSnapshot()!!.attachments.single().id
            store.close()

            val reopened = DesktopDataStore(root.resolve("app"))
            assertTrue(reopened.open(testPassword()))
            val snapshot = reopened.currentSnapshot()!!
            assertEquals("Prepare the release", snapshot.todos.single().title)
            assertEquals("release,desktop", snapshot.todos.single().tagsCsv)
            assertEquals("Plan", snapshot.notes.single().title)
            assertEquals(1, snapshot.vaultEntries.size)
            assertEquals(1, snapshot.ledgerEntries.size)
            assertArrayEquals(source.readBytes(), reopened.attachmentFile(attachmentId)!!.readBytes())
            reopened.close()
        } finally {
            password.fill('\u0000')
            root.deleteRecursively()
        }
    }

    @Test
    fun staleCloudRestoreCannotOverwriteNewLocalChanges() = runBlocking {
        val root = Files.createTempDirectory("life-tracker-desktop-conflict-test").toFile()
        try {
            val store = DesktopDataStore(root.resolve("app"))
            assertTrue(store.open(testPassword()))
            val upload = store.createUploadSnapshot()
            assertTrue(
                store.upsertTodo(
                    null,
                    "",
                    "Changed while downloading",
                    null,
                    TodoPriority.NONE,
                    null,
                    "",
                    false,
                ),
            )

            assertEquals(
                DesktopReplaceResult.LocalChanged,
                store.replaceFromEncrypted(upload.file, upload.fingerprint),
            )
            assertEquals("Changed while downloading", store.currentSnapshot()!!.todos.single().description)
            val corrupt = root.resolve("corrupt.tlb").apply { writeText("not a backup") }
            val currentFingerprint = store.localFingerprint()
            assertEquals(
                DesktopReplaceResult.Invalid,
                store.replaceFromEncrypted(corrupt, currentFingerprint),
            )
            assertEquals("Changed while downloading", store.currentSnapshot()!!.todos.single().description)
            val other = DesktopDataStore(root.resolve("other-app"))
            assertTrue(other.open(charArrayOf('d', 'i', 'f', 'f', 'e', 'r', 'e', 'n', 't', '-', '1', '2', '3', '!')))
            val otherUpload = other.createUploadSnapshot()
            assertFalse(store.verifyEncrypted(otherUpload.file))
            assertEquals("Changed while downloading", store.currentSnapshot()!!.todos.single().description)
            otherUpload.file.delete()
            other.close()
            upload.file.delete()
            store.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun editingAndDeletingTodoPreservesDesktopUnsupportedMetadata() = runBlocking {
        val root = Files.createTempDirectory("life-tracker-desktop-metadata-test").toFile()
        try {
            val store = DesktopDataStore(root.resolve("app"))
            assertTrue(store.open(testPassword()))
            store.upsertTodo(null, "Original", "Details", null, TodoPriority.LOW, null, "one", false)
            val id = store.currentSnapshot()!!.todos.single().id
            store.mutate { snapshot ->
                val occurrenceDay = 21_010L
                snapshot.copy(
                    todoSeries = listOf(
                        TodoSeriesEntity(
                            id = 7,
                            title = "Original",
                            description = "Details",
                            startEpochDay = occurrenceDay,
                            recurrenceUnit = RecurrenceUnit.WEEK,
                            createdAt = snapshot.createdAt,
                            updatedAt = snapshot.createdAt,
                        ),
                    ),
                    todos = snapshot.todos.map {
                        it.copy(
                            seriesId = 7,
                            occurrenceEpochDay = occurrenceDay,
                            clientOperationToken = "desktop-test-token",
                        )
                    },
                )
            }

            store.upsertTodo(id, "Edited", "Details", null, TodoPriority.HIGH, null, "two", true)
            val edited = store.currentSnapshot()!!.todos.single()
            assertEquals("desktop-test-token", edited.clientOperationToken)
            assertEquals(7L, edited.seriesId)
            assertEquals(21_010L, edited.occurrenceEpochDay)
            assertNotNull(edited.completedAt)

            store.deleteTodo(id)
            val deleted = store.currentSnapshot()!!.todos.single()
            assertNotNull(deleted.deletedAt)
            assertEquals("desktop-test-token", deleted.clientOperationToken)
            assertEquals(7L, deleted.seriesId)
            assertEquals(1, store.currentSnapshot()!!.todoSeries.size)
            assertNull(store.currentSnapshot()!!.todos.firstOrNull { it.id == id && it.deletedAt == null })
            store.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun secondDesktopProcessCannotOpenTheSameLocalFile() = runBlocking {
        val root = Files.createTempDirectory("life-tracker-desktop-lock-test").toFile()
        try {
            val first = DesktopDataStore(root.resolve("app"))
            val second = DesktopDataStore(root.resolve("app"))
            assertTrue(first.open(testPassword()))
            assertTrue(!second.open(testPassword()))
            assertTrue(second.state.value is DesktopStoreState.Error)

            first.close()
            assertTrue(second.open(testPassword()))
            second.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun customizedSynchronizedSettingsAreNotTreatedAsEmptyData() = runBlocking {
        val root = Files.createTempDirectory("life-tracker-desktop-settings-test").toFile()
        try {
            val store = DesktopDataStore(root.resolve("app"))
            assertTrue(store.open(testPassword()))
            assertTrue(store.isUserDataEmpty())
            assertTrue(store.setAccentColor(AccentColor.VIOLET))
            assertFalse(store.isUserDataEmpty())
            store.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun manualImportMigratesDifferentBackupPasswordAndKeepsAttachments(): Unit = runBlocking {
        val root = Files.createTempDirectory("life-tracker-desktop-password-migration-test").toFile()
        val attachmentBytes = "attachment from the Android backup".encodeToByteArray()
        try {
            val attachmentSource = root.resolve("source-document.txt").apply { writeBytes(attachmentBytes) }
            val sourceStore = DesktopDataStore(root.resolve("source-app"))
            assertTrue(sourceStore.open(sourceBackupPassword()))
            assertTrue(sourceStore.upsertNote(null, null, "Imported note", "Created on Android", false))
            val noteId = sourceStore.currentSnapshot()!!.notes.single().id
            assertTrue(sourceStore.attachFile(AttachmentOwnerType.NOTE, noteId, attachmentSource))
            val encryptedSource = sourceStore.createUploadSnapshot()
            sourceStore.close()

            val targetStore = DesktopDataStore(root.resolve("target-app"))
            assertTrue(targetStore.open(targetLocalPassword()))
            assertTrue(targetStore.upsertTodo(null, "Temporary", "Will be replaced", null, TodoPriority.NONE, null, "", false))
            val expectedFingerprint = targetStore.localFingerprint()
            val result = targetStore.importFromEncrypted(
                encryptedSource.file,
                sourceBackupPassword(),
                expectedFingerprint,
            )
            assertTrue(result is DesktopReplaceResult.Applied)
            assertEquals("Imported note", targetStore.currentSnapshot()!!.notes.single().title)
            assertTrue(targetStore.currentSnapshot()!!.todos.isEmpty())
            targetStore.close()

            val reopened = DesktopDataStore(root.resolve("target-app"))
            assertTrue(reopened.open(targetLocalPassword()))
            val importedAttachmentId = reopened.currentSnapshot()!!.attachments.single().id
            assertArrayEquals(attachmentBytes, reopened.attachmentFile(importedAttachmentId)!!.readBytes())
            reopened.close()

            val oldPasswordAttempt = DesktopDataStore(root.resolve("target-app"))
            assertFalse(oldPasswordAttempt.open(sourceBackupPassword()))
            oldPasswordAttempt.close()
            encryptedSource.file.delete()
        } finally {
            attachmentBytes.fill(0)
            root.deleteRecursively()
        }
    }

    private fun testPassword(): CharArray = charArrayOf(
        'd', 'e', 's', 'k', 't', 'o', 'p', '-', 't', 'e', 's', 't', '-', '1', '7', '!',
    )

    private fun sourceBackupPassword(): CharArray = charArrayOf(
        'o', 'l', 'd', '-', 'a', 'n', 'd', 'r', 'o', 'i', 'd', '-', '2', '6', '!',
    )

    private fun targetLocalPassword(): CharArray = charArrayOf(
        'n', 'e', 'w', '-', 'w', 'i', 'n', 'd', 'o', 'w', 's', '-', '2', '6', '!',
    )
}
