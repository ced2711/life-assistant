package com.ced2711.lifetracker.ui.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreContractTest {
    @Test
    fun exportPasswordRequiresEightCharactersAndMatchingConfirmation() {
        assertEquals(
            BackupPasswordIssue.TOO_SHORT,
            exportPasswordIssue("1234567", "1234567"),
        )
        assertEquals(
            BackupPasswordIssue.DOES_NOT_MATCH,
            exportPasswordIssue("abcdefgh", "87654321"),
        )
        assertNull(exportPasswordIssue("any language 密码", "any language 密码"))
    }

    @Test
    fun restorePasswordRequiresEightCharacters() {
        assertEquals(BackupPasswordIssue.TOO_SHORT, restorePasswordIssue("short"))
        assertNull(restorePasswordIssue("long enough"))
    }

    @Test
    fun onlyRunningTasksAreNonInterruptible() {
        assertFalse(BackupRestoreUiState().isNonInterruptible)
        assertTrue(
            BackupRestoreUiState(task = BackupRestoreTask.EXPORTING).isNonInterruptible,
        )
        assertTrue(
            BackupRestoreUiState(task = BackupRestoreTask.VALIDATING_RESTORE).isNonInterruptible,
        )
        assertTrue(
            BackupRestoreUiState(task = BackupRestoreTask.COMMITTING_RESTORE).isNonInterruptible,
        )
    }

    @Test
    fun runningOrLocallySubmittedWorkBlocksExit() {
        assertFalse(shouldBlockBackupExit(BackupRestoreUiState(), hasLocalSubmission = false))
        assertTrue(shouldBlockBackupExit(BackupRestoreUiState(), hasLocalSubmission = true))
        assertTrue(
            shouldBlockBackupExit(
                BackupRestoreUiState(task = BackupRestoreTask.VALIDATING_RESTORE),
                hasLocalSubmission = false,
            ),
        )
    }

    @Test
    fun passwordPreviewAndBusyStagesProtectTheWindow() {
        val idle = BackupRestoreUiState()
        assertFalse(shouldProtectBackupWindow(idle, false, false))
        assertTrue(shouldProtectBackupWindow(idle, true, false))
        assertTrue(shouldProtectBackupWindow(idle, false, true))
        assertTrue(
            shouldProtectBackupWindow(
                BackupRestoreUiState(restorePreview = preview()),
                hasPasswordPrompt = false,
                hasLocalSubmission = false,
            ),
        )
        assertTrue(
            shouldProtectBackupWindow(
                BackupRestoreUiState(task = BackupRestoreTask.EXPORTING),
                hasPasswordPrompt = false,
                hasLocalSubmission = false,
            ),
        )
    }

    @Test
    fun previewCannotCoexistWithRunningOperation() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupRestoreUiState(
                task = BackupRestoreTask.COMMITTING_RESTORE,
                restorePreview = preview(),
            )
        }
    }

    @Test
    fun byteCountsStayCompactAndUseBinaryUnits() {
        assertEquals("0 B", formatBackupByteCount(0L))
        assertEquals("1,023 B", formatBackupByteCount(1_023L))
        assertEquals("1 KB", formatBackupByteCount(1_024L))
        assertEquals("1.5 KB", formatBackupByteCount(1_536L))
        assertEquals("128 MB", formatBackupByteCount(128L * 1_024L * 1_024L))
        assertEquals("1 TB", formatBackupByteCount(1_024L * 1_024L * 1_024L * 1_024L))
    }

    @Test
    fun exportFileNameAlwaysUsesTaskLedgerExtension() {
        assertEquals("TaskLedger-backup.tlb", ensureBackupExtension("TaskLedger-backup"))
        assertEquals("archive.TLB", ensureBackupExtension(" archive.TLB "))
        assertEquals("LifeAssistant-backup.tlb", ensureBackupExtension("   "))
    }

    @Test
    fun previewRejectsImpossibleCountsAndSizes() {
        assertThrows(IllegalArgumentException::class.java) {
            preview().copy(todoCount = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            preview().copy(totalBytes = -1L)
        }
    }

    private fun preview() = BackupRestorePreview(
        createdAtLabel = "Aug 20, 2026 at 3:45 PM",
        todoCount = 12,
        ledgerCount = 8,
        vaultCount = 2,
        attachmentCount = 3,
        attachmentBytes = 1_024L,
        totalBytes = 2_048L,
    )
}
