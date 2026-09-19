package com.ced2711.lifetracker.ui.backup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.domain.model.UiLanguage
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.data.cloud.CloudSyncAttention
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    cloudViewModel: CloudSyncViewModel,
    onSensitiveContentChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    defaultExportFileName: String = "LifeAssistant-backup.tlb",
    hasIncludedPersonalBackup: Boolean = false,
) {
    var exportDestination by remember { mutableStateOf<Uri?>(null) }
    var restoreSource by remember { mutableStateOf<Uri?>(null) }
    var includedRestoreRequested by remember { mutableStateOf(false) }
    var pendingSubmission by remember { mutableStateOf<BackupRestoreTask?>(null) }
    var reviewStep by remember { mutableStateOf(RestoreReviewStep.PREVIEW) }
    var connectCloudRequested by remember { mutableStateOf(false) }
    var recoveryFileName by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiLanguage = LocalUiLanguage.current
    val cloudState by cloudViewModel.uiState.collectAsStateWithLifecycle()
    val cloudConsent by cloudViewModel.consentRequest.collectAsStateWithLifecycle()

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
    val cloudConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> cloudViewModel.completeConsent(result.data, result.resultCode) }
    val exportRecoveryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TASK_LEDGER_BACKUP_MIME_TYPE),
    ) { destination ->
        val fileName = recoveryFileName
        recoveryFileName = null
        if (destination != null && fileName != null) cloudViewModel.exportRecovery(fileName, destination)
    }

    LaunchedEffect(cloudConsent?.id) {
        val request = cloudConsent ?: return@LaunchedEffect
        if (!cloudViewModel.markConsentDispatched(request.id)) return@LaunchedEffect
        try {
            cloudConsentLauncher.launch(IntentSenderRequest.Builder(request.pendingIntent).build())
        } catch (error: Throwable) {
            cloudViewModel.consentLaunchFailed(request.id, error)
        }
    }

    LaunchedEffect(uiState.task) {
        if (uiState.task != BackupRestoreTask.NONE) pendingSubmission = null
    }
    LaunchedEffect(uiState.restorePreview) {
        if (uiState.restorePreview == null) reviewStep = RestoreReviewStep.PREVIEW
    }
    LaunchedEffect(uiState.notice, uiLanguage) {
        val notice = uiState.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(translateUiText(notice.message, uiLanguage))
        actions.acknowledgeNotice()
    }
    LaunchedEffect(cloudState.message, uiLanguage) {
        val message = cloudState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(translateUiText(message, uiLanguage))
        cloudViewModel.acknowledgeMessage()
    }

    val isLocallySubmitted = pendingSubmission != null ||
        reviewStep == RestoreReviewStep.SUBMITTED
    val hasPasswordPrompt = exportDestination != null ||
        restoreSource != null ||
        includedRestoreRequested ||
        connectCloudRequested
    val isBlocked = shouldBlockBackupExit(uiState, isLocallySubmitted) || cloudState.busy
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
                CloudSyncCard(
                    state = cloudState,
                    onConnect = { connectCloudRequested = true },
                    onSync = { cloudViewModel.syncNow() },
                    onAutomaticChange = cloudViewModel::setAutomaticSync,
                    onReconnect = cloudViewModel::reconnect,
                    onDisconnect = cloudViewModel::disconnect,
                )
                Spacer(Modifier.height(16.dp))
                if (cloudState.recoveryFiles.isNotEmpty()) {
                    CloudRecoveryCard(
                        files = cloudState.recoveryFiles,
                        enabled = !isBlocked && uiState.restorePreview == null,
                        onExport = { name ->
                            recoveryFileName = name
                            exportRecoveryLauncher.launch(name)
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                }
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

    if (connectCloudRequested) {
        CloudSyncPasswordDialog(
            onDismiss = { connectCloudRequested = false },
            onConnect = { password ->
                connectCloudRequested = false
                cloudViewModel.connect(password)
            },
        )
    }

    cloudState.conflict?.let {
        CloudConflictDialog(
            onKeepLocal = { cloudViewModel.syncNow(ConflictResolution.KEEP_LOCAL) },
            onUseCloud = { cloudViewModel.syncNow(ConflictResolution.USE_CLOUD) },
            onDismiss = cloudViewModel::dismissConflict,
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
private fun CloudSyncCard(
    state: CloudSyncUiState,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onAutomaticChange: (Boolean) -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Outlined.Cloud, null, modifier = Modifier.padding(10.dp).size(28.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        localizedText("Google Drive sync"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        localizedText(if (state.connected) "Connected" else "Not connected"),
                        color = if (state.connected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Text(
                localizedText(
                    "Sync password-encrypted snapshots through Life Assistant's private app folder. " +
                        "The app cannot see other files in your Google Drive.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                localizedText(
                    "Each upload creates a new encrypted version. Previous versions are kept; " +
                        "conflicts pause sync.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                localizedText(
                    "Google Drive is optional. Life Assistant works offline by default; enable Drive " +
                        "only when you want encrypted backups shared between Android and Windows.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                localizedText(
                    "Setup requires Drive API access, package com.ced2711.lifetracker, and the " +
                        "release signing SHA-1 listed in the setup guide. Android and Windows must " +
                        "use the same Google Cloud project and account.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.connectionError?.let { error ->
                Text(
                    localizedText(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.connected) {
                Text(
                    localizedText(
                        "When Vault contains entries, background sync pauses until you unlock it " +
                            "in Life Assistant. This keeps Vault keys protected by Android.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(localizedText("Automatic sync"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            localizedText("Runs periodically when a network is available"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.automaticSync,
                        enabled = !state.busy,
                        onCheckedChange = onAutomaticChange,
                    )
                }
                state.lastSyncAt?.let { timestamp ->
                    Text(
                        localizedText(
                            "Last sync: ${formatCloudSyncTime(timestamp, LocalUiLanguage.current)}",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.attention?.let { attention ->
                    val text = when (attention) {
                        CloudSyncAttention.CONFLICT ->
                            "Cloud changes need review. Sync now to choose which version to keep."
                        CloudSyncAttention.VAULT_UNLOCK ->
                            "Automatic sync is waiting for Vault authentication."
                        CloudSyncAttention.GOOGLE_CONSENT ->
                            "Google Drive permission needs to be renewed."
                        CloudSyncAttention.FAILED ->
                            "The last automatic sync failed. Try syncing again."
                    }
                    Text(
                        localizedText(text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !state.busy,
                        onClick = if (state.attention == CloudSyncAttention.GOOGLE_CONSENT) {
                            onReconnect
                        } else {
                            onSync
                        },
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            localizedText(
                                when {
                                    state.busy -> "Syncing…"
                                    state.attention == CloudSyncAttention.GOOGLE_CONSENT -> "Reconnect"
                                    else -> "Sync now"
                                },
                            ),
                        )
                    }
                    TextButton(enabled = !state.busy, onClick = onDisconnect) {
                        Text(localizedText("Disconnect"))
                    }
                }
            } else {
                Button(enabled = !state.busy, onClick = onConnect) {
                    Text(localizedText(if (state.busy) "Connecting…" else "Connect Google Drive"))
                }
            }
        }
    }
}

@Composable
private fun CloudRecoveryCard(files: List<String>, enabled: Boolean, onExport: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(localizedText("Local sync recovery"), style = MaterialTheme.typography.titleMedium)
            Text(
                localizedText("Before using a cloud version, Life Assistant keeps an encrypted local recovery copy. Export a copy and use Restore backup to recover it with its original sync password. Copies are not deleted automatically."),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(localizedText(if (expanded) "Hide recovery copies" else "Show recovery copies"))
            }
            if (expanded) {
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    files.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(enabled = enabled, onClick = { onExport(name) }) {
                                Text(localizedText("Export"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudSyncPasswordDialog(
    onDismiss: () -> Unit,
    onConnect: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val issue = if (submitted) exportPasswordIssue(password, confirmation) else null
    val dismiss = {
        password = ""
        confirmation = ""
        onDismiss()
    }
    HingeSafeAlertDialog(
        onDismissRequest = dismiss,
        title = { Text(localizedText("Connect Google Drive")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    localizedText(
                        "Choose a sync password. You will enter the same password on Windows or a new device. " +
                            "Google cannot recover it.",
                    ),
                )
                PasswordField(
                    value = password,
                    label = "Sync password",
                    visible = visible,
                    isError = issue == BackupPasswordIssue.TOO_SHORT,
                    onValueChange = { password = it; submitted = false },
                    onVisibilityChange = { visible = !visible },
                )
                PasswordField(
                    value = confirmation,
                    label = "Confirm password",
                    visible = visible,
                    isError = issue == BackupPasswordIssue.DOES_NOT_MATCH,
                    onValueChange = { confirmation = it; submitted = false },
                    onVisibilityChange = { visible = !visible },
                )
                if (issue != null) {
                    Text(localizedText(issue.message), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(localizedText("Cancel")) } },
        confirmButton = {
            Button(onClick = {
                submitted = true
                if (exportPasswordIssue(password, confirmation) == null) {
                    val transferred = password.toCharArray()
                    password = ""
                    confirmation = ""
                    onConnect(transferred)
                }
            }) { Text(localizedText("Connect")) }
        },
    )
}

@Composable
private fun CloudConflictDialog(
    onKeepLocal: () -> Unit,
    onUseCloud: () -> Unit,
    onDismiss: () -> Unit,
) {
    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("Sync conflict")) },
        text = {
            Text(
                localizedText(
                    "This device and Google Drive both changed since the last sync. " +
                        "Use cloud replaces all local data. Keep this device uploads the current " +
                        "local data as the next encrypted snapshot.",
                ),
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("Cancel")) } },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onUseCloud) { Text(localizedText("Use cloud")) }
                Button(onClick = onKeepLocal) { Text(localizedText("Keep this device")) }
            }
        },
    )
}

private fun formatCloudSyncTime(timestamp: Long, language: UiLanguage): String = runCatching {
    val (pattern, locale) = when (language) {
        UiLanguage.ENGLISH -> "MMM d, yyyy, h:mm a" to Locale.US
        UiLanguage.SIMPLIFIED_CHINESE -> "yyyy/M/d HH:mm" to Locale.SIMPLIFIED_CHINESE
    }
    DateTimeFormatter.ofPattern(pattern, locale)
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
}.getOrDefault("Unknown")

@Composable
private fun IncludedPersonalBackupCard(
    enabled: Boolean,
    onRestore: () -> Unit,
) {
    BackupActionCard(
        title = "Restore your previous data",
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
                Text(localizedText("Restore included backup"))
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
                    text = localizedText("Private, offline backup"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = localizedText(
                        "Your todos, ledger, notes, settings, attachments, and Vault are encrypted " +
                            "into one local .tlb file. Nothing is uploaded.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = localizedText("The backup password is never saved and cannot be recovered."),
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
                Text(localizedText("Choose export location"))
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
                Text(localizedText("Choose backup file"))
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
                text = localizedText(title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = localizedText(description),
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
        title = { Text(localizedText("Encrypt backup")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    localizedText("Use at least 8 characters. This password is not saved and cannot be recovered."),
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
                        text = localizedText(issue.message),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text(localizedText("Cancel")) }
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
                Text(localizedText("Export"))
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
        title = { Text(localizedText("Unlock backup")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    localizedText("Enter the password used when this backup was created. It is not saved."),
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
                        text = localizedText(issue.message),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text(localizedText("Cancel")) }
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
                Text(localizedText("Decrypt & review"))
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
        label = { Text(localizedText(label)) },
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
                    contentDescription = localizedText(if (visible) "Hide password" else "Show password"),
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
        title = { Text(localizedText("Review backup")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    localizedText("The backup was decrypted and validated. Review it before continuing."),
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
                    text = localizedText("Continuing does not restore yet. You will see a final replacement warning."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(localizedText("Cancel restore")) }
        },
        confirmButton = {
            Button(onClick = onContinue) { Text(localizedText("Continue")) }
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
            text = localizedText(label),
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
        title = { Text(localizedText("Replace all local data?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    localizedText(
                        "This is a complete replacement, not a merge. It permanently replaces your " +
                            "current todos, ledger, notes, settings, attachments, and Vault with the backup.",
                    ),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    localizedText(
                        "Once restore starts, it cannot be cancelled. The existing data is left " +
                            "unchanged if validation or preparation fails.",
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onBackToPreview) { Text(localizedText("Back to preview")) }
        },
        confirmButton = {
            Button(onClick = onRestore) { Text(localizedText("Replace & restore")) }
        },
    )
}

@Composable
private fun BlockingBackupDialog(task: BackupRestoreTask) {
    val (title, message) = when (task) {
        BackupRestoreTask.EXPORTING ->
            "Creating encrypted backup" to "Keep Life Assistant open while the file is written."
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
        title = { Text(localizedText(title)) },
        text = { Text(localizedText(message)) },
        confirmButton = { },
    )
}

internal fun ensureBackupExtension(fileName: String): String {
    val trimmed = fileName.trim().ifEmpty { "LifeAssistant-backup" }
    return if (trimmed.endsWith(".tlb", ignoreCase = true)) trimmed else "$trimmed.tlb"
}
