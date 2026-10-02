package com.ced2711.lifetracker.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog
import com.ced2711.lifetracker.ui.components.FieldLabel
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.RowDivider
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.theme.LifeTheme

/*
 * The dialogs of Backup & sync: connecting the cloud, the GitHub code, a sync conflict, the
 * passwords of a backup file, and the steps of restoring one. They only show and collect;
 * BackupRestoreScreen decides what happens next.
 */

/** Asks for the sync password (twice) before connecting; some builds also ask where GitHub is. */
@Composable
internal fun CloudSyncPasswordDialog(
    onDismiss: () -> Unit,
    onConnect: (CharArray) -> Unit,
    title: String = "Connect Google Drive",
    gitHub: Boolean = false,
    // Only builds without a built-in GitHub sign-in ask for a Client ID and repository.
    showGitHubFields: Boolean = gitHub,
    initialClientId: String = "",
    initialRepository: String = "",
    onConnectGitHub: (CharArray, String, String) -> Unit = { password, _, _ -> onConnect(password) },
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var clientId by rememberSaveable { mutableStateOf(initialClientId) }
    var repository by rememberSaveable { mutableStateOf(initialRepository) }
    val issue = if (submitted) exportPasswordIssue(password, confirmation) else null
    val clientIdMissing = showGitHubFields && submitted && clientId.isBlank()
    val dismiss = {
        password = ""
        confirmation = ""
        onDismiss()
    }
    HingeSafeAlertDialog(
        onDismissRequest = dismiss,
        title = { Text(localizedText(title)) },
        text = {
            // HingeSafeAlertDialog already scrolls its body; a second vertical scroll here crashes.
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    localizedText(
                        "Choose a sync password. If another device already syncs, enter its password " +
                            "(on Windows this is your data password). Nobody can recover it for you.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showGitHubFields) {
                    FieldLabel(localizedText("GitHub Client ID"))
                    LifeTextField(
                        value = clientId,
                        onValueChange = { clientId = it.trim(); submitted = false },
                        isError = clientIdMissing,
                        background = dialogFieldColor(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FieldLabel(localizedText("Private repository (owner/name, optional)"))
                    LifeTextField(
                        value = repository,
                        onValueChange = { repository = it },
                        background = dialogFieldColor(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PasswordField(
                    value = password,
                    label = localizedText("Sync password"),
                    visible = visible,
                    isError = issue == BackupPasswordIssue.TOO_SHORT,
                    onValueChange = { password = it; submitted = false },
                    onVisibilityChange = { visible = !visible },
                    imeAction = ImeAction.Next,
                )
                PasswordField(
                    value = confirmation,
                    label = localizedText("Confirm password"),
                    visible = visible,
                    isError = issue == BackupPasswordIssue.DOES_NOT_MATCH,
                    onValueChange = { confirmation = it; submitted = false },
                    onVisibilityChange = { visible = !visible },
                )
                DialogHint(issue?.let { localizedText(it.message) }, localizedText("At least 8 characters."))
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(localizedText("Cancel")) } },
        confirmButton = {
            Button(onClick = {
                submitted = true
                if (exportPasswordIssue(password, confirmation) == null && !(showGitHubFields && clientId.isBlank())) {
                    val transferred = password.toCharArray()
                    password = ""
                    confirmation = ""
                    if (gitHub) onConnectGitHub(transferred, clientId, repository) else onConnect(transferred)
                }
            }) { Text(localizedText("Connect")) }
        },
    )
}

/**
 * The code to type at GitHub, large enough to read across a desk. It is copied when the dialog
 * opens (by the caller); the dialog closes by itself once GitHub has approved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GitHubCodeDialog(
    prompt: GitHubCodePrompt,
    onCopy: () -> Unit,
    onOpenGitHub: () -> Unit,
    onCancel: () -> Unit,
) {
    HingeSafeAlertDialog(
        onDismissRequest = {},
        title = { Text(localizedText("Approve on GitHub")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(
                    localizedText("The code is copied. Open GitHub, paste it, and choose Authorize. Then come back; this screen continues by itself."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(dialogFieldColor())
                        .padding(horizontal = Space.md, vertical = Space.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Text(
                        prompt.userCode,
                        style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 2.sp),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        prompt.verificationUri.removePrefix("https://"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    OutlinedButton(onClick = onCopy) { Text(localizedText("Copy code")) }
                    Button(onClick = onOpenGitHub) { Text(localizedText("Open GitHub")) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small))
                    Text(
                        localizedText("Waiting for your approval on GitHub…"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(localizedText("Cancel")) } },
    )
}

/** Both sides changed: the user picks which one stays. [provider] is the cloud in use. */
@Composable
internal fun CloudConflictDialog(
    provider: String,
    onKeepLocal: () -> Unit,
    onUseCloud: () -> Unit,
    onDismiss: () -> Unit,
) {
    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("Sync conflict")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(conflictExplanation(provider, LocalUiLanguage.current), color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Full-width choices: three buttons do not fit side by side on a phone.
                Button(onClick = onKeepLocal, modifier = Modifier.fillMaxWidth().padding(top = Space.sm)) {
                    Text(localizedText("Keep this device"))
                }
                OutlinedButton(onClick = onUseCloud, modifier = Modifier.fillMaxWidth()) {
                    Text(localizedText("Use cloud"), color = LifeTheme.colors.danger)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(localizedText("Cancel")) } },
    )
}

private fun conflictExplanation(provider: String, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH ->
        "This device and $provider both changed since the last sync. Use cloud replaces all local data. " +
            "Keep this device uploads the current local data as the next encrypted snapshot."
    UiLanguage.SIMPLIFIED_CHINESE ->
        "自上次同步以来，本机和 $provider 上的数据都有改动。“使用云端”会替换本机的全部数据；" +
            "“保留本机”会把本机当前的数据上传为下一个加密快照。"
}

/** Second step of an export: the password of the new backup file, twice. */
@Composable
internal fun ExportPasswordDialog(
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
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    localizedText("Use at least 8 characters. This password is not saved and cannot be recovered."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PasswordField(
                    value = password,
                    label = localizedText("Backup password"),
                    visible = showPassword,
                    isError = issue == BackupPasswordIssue.TOO_SHORT,
                    onValueChange = {
                        password = it
                        submitted = false
                    },
                    onVisibilityChange = { showPassword = !showPassword },
                    imeAction = ImeAction.Next,
                )
                PasswordField(
                    value = confirmation,
                    label = localizedText("Confirm password"),
                    visible = showPassword,
                    isError = issue == BackupPasswordIssue.DOES_NOT_MATCH,
                    onValueChange = {
                        confirmation = it
                        submitted = false
                    },
                    onVisibilityChange = { showPassword = !showPassword },
                )
                DialogHint(issue?.let { localizedText(it.message) }, null)
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(localizedText("Cancel")) } },
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
            ) { Text(localizedText("Export")) }
        },
    )
}

/** Step 1 of a restore: the password the backup was made with. */
@Composable
internal fun RestorePasswordDialog(
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
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    localizedText("Enter the password used when this backup was created. It is not saved."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PasswordField(
                    value = password,
                    label = localizedText("Backup password"),
                    visible = showPassword,
                    isError = issue != null,
                    onValueChange = {
                        password = it
                        submitted = false
                    },
                    onVisibilityChange = { showPassword = !showPassword },
                )
                DialogHint(issue?.let { localizedText(it.message) }, null)
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(localizedText("Cancel")) } },
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
            ) { Text(localizedText("Decrypt & review")) }
        },
    )
}

/** Step 2 of a restore: what the backup holds, before anything is replaced. */
@Composable
internal fun RestorePreviewDialog(
    preview: BackupRestorePreview,
    onCancel: () -> Unit,
    onContinue: () -> Unit,
) {
    HingeSafeAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(localizedText("Review backup")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(
                    localizedText("The backup was decrypted and validated. Review it before continuing."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(dialogFieldColor())
                        .padding(vertical = Space.xs),
                ) {
                    PreviewValue(localizedText("Created"), preview.createdAtLabel)
                    RowDivider()
                    PreviewValue(localizedText("Todos"), preview.todoCount.toString())
                    PreviewValue(localizedText("Ledger entries"), preview.ledgerCount.toString())
                    PreviewValue(localizedText("Notes"), preview.noteCount.toString())
                    PreviewValue(localizedText("Diary entries"), preview.diaryCount.toString())
                    PreviewValue(localizedText("Vault entries"), preview.vaultCount.toString())
                    PreviewValue(
                        localizedText("Attachments"),
                        "${preview.attachmentCount} (${formatBackupByteCount(preview.attachmentBytes)})",
                    )
                    RowDivider()
                    PreviewValue(localizedText("Total backup size"), formatBackupByteCount(preview.totalBytes))
                }
                Text(
                    localizedText("Continuing does not restore yet. You will see a final replacement warning."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(localizedText("Cancel restore")) } },
        confirmButton = { Button(onClick = onContinue) { Text(localizedText("Continue")) } },
    )
}

@Composable
private fun PreviewValue(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

/** Step 3 of a restore: the last warning before the local data is replaced. */
@Composable
internal fun RestoreReplacementConfirmationDialog(
    onBackToPreview: () -> Unit,
    onRestore: () -> Unit,
) {
    HingeSafeAlertDialog(
        onDismissRequest = onBackToPreview,
        title = { Text(localizedText("Replace all local data?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(
                    localizedText(
                        "This is a complete replacement, not a merge. It permanently replaces your " +
                            "current todos, ledger, notes, settings, attachments, and Vault with the backup.",
                    ),
                    color = LifeTheme.colors.danger,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    localizedText(
                        "Once restore starts, it cannot be cancelled. The existing data is left " +
                            "unchanged if validation or preparation fails.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = { TextButton(onClick = onBackToPreview) { Text(localizedText("Back to preview")) } },
        confirmButton = {
            TextButton(onClick = onRestore) {
                Text(localizedText("Replace & restore"), color = LifeTheme.colors.danger, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/** Step 4, and every other backup task: progress that cannot be dismissed. */
@Composable
internal fun BlockingBackupDialog(task: BackupRestoreTask) {
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
        icon = { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp) },
        title = { Text(localizedText(title), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Text(
                localizedText(message),
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = { },
    )
}

/** A password with its label above and an eye to show it. */
@Composable
private fun PasswordField(
    value: String,
    label: String,
    visible: Boolean,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    onVisibilityChange: () -> Unit,
    imeAction: ImeAction = ImeAction.Done,
) {
    FieldLabel(label)
    LifeTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        isError = isError,
        background = dialogFieldColor(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        trailing = {
            IconButton(onClick = onVisibilityChange, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = localizedText(if (visible) "Hide password" else "Show password"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

/** The line under the fields: what is wrong in red, otherwise a quiet hint (or nothing). */
@Composable
private fun DialogHint(error: String?, hint: String?) {
    val text = error ?: hint ?: return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error != null) LifeTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.xs),
    )
}

/** Fields inside a dialog sit one step darker than the dialog itself. */
@Composable
private fun dialogFieldColor() = MaterialTheme.colorScheme.surfaceContainerLowest
