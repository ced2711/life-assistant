package com.ced2711.lifetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.backup.InvalidBackupException
import com.ced2711.lifetracker.data.backup.StagedAttachments
import com.ced2711.lifetracker.data.backup.validate
import com.ced2711.lifetracker.data.vault.VaultBackupCipher
import com.ced2711.lifetracker.data.vault.VaultSession
import com.ced2711.lifetracker.domain.model.VaultEntry
import java.util.UUID

@Dao
interface BackupDao {
    @Query("SELECT * FROM categories ORDER BY id") suspend fun backupCategories(): List<CategoryEntity>
    @Query("SELECT * FROM todo_series ORDER BY id") suspend fun backupTodoSeries(): List<TodoSeriesEntity>
    @Query("SELECT * FROM todo_series_subtasks ORDER BY seriesId, sortOrder") suspend fun backupTodoSeriesSubtasks(): List<TodoSeriesSubtaskEntity>
    @Query("SELECT * FROM todo_occurrence_exceptions ORDER BY seriesId, occurrenceEpochDay") suspend fun backupTodoExceptions(): List<TodoOccurrenceExceptionEntity>
    @Query("SELECT * FROM todos ORDER BY id") suspend fun backupTodos(): List<TodoEntity>
    @Query("SELECT * FROM subtasks ORDER BY id") suspend fun backupSubtasks(): List<SubtaskEntity>
    @Query("SELECT * FROM todo_reminders ORDER BY id") suspend fun backupTodoReminders(): List<TodoReminderEntity>
    @Query("SELECT * FROM ledger_series ORDER BY id") suspend fun backupLedgerSeries(): List<LedgerSeriesEntity>
    @Query("SELECT * FROM ledger_occurrence_exceptions ORDER BY seriesId, occurrenceEpochDay") suspend fun backupLedgerExceptions(): List<LedgerOccurrenceExceptionEntity>
    @Query("SELECT * FROM ledger_entries ORDER BY id") suspend fun backupLedgerEntries(): List<LedgerEntryEntity>
    @Query("SELECT * FROM attachments ORDER BY id") suspend fun backupAttachments(): List<AttachmentEntity>
    @Query("SELECT id FROM vault_entries ORDER BY id") suspend fun backupVaultIds(): List<String>
    @Query("SELECT * FROM vault_entries ORDER BY id") suspend fun backupVaultRows(): List<VaultEntryEntity>
    @Query("SELECT restoreToken FROM restore_commit_state WHERE singletonId = 1")
    suspend fun restoreCommitToken(): String?

    @Query("DELETE FROM restore_commit_state WHERE singletonId = 1 AND restoreToken = :restoreToken")
    suspend fun clearRestoreCommitToken(restoreToken: String): Int

    @Query("DELETE FROM restore_commit_state")
    suspend fun clearStaleRestoreCommitToken(): Int

    /** One transactionally consistent view used by export stability checks and restore recovery. */
    @Transaction
    suspend fun backupState(): BackupDatabaseState = BackupDatabaseState(
        categories = backupCategories(),
        todoSeries = backupTodoSeries(),
        todoSeriesSubtasks = backupTodoSeriesSubtasks(),
        todoOccurrenceExceptions = backupTodoExceptions(),
        todos = backupTodos(),
        subtasks = backupSubtasks(),
        todoReminders = backupTodoReminders(),
        ledgerSeries = backupLedgerSeries(),
        ledgerOccurrenceExceptions = backupLedgerExceptions(),
        ledgerEntries = backupLedgerEntries(),
        attachments = backupAttachments(),
        vaultEntries = backupVaultRows(),
    )

    /** Validation, vault re-encryption, deletes, and inserts all complete before Room commits. */
    @Transaction
    suspend fun replaceSnapshot(
        snapshot: BackupSnapshot,
        vaultSession: VaultSession?,
        stagedAttachments: StagedAttachments,
        restoreToken: String,
    ) {
        requireCanonicalRestoreToken(restoreToken)
        snapshot.validate()
        // Finish all fallible plaintext/vault work before the first live-table mutation.
        val encryptedVault = if (snapshot.vaultEntries.isEmpty()) {
            emptyList()
        } else {
            val session = requireNotNull(vaultSession) { "An unlocked vault session is required to restore vault entries." }
            snapshot.vaultEntries.map { VaultBackupCipher.encrypt(it, session) }
        }

        replaceSnapshot(snapshot, encryptedVault, stagedAttachments, restoreToken)
    }

    /** Uses the exact pre-encrypted Vault rows validated before the durable restore commit. */
    @Transaction
    suspend fun replaceSnapshot(
        snapshot: BackupSnapshot,
        encryptedVault: List<VaultEntryEntity>,
        stagedAttachments: StagedAttachments,
        restoreToken: String,
    ) {
        requireCanonicalRestoreToken(restoreToken)
        snapshot.validate()
        val attachmentRows = stagedAttachments.entities(snapshot)
        val expectedVault = snapshot.vaultEntries.associateBy { it.id }
        if (
            encryptedVault.map { it.id }.toSet() != expectedVault.keys ||
            encryptedVault.any { row ->
                expectedVault[row.id]?.let { it.createdAt != row.createdAt || it.updatedAt != row.updatedAt } != false
            }
        ) {
            throw InvalidBackupException("Prepared vault entries do not match the restore snapshot.")
        }

        deleteAllAttachments()
        deleteAllTodoReminders()
        deleteAllSubtasks()
        deleteAllTodos()
        deleteAllTodoExceptions()
        deleteAllTodoSeriesSubtasks()
        deleteAllTodoSeries()
        deleteAllCategories()
        deleteAllLedgerEntries()
        deleteAllLedgerExceptions()
        deleteAllLedgerSeries()
        deleteAllVaultEntries()

        insertCategories(topologicallySortedCategories(snapshot.categories))
        insertTodoSeries(snapshot.todoSeries)
        insertTodoSeriesSubtasks(snapshot.todoSeriesSubtasks)
        insertTodoExceptions(snapshot.todoOccurrenceExceptions)
        insertTodos(snapshot.todos)
        insertSubtasks(snapshot.subtasks)
        insertTodoReminders(snapshot.todoReminders)
        insertLedgerSeries(snapshot.ledgerSeries)
        insertLedgerExceptions(snapshot.ledgerOccurrenceExceptions)
        insertLedgerEntries(snapshot.ledgerEntries)
        insertAttachments(attachmentRows)
        insertVaultEntries(encryptedVault)
        writeRestoreCommit(RestoreCommitEntity(restoreToken = restoreToken))
    }

    @Query("DELETE FROM attachments") suspend fun deleteAllAttachments()
    @Query("DELETE FROM todo_reminders") suspend fun deleteAllTodoReminders()
    @Query("DELETE FROM subtasks") suspend fun deleteAllSubtasks()
    @Query("DELETE FROM todos") suspend fun deleteAllTodos()
    @Query("DELETE FROM todo_occurrence_exceptions") suspend fun deleteAllTodoExceptions()
    @Query("DELETE FROM todo_series_subtasks") suspend fun deleteAllTodoSeriesSubtasks()
    @Query("DELETE FROM todo_series") suspend fun deleteAllTodoSeries()
    @Query("DELETE FROM categories") suspend fun deleteAllCategories()
    @Query("DELETE FROM ledger_entries") suspend fun deleteAllLedgerEntries()
    @Query("DELETE FROM ledger_occurrence_exceptions") suspend fun deleteAllLedgerExceptions()
    @Query("DELETE FROM ledger_series") suspend fun deleteAllLedgerSeries()
    @Query("DELETE FROM vault_entries") suspend fun deleteAllVaultEntries()

    @Insert suspend fun insertCategories(values: List<CategoryEntity>)
    @Insert suspend fun insertTodoSeries(values: List<TodoSeriesEntity>)
    @Insert suspend fun insertTodoSeriesSubtasks(values: List<TodoSeriesSubtaskEntity>)
    @Insert suspend fun insertTodoExceptions(values: List<TodoOccurrenceExceptionEntity>)
    @Insert suspend fun insertTodos(values: List<TodoEntity>)
    @Insert suspend fun insertSubtasks(values: List<SubtaskEntity>)
    @Insert suspend fun insertTodoReminders(values: List<TodoReminderEntity>)
    @Insert suspend fun insertLedgerSeries(values: List<LedgerSeriesEntity>)
    @Insert suspend fun insertLedgerExceptions(values: List<LedgerOccurrenceExceptionEntity>)
    @Insert suspend fun insertLedgerEntries(values: List<LedgerEntryEntity>)
    @Insert suspend fun insertAttachments(values: List<AttachmentEntity>)
    @Insert suspend fun insertVaultEntries(values: List<VaultEntryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun writeRestoreCommit(value: RestoreCommitEntity)
}

data class BackupDatabaseState(
    val categories: List<CategoryEntity>,
    val todoSeries: List<TodoSeriesEntity>,
    val todoSeriesSubtasks: List<TodoSeriesSubtaskEntity>,
    val todoOccurrenceExceptions: List<TodoOccurrenceExceptionEntity>,
    val todos: List<TodoEntity>,
    val subtasks: List<SubtaskEntity>,
    val todoReminders: List<TodoReminderEntity>,
    val ledgerSeries: List<LedgerSeriesEntity>,
    val ledgerOccurrenceExceptions: List<LedgerOccurrenceExceptionEntity>,
    val ledgerEntries: List<LedgerEntryEntity>,
    val attachments: List<AttachmentEntity>,
    val vaultEntries: List<VaultEntryEntity>,
)

private fun topologicallySortedCategories(values: List<CategoryEntity>): List<CategoryEntity> {
    val byId = values.associateBy(CategoryEntity::id)
    val children = HashMap<Long, MutableList<CategoryEntity>>(values.size)
    val roots = ArrayDeque<CategoryEntity>()
    values.forEach { category ->
        if (category.parentId == null) roots.addLast(category)
        else children.getOrPut(category.parentId) { ArrayList() }.add(category)
    }
    val result = ArrayList<CategoryEntity>(values.size)
    while (roots.isNotEmpty()) {
        val category = roots.removeFirst()
        result += category
        children[category.id].orEmpty().forEach { child ->
            roots.addLast(child)
        }
    }
    if (result.size != byId.size) throw InvalidBackupException("Category hierarchy cannot be restored.")
    return result
}

internal fun requireCanonicalRestoreToken(value: String) {
    val parsed = runCatching { UUID.fromString(value) }.getOrNull()
    require(parsed != null && parsed.toString() == value) { "Restore token must be a canonical UUID." }
}
