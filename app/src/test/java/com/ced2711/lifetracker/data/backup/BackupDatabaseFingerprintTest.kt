package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.local.BackupDatabaseState
import com.ced2711.lifetracker.data.local.VaultEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackupDatabaseFingerprintTest {
    @Test
    fun `expected fingerprint matches restored semantic state while ciphertext remains version sensitive`() {
        val snapshot = fullBackupSnapshot()
        val attachmentRows = snapshot.attachments.map { it.toEntity("/private/attachments/${it.id}.bin") }
        val vault = snapshot.vaultEntries.single()
        val state = BackupDatabaseState(
            categories = snapshot.categories,
            todoSeries = snapshot.todoSeries,
            todoSeriesSubtasks = snapshot.todoSeriesSubtasks,
            todoOccurrenceExceptions = snapshot.todoOccurrenceExceptions,
            todos = snapshot.todos,
            subtasks = snapshot.subtasks,
            todoReminders = snapshot.todoReminders,
            ledgerSeries = snapshot.ledgerSeries,
            ledgerOccurrenceExceptions = snapshot.ledgerOccurrenceExceptions,
            ledgerEntries = snapshot.ledgerEntries,
            attachments = attachmentRows,
            vaultEntries = listOf(
                VaultEntryEntity(vault.id, 1, ByteArray(12) { 1 }, ByteArray(20) { 2 }, vault.createdAt, vault.updatedAt),
            ),
        )

        assertEquals(expectedDatabaseFingerprint(snapshot, attachmentRows), semanticDatabaseFingerprint(state))
        assertEquals(
            expectedFullDatabaseFingerprint(snapshot, attachmentRows, state.vaultEntries),
            fullDatabaseFingerprint(state),
        )
        assertNotEquals(
            fullDatabaseFingerprint(state),
            fullDatabaseFingerprint(
                state.copy(vaultEntries = state.vaultEntries.map { it.copy(payloadCiphertext = ByteArray(20) { 3 }) }),
            ),
        )
    }

    @Test
    fun `full fingerprint is stable across list ordering`() {
        val snapshot = fullBackupSnapshot()
        val state = BackupDatabaseState(
            snapshot.categories,
            snapshot.todoSeries,
            snapshot.todoSeriesSubtasks,
            snapshot.todoOccurrenceExceptions,
            snapshot.todos,
            snapshot.subtasks,
            snapshot.todoReminders,
            snapshot.ledgerSeries,
            snapshot.ledgerOccurrenceExceptions,
            snapshot.ledgerEntries,
            snapshot.attachments.map { it.toEntity("/private/${it.id}") },
            emptyList(),
        )

        assertEquals(fullDatabaseFingerprint(state), fullDatabaseFingerprint(state.copy(todos = state.todos.reversed())))
    }
}
