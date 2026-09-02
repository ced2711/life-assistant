package com.ced2711.lifetracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        TodoSeriesEntity::class,
        TodoSeriesSubtaskEntity::class,
        TodoOccurrenceExceptionEntity::class,
        TodoEntity::class,
        SubtaskEntity::class,
        TodoReminderEntity::class,
        LedgerSeriesEntity::class,
        LedgerOccurrenceExceptionEntity::class,
        LedgerEntryEntity::class,
        AttachmentEntity::class,
        VaultEntryEntity::class,
        RestoreCommitEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TaskLedgerDatabase : RoomDatabase() {
    abstract fun dao(): TaskLedgerDao
    abstract fun vaultDao(): VaultDao
    abstract fun backupDao(): BackupDao

    companion object {
        @Volatile private var instance: TaskLedgerDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `todo_series_subtasks` (
                        `seriesId` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        PRIMARY KEY(`seriesId`, `sortOrder`),
                        FOREIGN KEY(`seriesId`) REFERENCES `todo_series`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `todo_occurrence_exceptions` (
                        `seriesId` INTEGER NOT NULL,
                        `occurrenceEpochDay` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`seriesId`, `occurrenceEpochDay`),
                        FOREIGN KEY(`seriesId`) REFERENCES `todo_series`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ledger_occurrence_exceptions` (
                        `seriesId` INTEGER NOT NULL,
                        `occurrenceEpochDay` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`seriesId`, `occurrenceEpochDay`),
                        FOREIGN KEY(`seriesId`) REFERENCES `ledger_series`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )

                // Preserve the template of existing v1 series from their earliest materialized task.
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `todo_series_subtasks` (`seriesId`, `sortOrder`, `description`)
                    SELECT `todo`.`seriesId`, `subtask`.`sortOrder`, MIN(`subtask`.`description`)
                    FROM `subtasks` AS `subtask`
                    INNER JOIN `todos` AS `todo` ON `todo`.`id` = `subtask`.`todoId`
                    WHERE `todo`.`seriesId` IS NOT NULL
                      AND `todo`.`occurrenceEpochDay` = (
                          SELECT MIN(`candidate`.`occurrenceEpochDay`)
                          FROM `todos` AS `candidate`
                          WHERE `candidate`.`seriesId` = `todo`.`seriesId`
                      )
                    GROUP BY `todo`.`seriesId`, `subtask`.`sortOrder`
                    """.trimIndent(),
                )

                // A v1 soft-deleted occurrence already represents an explicit user exception.
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `todo_occurrence_exceptions`
                        (`seriesId`, `occurrenceEpochDay`, `createdAt`)
                    SELECT `seriesId`, `occurrenceEpochDay`, `deletedAt`
                    FROM `todos`
                    WHERE `seriesId` IS NOT NULL
                      AND `occurrenceEpochDay` IS NOT NULL
                      AND `deletedAt` IS NOT NULL
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `ledger_occurrence_exceptions`
                        (`seriesId`, `occurrenceEpochDay`, `createdAt`)
                    SELECT `seriesId`, `occurrenceEpochDay`, `deletedAt`
                    FROM `ledger_entries`
                    WHERE `seriesId` IS NOT NULL
                      AND `occurrenceEpochDay` IS NOT NULL
                      AND `deletedAt` IS NOT NULL
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vault_entries` (
                        `id` TEXT NOT NULL,
                        `formatVersion` INTEGER NOT NULL,
                        `payloadIv` BLOB NOT NULL,
                        `payloadCiphertext` BLOB NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_vault_entries_payloadIv` " +
                        "ON `vault_entries` (`payloadIv`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vault_entries_updatedAt` " +
                        "ON `vault_entries` (`updatedAt`)",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `todos` ADD COLUMN `clientOperationToken` TEXT")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_todos_clientOperationToken` " +
                        "ON `todos` (`clientOperationToken`)",
                )
                database.execSQL(
                    "ALTER TABLE `ledger_entries` ADD COLUMN `clientOperationToken` TEXT",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_entries_clientOperationToken` " +
                        "ON `ledger_entries` (`clientOperationToken`)",
                )
                database.execSQL(
                    "ALTER TABLE `ledger_series` ADD COLUMN `clientOperationToken` TEXT",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_series_clientOperationToken` " +
                        "ON `ledger_series` (`clientOperationToken`)",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `restore_commit_state` (
                        `singletonId` INTEGER NOT NULL,
                        `restoreToken` TEXT NOT NULL,
                        PRIMARY KEY(`singletonId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun getInstance(context: Context): TaskLedgerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TaskLedgerDatabase::class.java,
                "taskledger.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { instance = it }
        }
    }
}
