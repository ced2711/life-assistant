package com.ced2711.lifetracker

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Read-only upgrade evidence for the 1.6.1 -> 1.7.0 rebrand.
 *
 * The test deliberately avoids Room entities, AppIdentity, and all writes so the same source can
 * run against both release versions. The digest is reported to instrumentation rather than
 * printing user data; compare the status values before and after installing the update.
 */
@RunWith(AndroidJUnit4::class)
class RebrandUpgradeSnapshotInstrumentedTest {
    @Test
    fun existingUserDatabaseSnapshotIsReadableAndStableForUpgradeComparison() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        assertTrue("$DATABASE_NAME does not exist", databaseFile.isFile)
        assertTrue(
            "$DATABASE_NAME is not listed by Context.databaseList()",
            context.databaseList().contains(DATABASE_NAME),
        )

        val snapshot = readSnapshot(databaseFile)
        assertTrue("the upgraded snapshot must contain at least one todo", snapshot.todoRows > 0)

        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putString("snapshot_digest", snapshot.digest)
                putLong("row_count", snapshot.rowCount)
                putInt("table_count", snapshot.tableCount)
            },
        )
    }

    private fun readSnapshot(databaseFile: File): Snapshot {
        val database = SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        return try {
            val tables = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table'",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        cursor.getString(0)?.takeIf(::isUserTable)?.let(::add)
                    }
                }.sorted()
            }
            assertTrue("no user tables found in $DATABASE_NAME", tables.isNotEmpty())

            val digest = MessageDigest.getInstance("SHA-256")
            var rowCount = 0L
            var todoRows = 0L
            tables.forEach { table ->
                val quotedTable = quoteIdentifier(table)
                database.rawQuery("SELECT * FROM $quotedTable", null).use { cursor ->
                    val columnOrder = cursor.columnNames.indices.sortedBy { cursor.columnNames[it] }
                    val rows = buildList {
                        while (cursor.moveToNext()) {
                            rowCount += 1
                            if (table == "todos") todoRows += 1
                            add(canonicalRow(cursor, columnOrder))
                        }
                    }.sorted()
                    updateDigest(digest, "table:$table\n")
                    updateDigest(digest, "columns:${columnOrder.joinToString(",") { cursor.columnNames[it] }}\n")
                    rows.forEach { updateDigest(digest, "row:$it\n") }
                }
            }
            Snapshot(
                digest = digest.digest().toHexString(),
                rowCount = rowCount,
                tableCount = tables.size,
                todoRows = todoRows,
            )
        } finally {
            database.close()
        }
    }

    private fun canonicalRow(cursor: Cursor, columnOrder: List<Int>): String =
        columnOrder.joinToString(separator = "|") { index ->
            "${cursor.columnNames[index]}=${canonicalValue(cursor, index)}"
        }

    private fun canonicalValue(cursor: Cursor, index: Int): String = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> "null"
        Cursor.FIELD_TYPE_INTEGER -> "integer:${cursor.getLong(index)}"
        Cursor.FIELD_TYPE_FLOAT -> "float:${cursor.getDouble(index)}"
        Cursor.FIELD_TYPE_STRING -> {
            val value = cursor.getString(index)
            "text:${value.length}:$value"
        }
        Cursor.FIELD_TYPE_BLOB -> "blob:${sha256(cursor.getBlob(index))}"
        else -> error("Unknown SQLite field type ${cursor.getType(index)}")
    }

    private fun updateDigest(digest: MessageDigest, text: String) {
        digest.update(text.toByteArray(Charsets.UTF_8))
    }

    private fun quoteIdentifier(identifier: String): String =
        "\"${identifier.replace("\"", "\"\"")}\""

    private fun isUserTable(name: String): Boolean {
        val normalized = name.lowercase(Locale.ROOT)
        return normalized != "android_metadata" &&
            !normalized.startsWith("android_") &&
            !normalized.startsWith("room_") &&
            !normalized.startsWith("sqlite_") &&
            !normalized.startsWith("workmanager") &&
            !normalized.startsWith("androidx_")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }

    private data class Snapshot(
        val digest: String,
        val rowCount: Long,
        val tableCount: Int,
        val todoRows: Long,
    )

    private companion object {
        const val DATABASE_NAME = "taskledger.db"
    }
}
