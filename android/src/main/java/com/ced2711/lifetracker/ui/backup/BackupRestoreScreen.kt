package com.ced2711.lifetracker.ui.backup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.data.cloud.CloudProvider
import com.ced2711.lifetracker.data.cloud.CloudSyncAttention
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.components.ConfirmDialog
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.IconTile
import com.ced2711.lifetracker.ui.design.ReadableWidth
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.settings.GroupColumns
import com.ced2711.lifetracker.ui.settings.SettingDivider
import com.ced2711.lifetracker.ui.settings.SettingRow
import com.ced2711.lifetracker.ui.settings.SettingSwitchRow
import com.ced2711.lifetracker.ui.settings.SettingsGroup
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class RestoreReviewStep {
    PREVIEW,
    CONFIRM_REPLACEMENT,
    SUBMITTED,
}

/**
 * Backup & sync. Repository work, Vault authentication, and result mapping are owned by
 * [actions] and [cloudViewModel]; this part only launches the Storage Access Framework, owns the
 * dialogs and enforces the review flow of a restore. [BackupContent] draws the page.
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
    var connectGitHubRequested by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var dailyRestoreRequested by remember { mutableStateOf<DailyBackupCopy?>(null) }
    var recoveryFileName by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uiLanguage = LocalUiLanguage.current
    val cloudState by cloudViewModel.uiState.collectAsStateWithLifecycle()
    val cloudConsent by cloudViewModel.consentRequest.collectAsStateWithLifecycle()
    val dailyBackups by actions.dailyBackups.collectAsStateWithLifecycle()

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

    LaunchedEffect(Unit) { actions.refreshDailyBackups() }
    LaunchedEffect(uiState.task) {
        if (uiState.task != BackupRestoreTask.NONE) pendingSubmission = null
    }
    LaunchedEffect(uiState.restorePreview) {
        if (uiState.restorePreview == null) reviewStep = RestoreReviewStep.PREVIEW
    }
    LaunchedEffect(uiState.notice, uiLanguage) {
        val notice = uiState.notice ?: return@LaunchedEffect
        // A restored daily copy can be taken back right from its message.
        val offersUndo = notice == BackupRestoreNotice.DAILY_RESTORE_COMPLETE
        val result = snackbarHostState.showSnackbar(
            message = translateUiText(notice.message, uiLanguage),
            actionLabel = if (offersUndo) translateUiText("Undo restore", uiLanguage) else null,
            duration = if (offersUndo) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        actions.acknowledgeNotice()
        if (offersUndo && result == SnackbarResult.ActionPerformed) actions.undoDailyRestore()
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
        connectCloudRequested ||
        connectGitHubRequested
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

    Box(modifier.fillMaxSize()) {
        BackupContent(
            cloud = cloudState,
            daily = dailyBackups,
            actionsEnabled = !isBlocked && uiState.restorePreview == null,
            hasIncludedPersonalBackup = hasIncludedPersonalBackup,
            onConnectGitHub = { connectGitHubRequested = true },
            onSync = { cloudViewModel.syncNow() },
            onReconnect = cloudViewModel::reconnect,
            onAutomaticSync = cloudViewModel::setAutomaticSync,
            onDisconnect = { confirmDisconnect = true },
            onExportRecovery = { name ->
                recoveryFileName = name
                exportRecoveryLauncher.launch(name)
            },
            onRestoreDaily = { dailyRestoreRequested = it },
            onUndoDailyRestore = actions::undoDailyRestore,
            onExport = { createBackupLauncher.launch(ensureBackupExtension(defaultExportFileName)) },
            onChooseBackupFile = {
                openBackupLauncher.launch(arrayOf(TASK_LEDGER_BACKUP_MIME_TYPE, "application/octet-stream"))
            },
            onRestoreIncluded = { includedRestoreRequested = true },
        )
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
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

    // Google Drive is not offered for new connections at the moment; the dialog stays for the
    // day it returns.
    if (connectCloudRequested) {
        CloudSyncPasswordDialog(
            onDismiss = { connectCloudRequested = false },
            onConnect = { password ->
                connectCloudRequested = false
                cloudViewModel.connect(password)
            },
        )
    }

    if (connectGitHubRequested) {
        CloudSyncPasswordDialog(
            title = "Connect GitHub",
            initialClientId = cloudState.gitHubClientId,
            initialRepository = cloudState.gitHubRepository,
            gitHub = true,
            showGitHubFields = !cloudState.builtInGitHub,
            onDismiss = { connectGitHubRequested = false },
            onConnect = { password -> password.fill('\u0000') },
            onConnectGitHub = { password, clientId, repository ->
                connectGitHubRequested = false
                cloudViewModel.connectGitHub(password, clientId, repository)
            },
        )
    }

    cloudState.gitHubPrompt?.let { prompt ->
        val copyCode = {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("GitHub code", prompt.userCode))
            Unit
        }
        LaunchedEffect(prompt.userCode) { copyCode() }
        GitHubCodeDialog(
            prompt = prompt,
            onCopy = copyCode,
            onOpenGitHub = {
                copyCode()
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, prompt.verificationUri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure {
                    Toast.makeText(
                        context,
                        translateUiText("No browser is available to open GitHub.", uiLanguage),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onCancel = cloudViewModel::cancelGitHubConnect,
        )
    }

    cloudState.conflict?.let {
        CloudConflictDialog(
            provider = cloudState.providerName,
            onKeepLocal = { cloudViewModel.syncNow(ConflictResolution.KEEP_LOCAL) },
            onUseCloud = { cloudViewModel.syncNow(ConflictResolution.USE_CLOUD) },
            onDismiss = cloudViewModel::dismissConflict,
        )
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = localizedText("Disconnect cloud sync?"),
            text = localizedText("This device stops syncing. Your data stays on this device and in the cloud; you can connect again any time."),
            confirmLabel = localizedText("Disconnect"),
            destructive = true,
            onConfirm = {
                confirmDisconnect = false
                cloudViewModel.disconnect()
            },
            onDismiss = { confirmDisconnect = false },
        )
    }

    dailyRestoreRequested?.let { copy ->
        ConfirmDialog(
            title = localizedText("Go back to this copy?"),
            text = dailyRestoreWarning(
                UserFormatting.formatDate(copy.day, dailyBackups.dateFormat, uiLocale(uiLanguage)),
                uiLanguage,
            ),
            confirmLabel = localizedText("Restore"),
            destructive = true,
            onConfirm = {
                dailyRestoreRequested = null
                actions.restoreDailyBackup(copy.day)
            },
            onDismiss = { dailyRestoreRequested = null },
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

/**
 * The Backup & sync page without any Android service: cloud sync, the copies kept on this device
 * and the backup file, as groups of rows. [actionsEnabled] is false while a backup task runs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackupContent(
    cloud: CloudSyncUiState,
    daily: DailyBackupsUiState,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    hasIncludedPersonalBackup: Boolean = false,
    today: LocalDate = LocalDate.now(),
    scrollState: ScrollState = rememberScrollState(),
    onConnectGitHub: () -> Unit,
    onSync: () -> Unit,
    onReconnect: () -> Unit,
    onAutomaticSync: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onExportRecovery: (String) -> Unit,
    onRestoreDaily: (DailyBackupCopy) -> Unit,
    onUndoDailyRestore: () -> Unit,
    onExport: () -> Unit,
    onChooseBackupFile: () -> Unit,
    onRestoreIncluded: () -> Unit,
) {
    val language = LocalUiLanguage.current

    @Composable
    fun migrationGroup() {
        SettingsGroup(localizedText("Restore your previous data")) {
            SettingRow(
                title = localizedText("Your original backup is included"),
                supporting = localizedText(
                    "This personal migration build contains your original encrypted backup. " +
                        "Enter its password to validate it, review the contents, and restore. The password " +
                        "and decrypted data are not built into the app.",
                ),
                stacked = true,
            ) {
                Button(enabled = actionsEnabled, onClick = onRestoreIncluded) { Text(localizedText("Restore included backup")) }
            }
        }
    }

    @Composable
    fun cloudGroup() {
        SettingsGroup(
            title = localizedText("Cloud sync"),
            description = localizedText("Keeps your phone and your PC the same. Everything is encrypted before it leaves this device, and changes from both devices are merged automatically."),
        ) {
            if (!cloud.connected) {
                SettingRow(
                    title = "GitHub",
                    supporting = localizedText("Uses a private repository in your own GitHub account. One sign-in, nothing else to set up."),
                    leading = { IconTile(Icons.Rounded.Cloud) },
                    stacked = true,
                ) {
                    Button(enabled = !cloud.busy, onClick = onConnectGitHub) {
                        Text(localizedText(if (cloud.busy) "Connecting…" else "Connect GitHub"))
                    }
                }
                cloud.connectionError?.let { error -> AttentionLine(localizedText(error)) }
                SettingDivider()
                // Google Drive returns once its Google Cloud project is published; existing
                // connections keep working.
                SettingRow(title = "Google Drive", supporting = localizedText("Coming soon."), enabled = false)
                SettingDivider()
                SettingRow(
                    title = localizedText("Works without sync too"),
                    supporting = localizedText("Life Assistant is fully usable offline on this device alone. Sync is optional."),
                )
            } else {
                val needsReconnect = cloud.attention == CloudSyncAttention.GOOGLE_CONSENT || cloud.attention == CloudSyncAttention.GITHUB_SIGN_IN
                val connected = localizedText("Connected")
                SettingRow(
                    title = cloud.providerName,
                    supporting = listOfNotNull(connected, cloud.gitHubRepository.takeIf { cloud.provider == CloudProvider.GITHUB && it.isNotBlank() }).joinToString(" · "),
                    supportingColor = MaterialTheme.colorScheme.primary,
                    leading = { IconTile(Icons.Rounded.CloudDone) },
                )
                cloud.attention?.let { attention ->
                    AttentionLine(
                        text = when (attention) {
                            CloudSyncAttention.CONFLICT ->
                                localizedText("Cloud changes need review. Sync now to choose which version to keep.")
                            CloudSyncAttention.VAULT_UNLOCK ->
                                localizedText("Automatic sync is waiting for Vault authentication.")
                            CloudSyncAttention.GOOGLE_CONSENT ->
                                localizedText("Google Drive permission needs to be renewed.")
                            CloudSyncAttention.GITHUB_SIGN_IN ->
                                localizedText("GitHub sign-in expired. Reconnect; your data and settings stay as they are.")
                            CloudSyncAttention.FAILED ->
                                // The reason says more than "failed": no network, wrong password, …
                                cloud.lastError?.takeIf(String::isNotBlank)?.let { syncFailedBecause(localizedText(it), language) }
                                    ?: localizedText("The last automatic sync failed. Try syncing again.")
                        },
                        actionLabel = localizedText("Reconnect").takeIf { needsReconnect },
                        actionEnabled = !cloud.busy,
                        onAction = onReconnect,
                    )
                }
                SettingDivider()
                SettingSwitchRow(
                    title = localizedText("Automatic sync"),
                    supporting = localizedText("Syncs a few seconds after each change, when you open the app, and about every 15 minutes in the background."),
                    checked = cloud.automaticSync,
                    enabled = !cloud.busy,
                    onCheckedChange = onAutomaticSync,
                )
                SettingDivider()
                SettingRow(
                    title = localizedText("Last sync"),
                    supporting = cloud.lastSyncAt?.let { formatCloudSyncTime(it, language) } ?: localizedText("Never synced"),
                ) {
                    if (!needsReconnect) {
                        Button(enabled = !cloud.busy, onClick = onSync) {
                            if (cloud.busy) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(Space.sm))
                            Text(localizedText(if (cloud.busy) "Syncing…" else "Sync now"))
                        }
                    }
                }
                SettingDivider()
                SettingRow(
                    title = localizedText("Connection"),
                    supporting = localizedText("Reconnect signs in again and keeps everything. Disconnect stops syncing on this device; your data stays here."),
                    stacked = true,
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        if (!needsReconnect) {
                            OutlinedButton(enabled = !cloud.busy, onClick = onReconnect) { Text(localizedText("Reconnect")) }
                        }
                        OutlinedButton(
                            enabled = !cloud.busy,
                            onClick = onDisconnect,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LifeTheme.colors.danger),
                        ) { Text(localizedText("Disconnect")) }
                    }
                }
            }
        }
        if (cloud.connected) {
            Footnote(localizedText("When Vault contains entries, background sync pauses until you unlock it in Life Assistant. This keeps Vault keys protected by Android."))
        }
    }

    @Composable
    fun recoveryGroup() {
        SettingsGroup(
            title = localizedText("Sync recovery copies"),
            description = localizedText("Before a cloud version replaces local data, an encrypted copy is kept on this device (the 5 most recent). Export one, then restore it with the sync password it was made with."),
        ) {
            cloud.recoveryFiles.forEachIndexed { index, name ->
                if (index > 0) SettingDivider()
                SettingRow(title = recoveryCopyLabel(name, language)) {
                    OutlinedButton(enabled = actionsEnabled, onClick = { onExportRecovery(name) }) { Text(localizedText("Export")) }
                }
            }
        }
    }

    @Composable
    fun dailyGroup() {
        SettingsGroup(
            title = localizedText("Daily backups"),
            description = localizedText("The app keeps a copy of yesterday and the day before on this device."),
        ) {
            if (daily.copies.isEmpty()) {
                EmptyState(
                    title = localizedText("No copies yet"),
                    body = localizedText("The first copy is made tomorrow."),
                )
            }
            daily.copies.forEachIndexed { index, copy ->
                if (index > 0) SettingDivider()
                SettingRow(
                    title = dailyCopyLabel(copy.day, today, daily.dateFormat, language),
                    supporting = formatBackupByteCount(copy.sizeBytes),
                ) {
                    OutlinedButton(enabled = actionsEnabled, onClick = { onRestoreDaily(copy) }) { Text(localizedText("Restore")) }
                }
            }
            if (daily.canUndo) {
                SettingDivider()
                SettingRow(
                    title = localizedText("Undo the last restore"),
                    supporting = localizedText("Puts back the data from right before you restored a daily copy."),
                ) {
                    TextButton(enabled = actionsEnabled, onClick = onUndoDailyRestore) {
                        Text(localizedText("Undo restore"), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    @Composable
    fun fileGroup() {
        SettingsGroup(
            title = localizedText("Backup file"),
            description = localizedText("A single encrypted .tlb file with everything: todos, ledger, notes, diary, Vault and attached files. Use it to move data or keep a copy elsewhere."),
        ) {
            SettingRow(
                title = localizedText("Export"),
                supporting = localizedText("Choose where to save the file, then give it a password. Exporting the Vault may ask for device authentication."),
                stacked = true,
            ) {
                OutlinedButton(enabled = actionsEnabled, onClick = onExport) { Text(localizedText("Export backup")) }
            }
            SettingDivider()
            SettingRow(
                title = localizedText("Restore"),
                supporting = localizedText("Replace this device's data with a backup file, after reviewing what it contains."),
                stacked = true,
            ) {
                OutlinedButton(enabled = actionsEnabled, onClick = onChooseBackupFile) { Text(localizedText("Choose backup file")) }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 840.dp
        Box(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            ReadableWidth(maxWidth = if (twoColumns) 1120.dp else 760.dp) {
                GroupColumns(
                    twoColumns = twoColumns,
                    modifier = Modifier.padding(horizontal = if (twoColumns) Space.xxl else Space.lg).padding(top = Space.xs, bottom = Space.xxxl),
                    first = {
                        if (hasIncludedPersonalBackup) migrationGroup()
                        cloudGroup()
                        if (cloud.recoveryFiles.isNotEmpty()) recoveryGroup()
                    },
                    second = {
                        dailyGroup()
                        fileGroup()
                    },
                )
            }
        }
    }
}

/** Something about sync that needs the user: a red line inside the panel, with its action. */
@Composable
private fun AttentionLine(
    text: String,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.xs)
            .clip(MaterialTheme.shapes.medium)
            .background(LifeTheme.colors.dangerContainer)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Icon(Icons.Rounded.ErrorOutline, null, Modifier.padding(top = 1.dp).size(18.dp), tint = LifeTheme.colors.danger)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = LifeTheme.colors.danger, modifier = Modifier.weight(1f))
        }
        if (actionLabel != null) {
            Button(
                onClick = onAction,
                enabled = actionEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = LifeTheme.colors.danger, contentColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(start = 26.dp),
            ) { Text(actionLabel) }
        }
    }
}

/** A quiet note under a group. */
@Composable
private fun Footnote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.xs),
    )
}

private val CloudSyncUiState.providerName: String
    get() = if (provider == CloudProvider.GITHUB) "GitHub" else "Google Drive"

private fun syncFailedBecause(reason: String, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "The last sync failed: $reason"
    UiLanguage.SIMPLIFIED_CHINESE -> "上次同步失败：$reason"
}

private fun dailyRestoreWarning(date: String, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH ->
        "Everything except the Password vault is replaced by the copy from $date. What you have now is kept, so you can undo this."
    UiLanguage.SIMPLIFIED_CHINESE ->
        "除密码库以外的所有数据都会被 $date 的备份替换。现在的数据会保留，所以可以撤销。"
}

/** "Yesterday · 10/01/2026": which day a daily copy is from, in words and in the user's format. */
internal fun dailyCopyLabel(day: LocalDate, today: LocalDate, dateFormat: DateFormatOption, language: UiLanguage): String {
    val locale = uiLocale(language)
    val inWords = when (ChronoUnit.DAYS.between(day, today)) {
        1L -> translateUiText("Yesterday", language)
        2L -> when (language) {
            UiLanguage.ENGLISH -> "2 days ago"
            UiLanguage.SIMPLIFIED_CHINESE -> "前天"
        }
        else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    }
    return "$inWords · ${UserFormatting.formatDate(day, dateFormat, locale)}"
}

/** A recovery copy is named after the moment it was made; show that moment instead of the file name. */
private fun recoveryCopyLabel(fileName: String, language: UiLanguage): String {
    val madeAt = fileName.removePrefix("life-tracker-cloud-recovery-").substringBefore('-').toLongOrNull()
    return madeAt?.takeIf { it > 0L }?.let { formatCloudSyncTime(it, language) } ?: fileName
}

private fun formatCloudSyncTime(timestamp: Long, language: UiLanguage): String = runCatching {
    val (pattern, locale) = when (language) {
        UiLanguage.ENGLISH -> "MMM d, h:mm a" to Locale.US
        UiLanguage.SIMPLIFIED_CHINESE -> "M月d日 HH:mm" to Locale.SIMPLIFIED_CHINESE
    }
    DateTimeFormatter.ofPattern(pattern, locale)
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
}.getOrDefault(translateUiText("Unknown", language))

internal fun ensureBackupExtension(fileName: String): String {
    val trimmed = fileName.trim().ifEmpty { "LifeAssistant-backup" }
    return if (trimmed.endsWith(".tlb", ignoreCase = true)) trimmed else "$trimmed.tlb"
}
