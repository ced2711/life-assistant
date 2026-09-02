package com.ced2711.lifetracker.ui.backup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog

private enum class RestoreReviewStep {
    PREVIEW,
    CONFIRM_REPLACEMENT,
    SUBMITTED,
}

/**
 * Hinge-safe auxiliary page. Repository work, Vault authentication, and result mapping are owned by
 * [actions]; this screen only launches the Storage Access Framework and enforces the review flow.
 */
@Composable
fun BackupRestoreScreen(
    uiState: BackupRestoreUiState,
    actions: BackupRestoreActions,
    onSensitiveContentChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    defaultExportFileName: String = "LifeTracker-backup.tlb",
    hasIncludedPersonalBackup: Boolean = false,
) {
    var exportDestination by remember { mutableStateOf<Uri?>(null) }
    var restoreSource by remember { mutableStateOf<Uri?>(null) }
    var includedRestoreRequested by remember { mutableStateOf(false) }
    var pendingSubmission by remember { mutableStateOf<BackupRestoreTask?>(null) }
    var reviewStep by remember { mutableStateOf(RestoreReviewStep.PREVIEW) }
    val snackbarHostState = remember { SnackbarHostState() }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TASK_LEDGER_BACKUP_MIME_TYPE),
    ) { destination ->
        exportDestination = destination
    }
    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { source ->
        restoreSource = source
    }

    LaunchedEffect(uiState.task) {
        if (uiState.task != BackupRestoreTask.NONE) pendingSubmission = null
    }
    LaunchedEffect(uiState.restorePreview) {
        if (uiState.restorePreview == null) reviewStep = RestoreReviewStep.PREVIEW
    }
    LaunchedEffect(uiState.notice) {
        val notice = uiState.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice.message)
        actions.acknowledgeNotice()
    }

    val isLocallySubmitted = pendingSubmission != null ||
        reviewStep == RestoreReviewStep.SUBMITTED
    val hasPasswordPrompt = exportDestination != null ||
        restoreSource != null ||
        includedRestoreRequested
    val isBlocked = shouldBlockBackupExit(uiState, isLocallySubmitted)
    val isSensitive = shouldProtectBackupWindow(
        uiState = uiState,
        hasPasswordPrompt = hasPasswordPrompt,
        hasLocalSubmission = isLocallySubmitted,
    )

    LaunchedEffect(isSensitive) {
        onSensitiveContentChanged(isSensitive)
    }
    DisposableEffect(Unit) {
        onDispose {
            exportDestination?.let(actions::discardExportDestination)
            onSensitiveContentChanged(false)
        }
    }
    BackHandler(enabled = isBlocked) { }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OfflineEncryptionCard()
                Spacer(Modifier.height(16.dp))
                if (hasIncludedPersonalBackup) {
                    IncludedPersonalBackupCard(
                        enabled = !isBlocked && uiState.restorePreview == null,
                        onRestore = { includedRestoreRequested = true },
                    )
                    Spacer(Modifier.height(16.dp))
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useTwoColumns = maxWidth >= 680.dp
                    if (useTwoColumns) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ExportCard(
                                enabled = !isBlocked && uiState.restorePreview == null,
                                modifier = Modifier.weight(1f),
                                onChooseDestination = {
                                    createBackupLauncher.launch(ensureBackupExtension(defaultExportFileName))
                                },
                            )
                            RestoreCard(
                                enabled = !isBlocked && uiState.restorePreview == null,
                                modifier = Modifier.weight(1f),
                                onChooseSource = {
                                    openBackupLauncher.launch(
                                        arrayOf(
                                            TASK_LEDGER_BACKUP_MIME_TYPE,
                                            "application/octet-stream",
                                        ),
                                    )
                                },
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ExportCard(
                                enabled = !isBlocked && uiState.restorePreview == null,
                                modifier = Modifier.fillMaxWidth(),
                                onChooseDestination = {
                                    createBackupLauncher.launch(ensureBackupExtension(defaultExportFileName))
                                },
                            )
                            RestoreCard(
                                enabled = !isBlocked && uiState.restorePreview == null,
                                modifier = Modifier.fillMaxWidth(),
                                onChooseSource = {
                                    openBackupLauncher.launch(
                                        arrayOf(
                                            TASK_LEDGER_BACKUP_MIME_TYPE,
                                            "application/octet-stream",
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    exportDestination?.let { destination ->
        ExportPasswordDialog(
            onDismiss = {
                exportDestination = null
                actions.discardExportDestination(destination)
            },
            onExport = { password ->
                exportDestination = null
                pendingSubmission = BackupRestoreTask.EXPORTING
                actions.exportBackup(destination, password)
            },
        )
    }

    restoreSource?.let { source ->
        RestorePasswordDialog(
            onDismiss = { restoreSource = null },
            onOpen = { password ->
                restoreSource = null
                pendingSubmission = BackupRestoreTask.VALIDATING_RESTORE
                actions.prepareRestore(source, password)
            },
        )
    }

    if (includedRestoreRequested) {
        RestorePasswordDialog(
            onDismiss = { includedRestoreRequested = false },
            onOpen = { password ->
                includedRestoreRequested = false
                pendingSubmission = BackupRestoreTask.VALIDATING_RESTORE
                actions.prepareIncludedBackup(password)
            },
        )
    }

    val preview = uiState.restorePreview
    if (preview != null && reviewStep == RestoreReviewStep.PREVIEW) {
        RestorePreviewDialog(
            preview = preview,
            onCancel = {
                actions.discardPreparedRestore()
            },
            onContinue = {
                reviewStep = RestoreReviewStep.CONFIRM_REPLACEMENT
            },
        )
    }
    if (preview != null && reviewStep == RestoreReviewStep.CONFIRM_REPLACEMENT) {
        RestoreReplacementConfirmationDialog(
            onBackToPreview = { reviewStep = RestoreReviewStep.PREVIEW },
            onRestore = {
                reviewStep = RestoreReviewStep.SUBMITTED
                pendingSubmission = BackupRestoreTask.COMMITTING_RESTORE
                actions.commitPreparedRestore()
            },
        )
    }

    val blockingTask = uiState.task.takeUnless { it == BackupRestoreTask.NONE }
        ?: pendingSubmission
    if (blockingTask != null) {
        BlockingBackupDialog(blockingTask)
    }
}

@Composable
private fun IncludedPersonalBackupCard(
    enabled: Boolean,
    onRestore: () -> Unit,
) {
    BackupActionCard(
        title = "Restore your previous Life Tracker data",
        description = "This personal migration build contains your original encrypted backup. " +
            "Enter its password to validate it, review the contents, and restore. The password " +
            "and decrypted data are not built into the app.",
        icon = {
            Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(28.dp))
        },
        action = {
            Button(
                enabled = enabled,
                onClick = onRestore,
            ) {
                Text("Restore included backup")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OfflineEncryptionCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Private, offline backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Your todos, ledger, notes, settings, attachments, and Vault are encrypted " +
                        "into one local .tlb file. Nothing is uploaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "The backup password is never saved and cannot be recovered.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ExportCard(
    enabled: Boolean,
    modifier: Modifier,
    onChooseDestination: () -> Unit,
) {
    BackupActionCard(
        title = "Export encrypted backup",
        description = "Choose where to create a .tlb file, then protect it with a password of at " +
            "least 8 characters. Exporting the Vault may require device authentication.",
        icon = {
            Icon(Icons.Outlined.Upload, null, modifier = Modifier.size(28.dp))
        },
        action = {
            Button(
                enabled = enabled,
                onClick = onChooseDestination,
            ) {
                Text("Choose export location")
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun RestoreCard(
    enabled: Boolean,
    modifier: Modifier,
    onChooseSource: () -> Unit,
) {
    BackupActionCard(
        title = "Restore from backup",
        description = "Open a .tlb file and enter its password. The backup is decrypted and fully " +
            "validated before you see a required preview or any data is changed.",
        icon = {
            Icon(Icons.Outlined.Download, null, modifier = Modifier.size(28.dp))
        },
        action = {
            FilledTonalButton(
                enabled = enabled,
                onClick = onChooseSource,
            ) {
                Text("Choose backup file")
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun BackupActionCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(
                    modifier = Modifier.padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action()
        }
    }
}

@Composable
private fun ExportPasswordDialog(
    onDismiss: () -> Unit,
    onExport: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val issue = if (submitted) exportPasswordIssue(password, confirmation) else null
    val dismiss = {
        password = ""
        confirmation = ""
        onDismiss()
    }

    HingeSafeAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Encrypt backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Use at least 8 characters. This password is not saved and cannot be recovered.",
                )
                PasswordField(
                    value = password,
                    label = "Backup password",
                    visible = showPassword,
                    isError = issue == BackupPasswordIssue.TOO_SHORT,
                    onValueChange = {
                        password = it
                        submitted = false
                    },
                    onVisibilityChange = { showPassword = !showPassword },
                )
                PasswordField(
                    value = confirmation,
                    label = "Confirm password",
                    visible = showPassword,
                    isError = issue == BackupPasswordIssue.DOES_NOT_MATCH,
                    onValueChange = {
                        confirmation = it
                        submitted = false
                    },
                    onVisibilityChange = { showPassword = !showPassword },
                )
                if (issue != null) {
                    Text(
                        text = issue.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitted = true
                    if (exportPasswordIssue(password, confirmation) == null) {
                        val transferredPassword = password.toCharArray()
                        password = ""
                        confirmation = ""
                        onExport(transferredPassword)
                    }
                },
            ) {
                Text("Export")
            }
        },
    )
}

@Composable
private fun RestorePasswordDialog(
    onDismiss: () -> Unit,
    onOpen: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val issue = if (submitted) restorePasswordIssue(password) else null
    val dismiss = {
        password = ""
        onDismiss()
    }

    HingeSafeAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Unlock backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the password used when this backup was created. It is not saved.",
                )
                PasswordField(
                    value = password,
                    label = "Backup password",
                    visible = showPassword,
                    isError = issue != null,
                    onValueChange = {
                        password = it
                        submitted = false
                    },
                    onVisibilityChange = { showPassword = !showPassword },
                )
                if (issue != null) {
                    Text(
                        text = issue.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitted = true
                    if (restorePasswordIssue(password) == null) {
                        val transferredPassword = password.toCharArray()
                        password = ""
                        onOpen(transferredPassword)
                    }
                },
            ) {
                Text("Decrypt & review")
            }
        },
    )
}

@Composable
private fun PasswordField(
    value: String,
    label: String,
    visible: Boolean,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    onVisibilityChange: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        trailingIcon = {
            IconButton(onClick = onVisibilityChange) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
    )
}

@Composable
private fun RestorePreviewDialog(
    preview: BackupRestorePreview,
    onCancel: () -> Unit,
    onContinue: () -> Unit,
) {
    HingeSafeAlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Review backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The backup was decrypted and validated. Review it before continuing.",
                )
                HorizontalDivider()
                PreviewValue("Created", preview.createdAtLabel)
                PreviewValue("Todos", preview.todoCount.toString())
                PreviewValue("Ledger entries", preview.ledgerCount.toString())
                PreviewValue("Notes", preview.noteCount.toString())
                PreviewValue("Vault entries", preview.vaultCount.toString())
                PreviewValue(
                    "Attachments",
                    "${preview.attachmentCount} (${formatBackupByteCount(preview.attachmentBytes)})",
                )
                PreviewValue("Total backup size", formatBackupByteCount(preview.totalBytes))
                HorizontalDivider()
                Text(
                    text = "Continuing does not restore yet. You will see a final replacement warning.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel restore") }
        },
        confirmButton = {
            Button(onClick = onContinue) { Text("Continue") }
        },
    )
}

@Composable
private fun PreviewValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RestoreReplacementConfirmationDialog(
    onBackToPreview: () -> Unit,
    onRestore: () -> Unit,
) {
    HingeSafeAlertDialog(
        onDismissRequest = onBackToPreview,
        title = { Text("Replace all local data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This is a complete replacement, not a merge. It permanently replaces your " +
                        "current todos, ledger, notes, settings, attachments, and Vault with the backup.",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Once restore starts, it cannot be cancelled. The existing data is left " +
                        "unchanged if validation or preparation fails.",
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onBackToPreview) { Text("Back to preview") }
        },
        confirmButton = {
            Button(onClick = onRestore) { Text("Replace & restore") }
        },
    )
}

@Composable
private fun BlockingBackupDialog(task: BackupRestoreTask) {
    val (title, message) = when (task) {
        BackupRestoreTask.EXPORTING ->
            "Creating encrypted backup" to "Keep Life Tracker open while the file is written."
        BackupRestoreTask.VALIDATING_RESTORE ->
            "Checking backup" to "Decrypting and validating everything before making changes."
        BackupRestoreTask.COMMITTING_RESTORE ->
            "Restoring backup" to "Replacing local data. This cannot be cancelled."
        BackupRestoreTask.NONE -> return
    }
    HingeSafeAlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
        icon = { CircularProgressIndicator(modifier = Modifier.size(28.dp)) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { },
    )
}

internal fun ensureBackupExtension(fileName: String): String {
    val trimmed = fileName.trim().ifEmpty { "LifeTracker-backup" }
    return if (trimmed.endsWith(".tlb", ignoreCase = true)) trimmed else "$trimmed.tlb"
}
