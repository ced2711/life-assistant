package com.ced2711.lifetracker.data.vault

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TaskLedgerDatabase::class.java,
    )

    @Test
    fun migration2To3PreservesExistingRowsAndCreatesWritableVaultTable() {
        helper.createDatabase(DATABASE_NAME, 2).apply {
            execSQL(
                """
                INSERT INTO categories (id, name, parentId, sortOrder, createdAt)
                VALUES (42, 'Existing category', NULL, 0, 123)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            3,
            true,
            TaskLedgerDatabase.MIGRATION_2_3,
        ).apply {
            query("SELECT name, createdAt FROM categories WHERE id = 42").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Existing category", cursor.getString(0))
                assertEquals(123L, cursor.getLong(1))
            }

            execSQL(
                """
                INSERT INTO vault_entries
                    (id, formatVersion, payloadIv, payloadCiphertext, createdAt, updatedAt)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    "6ab976b4-707f-4c1e-ae1d-8dc9cb672993",
                    1,
                    ByteArray(12) { it.toByte() },
                    ByteArray(48) { (it + 1).toByte() },
                    200L,
                    300L,
                ),
            )
            query("SELECT COUNT(*) FROM vault_entries").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "vault-migration-test"
    }
}
