package com.ced2711.lifetracker.data.backup

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreCommitMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TaskLedgerDatabase::class.java,
    )

    @Test
    fun migration4To5PreservesBusinessRowsAndStartsWithoutMarker() {
        val databaseName = "restore-commit-migration"
        helper.createDatabase(databaseName, 4).apply {
            execSQL(
                """
                INSERT INTO todos
                    (id, title, description, priority, tagsCsv, createdAt, updatedAt,
                     customOrder, clientOperationToken)
                VALUES (41, 'Upgrade sentinel', 'Keep me', 'NONE', '', 10, 11, 1, 'sentinel')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            TaskLedgerDatabase.MIGRATION_4_5,
        ).apply {
            query("SELECT title, description, clientOperationToken FROM todos WHERE id = 41").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Upgrade sentinel", cursor.getString(0))
                assertEquals("Keep me", cursor.getString(1))
                assertEquals("sentinel", cursor.getString(2))
            }
            query("SELECT COUNT(*) FROM restore_commit_state").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            close()
        }
    }
}
