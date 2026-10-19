package com.ced2711.lifetracker.data.checklist

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChecklistMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TaskLedgerDatabase::class.java,
    )

    @Test
    fun migration7To8KeepsDataAndAddsTheDailyChecklist() {
        val databaseName = "checklist-migration"
        helper.createDatabase(databaseName, 7).apply {
            execSQL(
                "INSERT INTO diary_entries (id, epochDay, body, createdAt, updatedAt) " +
                    "VALUES (1, 20000, 'Keep me', 30, 31)",
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 8, true, TaskLedgerDatabase.MIGRATION_7_8).apply {
            query("SELECT body FROM diary_entries WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Keep me", cursor.getString(0))
            }
            execSQL("PRAGMA foreign_keys = ON")
            execSQL("INSERT INTO daily_checklist_items (id, title, sortOrder, createdAt, updatedAt) VALUES (1, 'Brush teeth', 0, 40, 40)")
            execSQL("INSERT INTO daily_checklist_checks (itemId, epochDay, checkedAt) VALUES (1, 20000, 41)")
            // One tick per item and day.
            assertThrows(SQLiteConstraintException::class.java) {
                execSQL("INSERT INTO daily_checklist_checks (itemId, epochDay, checkedAt) VALUES (1, 20000, 42)")
            }
            // Removing an item removes its ticks.
            execSQL("DELETE FROM daily_checklist_items WHERE id = 1")
            query("SELECT COUNT(*) FROM daily_checklist_checks").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            close()
        }
    }
}
