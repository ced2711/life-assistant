package com.ced2711.lifetracker.data.repository

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdempotentSaveMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TaskLedgerDatabase::class.java,
    )

    @Test
    fun migration1To4PreservesRowsAndAddsOperationTokens() = migrateTo4(
        startVersion = 1,
        migrations = arrayOf(
            TaskLedgerDatabase.MIGRATION_1_2,
            TaskLedgerDatabase.MIGRATION_2_3,
            TaskLedgerDatabase.MIGRATION_3_4,
        ),
    )

    @Test
    fun migration2To4PreservesRowsAndAddsOperationTokens() = migrateTo4(
        startVersion = 2,
        migrations = arrayOf(
            TaskLedgerDatabase.MIGRATION_2_3,
            TaskLedgerDatabase.MIGRATION_3_4,
        ),
    )

    @Test
    fun migration3To4PreservesRowsAndAddsOperationTokens() = migrateTo4(
        startVersion = 3,
        migrations = arrayOf(TaskLedgerDatabase.MIGRATION_3_4),
    )

    private fun migrateTo4(startVersion: Int, migrations: Array<Migration>) {
        val databaseName = "idempotent-save-migration-$startVersion"
        helper.createDatabase(databaseName, startVersion).apply {
            execSQL(
                """
                INSERT INTO todos
                    (id, title, description, priority, tagsCsv, createdAt, updatedAt, customOrder)
                VALUES (11, 'Existing task', 'Existing task', 'NONE', '', 1, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO ledger_entries
                    (id, type, amountCents, epochDay, minuteOfDay, note, merchant, tagsCsv,
                     createdAt, updatedAt)
                VALUES (12, 'EXPENSE', 100, 1, 0, '', '', '', 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO ledger_series
                    (id, type, amountCents, startEpochDay, recurrenceUnit, intervalCount,
                     note, merchant, tagsCsv, active, createdAt, updatedAt)
                VALUES (13, 'INCOME', 200, 2, 'DAY', 1, '', '', '', 1, 1, 1)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 4, true, *migrations).apply {
            execSQL("UPDATE todos SET clientOperationToken = 'todo-token' WHERE id = 11")
            execSQL("UPDATE ledger_entries SET clientOperationToken = 'entry-token' WHERE id = 12")
            execSQL("UPDATE ledger_series SET clientOperationToken = 'series-token' WHERE id = 13")

            query(
                """
                SELECT
                    (SELECT clientOperationToken FROM todos WHERE id = 11),
                    (SELECT clientOperationToken FROM ledger_entries WHERE id = 12),
                    (SELECT clientOperationToken FROM ledger_series WHERE id = 13)
                """.trimIndent(),
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("todo-token", cursor.getString(0))
                assertEquals("entry-token", cursor.getString(1))
                assertEquals("series-token", cursor.getString(2))
            }
            close()
        }
    }
}
