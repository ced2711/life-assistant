package com.ced2711.lifetracker.data.diary

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
class DiaryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TaskLedgerDatabase::class.java,
    )

    @Test
    fun migration6To7PreservesExistingDataAndAddsOneDiaryPagePerDay() {
        val databaseName = "diary-migration"
        helper.createDatabase(databaseName, 6).apply {
            execSQL(
                "INSERT INTO notes (id, folderId, title, body, pinned, createdAt, updatedAt) " +
                    "VALUES (3, NULL, 'Upgrade sentinel', 'Keep me', 0, 22, 23)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            7,
            true,
            TaskLedgerDatabase.MIGRATION_6_7,
        ).apply {
            query("SELECT body FROM notes WHERE id = 3").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Keep me", cursor.getString(0))
            }
            execSQL(
                "INSERT INTO diary_entries (id, epochDay, body, createdAt, updatedAt) " +
                    "VALUES (1, 20000, '今天 ✓', 30, 31)",
            )
            assertThrows(SQLiteConstraintException::class.java) {
                execSQL(
                    "INSERT INTO diary_entries (id, epochDay, body, createdAt, updatedAt) " +
                        "VALUES (2, 20000, 'second page', 32, 33)",
                )
            }
            close()
        }
    }
}
