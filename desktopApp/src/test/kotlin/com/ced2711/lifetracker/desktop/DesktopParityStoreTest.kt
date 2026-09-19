package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TodoReminderEntity
import com.ced2711.lifetracker.data.local.TodoSeriesEntity
import com.ced2711.lifetracker.data.local.TodoSeriesSubtaskEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.WeekStart
import com.ced2711.lifetracker.domain.model.TodoPriority
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopParityStoreTest {
    @Test
    fun rebrandKeepsLegacyStorageDirectoryAndReadsExistingDataAndConfig() = runBlocking {
        assertEquals("Life Tracker", DesktopDataStore.defaultAppDirectory().name)
        val root = Files.createTempDirectory("life-assistant-rebrand-compatibility").toFile()
        val legacyDirectory = root.resolve("Life Tracker")
        try {
            val store = DesktopDataStore(legacyDirectory)
            assertTrue(store.open(password()))
            assertTrue(store.upsertTodo(null, "Legacy record", "Still readable", null, TodoPriority.NONE, null, "", false))
            store.close()

            val configFile = legacyDirectory.resolve("desktop.properties")
            val config = DesktopConfigStore(configFile)
            config.setClientId("legacy-client")
            config.setUiLanguage(UiLanguage.SIMPLIFIED_CHINESE)

            val reopened = DesktopDataStore(legacyDirectory)
            assertTrue(reopened.open(password()))
            assertEquals("Legacy record", reopened.currentSnapshot()!!.todos.single().title)
            reopened.close()
            val persistedConfig = DesktopConfigStore(configFile).read()
            assertEquals("legacy-client", persistedConfig.clientId)
            assertEquals(UiLanguage.SIMPLIFIED_CHINESE, persistedConfig.uiLanguage)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun configDefaultsOfflineAndDeviceLocalSettingsSurviveDisconnect() {
        val root = Files.createTempDirectory("life-tracker-desktop-config-test").toFile()
        try {
            val config = DesktopConfigStore(root.resolve("desktop.properties"))
            val defaults = config.read()
            assertFalse(defaults.automaticSync)
            assertEquals(UiLanguage.ENGLISH, defaults.uiLanguage)
            assertEquals(TopLevelDestination.TODO, defaults.lastDestination)

            config.setClientId("desktop-client")
            config.setAutomaticSync(true)
            config.setUiLanguage(UiLanguage.SIMPLIFIED_CHINESE)
            config.setLastDestination(TopLevelDestination.NOTES)
            config.recordSync("revision-1", "fingerprint-1", 1234L)
            config.clearSyncState()

            val persisted = config.read()
            assertEquals("desktop-client", persisted.clientId)
            assertTrue(persisted.automaticSync)
            assertEquals(UiLanguage.SIMPLIFIED_CHINESE, persisted.uiLanguage)
            assertEquals(TopLevelDestination.NOTES, persisted.lastDestination)
            assertNull(persisted.syncState.lastRevisionId)
            assertNull(persisted.syncState.lastContentFingerprint)
            assertNull(persisted.syncState.lastSyncAt)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun settingMutationsPreserveRecurrenceRemindersSubtasksAndRoundTrip() = runBlocking {
        val root = Files.createTempDirectory("life-tracker-desktop-settings-roundtrip").toFile()
        try {
            val store = DesktopDataStore(root.resolve("app"))
            assertTrue(store.open(password()))
            assertTrue(store.upsertTodo(null, "Recurring", "Details", 21_000L, TodoPriority.HIGH, null, "tag", false))
            val todoId = store.currentSnapshot()!!.todos.single().id
            store.mutate { snapshot ->
                val now = snapshot.createdAt
                snapshot.copy(
                    todoSeries = listOf(
                        TodoSeriesEntity(
                            id = 7,
                            title = "Recurring",
                            description = "Details",
                            startEpochDay = 21_000L,
                            recurrenceUnit = RecurrenceUnit.WEEK,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    ),
                    todoSeriesSubtasks = listOf(TodoSeriesSubtaskEntity(7, 0, "Series subtask")),
                    todos = snapshot.todos.map { it.copy(seriesId = 7, occurrenceEpochDay = 21_000L) },
                    subtasks = listOf(SubtaskEntity(3, todoId, "Do it", false, 0)),
                    todoReminders = listOf(TodoReminderEntity(4, todoId, 60L)),
                )
            }

            assertTrue(store.setThemeMode(ThemeMode.LIGHT))
            assertTrue(store.setWeekStart(WeekStart.MONDAY))
            assertTrue(store.setTimeFormat(TimeFormatOption.HOUR_24))
            assertTrue(store.setDateFormat(DateFormatOption.YEAR_MONTH_DAY))
            val beforeClose = store.currentSnapshot()!!
            assertEquals(7L, beforeClose.todos.single().seriesId)
            assertEquals(1, beforeClose.subtasks.size)
            assertEquals(1, beforeClose.todoReminders.size)
            assertEquals(ThemeMode.LIGHT, beforeClose.settings.themeMode)
            assertEquals(WeekStart.MONDAY, beforeClose.settings.weekStart)
            assertEquals(TimeFormatOption.HOUR_24, beforeClose.settings.timeFormat)
            assertEquals(DateFormatOption.YEAR_MONTH_DAY, beforeClose.settings.dateFormat)
            store.close()

            val reopened = DesktopDataStore(root.resolve("app"))
            assertTrue(reopened.open(password()))
            val afterRoundTrip = reopened.currentSnapshot()!!
            assertEquals(beforeClose.todos, afterRoundTrip.todos)
            assertEquals(beforeClose.todoSeries, afterRoundTrip.todoSeries)
            assertEquals(beforeClose.todoSeriesSubtasks, afterRoundTrip.todoSeriesSubtasks)
            assertEquals(beforeClose.subtasks, afterRoundTrip.subtasks)
            assertEquals(beforeClose.todoReminders, afterRoundTrip.todoReminders)
            assertEquals(beforeClose.settings, afterRoundTrip.settings)
            reopened.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletingFolderUnfilesNotesPromotesChildrenAndPreservesAttachments() = runBlocking {
        val root = Files.createTempDirectory("life-tracker-desktop-folder-test").toFile()
        val bytes = "folder attachment".encodeToByteArray()
        try {
            val store = DesktopDataStore(root.resolve("app"))
            assertTrue(store.open(password()))
            assertTrue(store.addNoteFolder("Parent"))
            val parentId = store.currentSnapshot()!!.noteFolders.single { it.name == "Parent" }.id
            assertTrue(store.addNoteFolder("Child", parentId))
            val childId = store.currentSnapshot()!!.noteFolders.single { it.name == "Child" }.id
            assertTrue(store.addNoteFolder("Grandchild", childId))
            val grandchildId = store.currentSnapshot()!!.noteFolders.single { it.name == "Grandchild" }.id
            assertTrue(store.upsertNote(null, parentId, "Parent note", "", false))
            assertTrue(store.upsertNote(null, childId, "Child note", "", false))
            val childNoteId = store.currentSnapshot()!!.notes.single { it.title == "Child note" }.id
            val source = root.resolve("attachment.txt").apply { writeBytes(bytes) }
            assertTrue(store.attachFile(AttachmentOwnerType.NOTE, childNoteId, source))

            assertTrue(store.renameNoteFolder(grandchildId, "Renamed grandchild"))
            assertEquals("Renamed grandchild", store.currentSnapshot()!!.noteFolders.single { it.id == grandchildId }.name)
            assertTrue(store.deleteNoteFolder(parentId))

            val snapshot = store.currentSnapshot()!!
            assertEquals(2, snapshot.noteFolders.size)
            assertEquals(null, snapshot.noteFolders.single { it.id == childId }.parentId)
            assertEquals(childId, snapshot.noteFolders.single { it.id == grandchildId }.parentId)
            assertNull(snapshot.notes.single { it.title == "Parent note" }.folderId)
            assertEquals(childId, snapshot.notes.single { it.title == "Child note" }.folderId)
            val attachmentId = snapshot.attachments.single().id
            assertArrayEquals(bytes, store.attachmentFile(attachmentId)!!.readBytes())
            store.close()
        } finally {
            bytes.fill(0)
            root.deleteRecursively()
        }
    }

    @Test
    fun folderNamesAndIdsAreValidated() = runBlocking {
        val root = Files.createTempDirectory("life-tracker-desktop-folder-validation").toFile()
        try {
            val store = DesktopDataStore(root.resolve("app"))
            assertTrue(store.open(password()))
            assertFails { store.addNoteFolder("   ") }
            assertFails { store.addNoteFolder("Missing parent", 99L) }
            assertTrue(store.addNoteFolder("Folder"))
            assertFails { store.addNoteFolder(" folder ") }
            assertFails { store.renameNoteFolder(0L, "Invalid") }
            assertFails { store.deleteNoteFolder(0L) }
            store.close()
        } finally {
            root.deleteRecursively()
        }
    }

    private fun password() = charArrayOf('d', 'e', 's', 'k', 't', 'o', 'p', '-', 'p', 'a', 'r', 'i', 't', 'y', '-', '2', '6', '!')

    private suspend fun assertFails(block: suspend () -> Unit) {
        val failed = try {
            block()
            false
        } catch (_: Throwable) {
            true
        }
        assertTrue(failed)
    }
}
