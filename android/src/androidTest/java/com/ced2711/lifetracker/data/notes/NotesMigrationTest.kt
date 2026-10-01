package com.ced2711.lifetracker.data.notes

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TaskLedgerDatabase::class.java,
    )

    @Test
    fun migration5To6PreservesExistingDataAndCreatesHierarchicalNotes() {
        val databaseName = "notes-migration"
        helper.createDatabase(databaseName, 5).apply {
            execSQL(
                """
                INSERT INTO todos
                    (id, title, description, priority, tagsCsv, createdAt, updatedAt, customOrder)
                VALUES (41, 'Upgrade sentinel', 'Keep me', 'NONE', '', 10, 11, 1)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            TaskLedgerDatabase.MIGRATION_5_6,
        ).apply {
            query("SELECT description FROM todos WHERE id = 41").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Keep me", cursor.getString(0))
            }
            execSQL(
                "INSERT INTO note_folders (id, name, parentId, sortOrder, createdAt) " +
                    "VALUES (1, 'Reference', NULL, 0, 20)",
            )
            execSQL(
                "INSERT INTO note_folders (id, name, parentId, sortOrder, createdAt) " +
                    "VALUES (2, 'Accounts', 1, 0, 21)",
            )
            execSQL(
                "INSERT INTO notes (id, folderId, title, body, pinned, createdAt, updatedAt) " +
                    "VALUES (3, 2, 'Long-term note', 'Unicode ✓', 1, 22, 23)",
            )
            query(
                "SELECT note_folders.parentId, notes.title, notes.pinned " +
                    "FROM notes JOIN note_folders ON notes.folderId = note_folders.id " +
                    "WHERE notes.id = 3",
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertEquals("Long-term note", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
            }
            close()
        }
    }
}
