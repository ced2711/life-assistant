package com.ced2711.lifetracker.ui.backup

import android.net.Uri
import java.util.Locale

const val TASK_LEDGER_BACKUP_MIME_TYPE = "application/vnd.taskledger.backup"
const val MINIMUM_BACKUP_PASSWORD_LENGTH = 8

/**
 * UI-only projection of an authenticated backup. The data layer must fully decrypt and validate
 * the archive before publishing this preview.
 */
data class BackupRestorePreview(
    val createdAtLabel: String,
    val todoCount: Int,
    val ledgerCount: Int,
    val vaultCount: Int,
    val attachmentCount: Int,
    val attachmentBytes: Long,
    val totalBytes: Long,
    val noteCount: Int = 0,
) {
    init {
        require(createdAtLabel.isNotBlank())
        require(todoCount >= 0)
        require(ledgerCount >= 0)
        require(noteCount >= 0)
        require(vaultCount >= 0)
        require(attachmentCount >= 0)
        require(attachmentBytes >= 0L)
        require(totalBytes >= 0L)
    }
}

enum class BackupRestoreTask {
    NONE,
    EXPORTING,
    VALIDATING_RESTORE,
    COMMITTING_RESTORE,
}

/** Fixed user-facing outcomes prevent raw exceptions, paths, or passwords leaking into the UI. */
enum class BackupRestoreNotice(val message: String) {
    EXPORT_COMPLETE("Encrypted backup saved."),
    RESTORE_COMPLETE("Backup restored."),
    VAULT_AUTHENTICATION_CANCELLED("Vault authentication was cancelled."),
    BACKUP_NOT_READABLE("The selected backup could not be read."),
    WRONG_PASSWORD_OR_MODIFIED("The password is incorrect or the backup was modified."),
    UNSUPPORTED_BACKUP("This backup version is not supported."),
    BACKUP_TOO_LARGE("This backup is too large to process safely."),
    EXPORT_FAILED("The encrypted backup could not be saved."),
    RESTORE_FAILED("Nothing was changed because the backup could not be restored."),
}

data class BackupRestoreUiState(
    val task: BackupRestoreTask = BackupRestoreTask.NONE,
    val restorePreview: BackupRestorePreview? = null,
    val notice: BackupRestoreNotice? = null,
) {
    init {
        require(task == BackupRestoreTask.NONE || restorePreview == null) {
            "A restore preview cannot be shown while an operation is running."
        }
    }

    val isNonInterruptible: Boolean
        get() = task != BackupRestoreTask.NONE
}

/**
 * Boundary between the auxiliary screen and backup/Vault infrastructure.
 *
 * Password arrays are transferred to the receiver. Implementations must overwrite them after the
 * operation and must never persist or log them. [prepareRestore] must authenticate, decrypt, and
 * validate without changing existing app data; only [commitPreparedRestore] may replace data.
 */
interface BackupRestoreActions {
    fun exportBackup(destination: Uri, password: CharArray)

    fun discardExportDestination(destination: Uri)

    fun prepareRestore(source: Uri, password: CharArray)

    /** Available only in the one-off personal migration build. */
    fun prepareIncludedBackup(password: CharArray) {
        password.fill('\u0000')
    }

    fun commitPreparedRestore()

    fun discardPreparedRestore()

    fun acknowledgeNotice()
}

enum class BackupPasswordIssue(val message: String) {
    TOO_SHORT("Use at least $MINIMUM_BACKUP_PASSWORD_LENGTH characters."),
    DOES_NOT_MATCH("Passwords do not match."),
}

fun exportPasswordIssue(password: String, confirmation: String): BackupPasswordIssue? = when {
    password.length < MINIMUM_BACKUP_PASSWORD_LENGTH -> BackupPasswordIssue.TOO_SHORT
    password != confirmation -> BackupPasswordIssue.DOES_NOT_MATCH
    else -> null
}

fun restorePasswordIssue(password: String): BackupPasswordIssue? =
    if (password.length < MINIMUM_BACKUP_PASSWORD_LENGTH) {
        BackupPasswordIssue.TOO_SHORT
    } else {
        null
    }

internal fun shouldBlockBackupExit(
    uiState: BackupRestoreUiState,
    hasLocalSubmission: Boolean,
): Boolean = uiState.isNonInterruptible || hasLocalSubmission

internal fun shouldProtectBackupWindow(
    uiState: BackupRestoreUiState,
    hasPasswordPrompt: Boolean,
    hasLocalSubmission: Boolean,
): Boolean = hasPasswordPrompt ||
    hasLocalSubmission ||
    uiState.isNonInterruptible ||
    uiState.restorePreview != null

fun formatBackupByteCount(bytes: Long): String {
    require(bytes >= 0L)
    if (bytes < 1_024L) return String.format(Locale.US, "%,d B", bytes)

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    do {
        value /= 1_024.0
        unitIndex += 1
    } while (value >= 1_024.0 && unitIndex < units.lastIndex)

    val pattern = if (value >= 100.0 || value % 1.0 == 0.0) "%.0f %s" else "%.1f %s"
    return String.format(Locale.US, pattern, value, units[unitIndex])
}
