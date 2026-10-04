package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSettings
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.AppIdentity
import com.ced2711.lifetracker.domain.model.AppLockTimeout
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.ReminderOffsetPreset
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.WeekStart
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.Pill
import com.ced2711.lifetracker.ui.design.RowDivider
import com.ced2711.lifetracker.ui.design.Segmented
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import com.ced2711.lifetracker.ui.theme.accentOf
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsSection(val label: String, val icon: ImageVector) {
    SYNC("Sync & backup", Icons.Rounded.Cloud),
    APPEARANCE("Appearance", Icons.Rounded.Palette),
    REMINDERS("Reminders", Icons.Rounded.Notifications),
    SECURITY("Security", Icons.Rounded.Shield),
    AI("AI assistants", Icons.Rounded.SmartToy),
    ABOUT("About", Icons.Rounded.Info),
}

/** Settings, one topic at a time: topics on the left, the chosen topic's options on the right. */
@Composable
internal fun SettingsPage(
    snapshot: BackupSnapshot,
    cloudState: DesktopCloudUiState,
    cloud: DesktopCloudSyncController,
    configStore: DesktopConfigStore,
    dataStore: DesktopDataStore,
    credentials: DesktopCredentialStore,
    agentActivity: StateFlow<List<DesktopAgentActivity>>,
    onUiLanguageChanged: (UiLanguage) -> Unit,
    visibleDestinations: Set<TopLevelDestination>,
    onVisibleDestinationsChanged: (Set<TopLevelDestination>) -> Unit,
    moduleLabel: (TopLevelDestination) -> String,
    appLockEnabled: Boolean,
    appLockTimeout: AppLockTimeout,
    onAppLockChanged: (Boolean, AppLockTimeout) -> Unit,
) {
    var section by remember { mutableStateOf(SettingsSection.entries.firstOrNull { it.name == DesktopStartHints.settingsSection } ?: SettingsSection.SYNC) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideNav = maxWidth >= 820.dp
        Row(Modifier.fillMaxSize()) {
            if (sideNav) {
                FilterColumn(Modifier.width(232.dp)) {
                    Text(desktopText("Settings"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 10.dp, bottom = Space.lg))
                    SettingsSection.entries.forEach { item ->
                        FilterEntry(desktopText(item.label), null, section == item) { section = item }
                    }
                }
                ColumnDivider()
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), horizontalAlignment = if (sideNav) Alignment.Start else Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(horizontal = PagePadding).padding(top = 28.dp, bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    if (!sideNav) {
                        Text(desktopText("Settings"), style = MaterialTheme.typography.headlineLarge)
                        Box(Modifier.horizontalScroll(rememberScrollState())) {
                            Segmented(SettingsSection.entries, section, { section = it }, { desktopText(it.label) })
                        }
                    } else {
                        Text(desktopText(section.label), style = MaterialTheme.typography.headlineLarge)
                    }
                    when (section) {
                        SettingsSection.SYNC -> SyncSection(snapshot, cloudState, cloud, configStore, dataStore)
                        SettingsSection.APPEARANCE -> AppearanceSection(snapshot, configStore, dataStore, onUiLanguageChanged, visibleDestinations, onVisibleDestinationsChanged, moduleLabel)
                        SettingsSection.REMINDERS -> RemindersSection(snapshot.settings, configStore, dataStore)
                        SettingsSection.SECURITY -> SecuritySection(cloudState, configStore, dataStore, credentials, appLockEnabled, appLockTimeout, onAppLockChanged)
                        SettingsSection.AI -> AiSection(configStore, agentActivity)
                        SettingsSection.ABOUT -> AboutSection()
                    }
                }
            }
        }
    }
}

/** A titled group of settings. */
@Composable
private fun Group(title: String? = null, description: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (title != null) {
            Text(desktopText(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Space.xs, top = Space.sm))
        }
        if (description != null) {
            Text(desktopText(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.xs))
        }
        Panel(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.xs), content = content)
    }
}

/** One setting: what it is, a line of explanation, and its control on the right. */
@Composable
private fun SettingRow(title: String, description: String? = null, translate: Boolean = true, control: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.xl, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Space.lg), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (translate) desktopText(title) else title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(if (translate) desktopText(description) else description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}

/** A setting whose choices need a line of their own. */
@Composable
private fun SettingBlock(title: String, description: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(desktopText(title), style = MaterialTheme.typography.bodyLarge)
        if (description != null) Text(desktopText(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

// ---- Sync & backup -------------------------------------------------------------------------

@Composable
private fun SyncSection(
    snapshot: BackupSnapshot,
    cloudState: DesktopCloudUiState,
    cloud: DesktopCloudSyncController,
    configStore: DesktopConfigStore,
    dataStore: DesktopDataStore,
) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val config = configStore.read()
    val cloudDefaults = DesktopCloudDefaults.builtIn
    var gitHubDialog by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    fun connectGitHub() {
        // Installers carry the built-in sign-in, so connecting is a single click.
        if (cloudDefaults.hasGitHub) cloud.startGitHubConnect(cloudDefaults.gitHubClientId, "") else gitHubDialog = true
    }
    fun reconnect() {
        if (cloudState.provider == DesktopCloudProvider.GITHUB) {
            val clientId = config.gitHubClientId.ifBlank { cloudDefaults.gitHubClientId }
            if (clientId.isNotBlank() && config.gitHubRepository.isNotBlank()) cloud.startGitHubConnect(clientId, config.gitHubRepository) else gitHubDialog = true
        } else if (cloudDefaults.hasGoogle) {
            cloud.launch { connect(cloudDefaults.googleClientId, cloudDefaults.googleClientSecret.toCharArray()) }
        }
    }

    Group(
        "Cloud sync",
        "Keeps your phone and this PC the same. Everything is encrypted before it leaves this PC, and changes from both devices are merged automatically.",
    ) {
        if (!cloudState.connected) {
            SettingRow("GitHub", "Uses a private repository in your own GitHub account. One sign-in, nothing else to set up.") {
                Button(enabled = !cloudState.syncing, onClick = ::connectGitHub) { Text(desktopText(if (cloudState.syncing) "Connecting…" else "Connect GitHub")) }
            }
            RowDivider(inset = Space.xl)
            SettingRow("Google Drive", "Coming soon.")
            RowDivider(inset = Space.xl)
            SettingRow("Works without sync too", "Life Assistant is fully usable offline on this PC alone. Sync is optional.")
        } else {
            val provider = if (cloudState.provider == DesktopCloudProvider.GITHUB) "GitHub" else "Google Drive"
            SettingRow(
                title = desktopText("Connected") + " · " + provider + (cloudState.gitHubRepository.takeIf { it.isNotBlank() && cloudState.provider == DesktopCloudProvider.GITHUB }?.let { " · $it" } ?: ""),
                description = when {
                    cloudState.needsSignIn -> desktopText("GitHub sign-in expired. Reconnect; your data and settings stay as they are.")
                    else -> cloudState.lastSyncAt?.let { desktopLastSync(formatTimestamp(it), language) } ?: desktopText("Never synced")
                },
                translate = false,
            ) {
                if (cloudState.needsSignIn) {
                    Button(enabled = !cloudState.syncing, onClick = ::reconnect) { Text(desktopText("Reconnect")) }
                } else {
                    Button(enabled = !cloudState.syncing, onClick = { cloud.launch { synchronize() } }) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(desktopText(if (cloudState.syncing) "Syncing…" else "Sync now"))
                    }
                }
            }
            RowDivider(inset = Space.xl)
            SettingRow("Automatic sync", "A few seconds after each change, when the window is focused, and every 2 minutes while Life Assistant is open.") {
                Switch(cloudState.automaticSync, cloud::setAutomaticSync)
            }
            RowDivider(inset = Space.xl)
            SettingRow("Connection", "Reconnect signs in again and keeps everything. Disconnect stops syncing on this PC; your data stays here.") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    if (!cloudState.needsSignIn) OutlinedButton(enabled = !cloudState.syncing, onClick = ::reconnect) { Text(desktopText("Reconnect")) }
                    TextButton(enabled = !cloudState.syncing, onClick = { confirmDisconnect = true }) { Text(desktopText("Disconnect"), color = LifeTheme.colors.danger) }
                }
            }
        }
    }

    DailyBackups(snapshot, dataStore)

    var exportDialog by remember { mutableStateOf(false) }
    var importCandidate by remember { mutableStateOf<File?>(null) }
    // Second step of an import: what the backup holds, shown before anything is replaced.
    var importPreview by remember { mutableStateOf<Pair<File, BackupSnapshot>?>(null) }
    var importPreviewPassword by remember { mutableStateOf<CharArray?>(null) }
    var importChecking by remember { mutableStateOf(false) }
    var importPassword by remember { mutableStateOf("") }
    var importFailed by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    Group("Backup file", "A single encrypted .tlb file with everything: todos, ledger, notes, diary, Vault and attached files. Use it to move data or keep a copy elsewhere.") {
        SettingRow("Export", "Save an encrypted backup file where you choose.") {
            OutlinedButton(onClick = { exportDialog = true }) { Text(desktopText("Export backup")) }
        }
        RowDivider(inset = Space.xl)
        SettingRow("Import", "Replace this PC's data with a backup file, after reviewing what it contains.") {
            OutlinedButton(onClick = {
                importCandidate = JFileChooser().takeIf { it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION }?.selectedFile
                importPassword = ""
                importFailed = false
            }) { Text(desktopText("Import backup")) }
        }
        val recoveryDirectory = dataStore.cloudRecoveryDirectory
        if (recoveryDirectory.isDirectory) {
            RowDivider(inset = Space.xl)
            SettingRow("Sync recovery copies", "Before a cloud version replaces local data, an encrypted copy is kept here (the 5 most recent). Import one to go back.") {
                OutlinedButton(
                    enabled = Desktop.isDesktopSupported(),
                    onClick = { scope.launch(Dispatchers.IO) { runCatching { Desktop.getDesktop().open(recoveryDirectory) } } },
                ) { Text(desktopText("Open folder")) }
            }
        }
        backupMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.sm)) }
    }

    if (gitHubDialog) GitHubConnectDialog(config.gitHubClientId, config.gitHubRepository, { gitHubDialog = false }) { clientId, repository -> gitHubDialog = false; cloud.startGitHubConnect(clientId, repository) }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(desktopText("Disconnect cloud sync?")) },
            text = { Text(desktopText("This PC stops syncing. Your data stays on this PC and in the cloud; you can connect again any time.")) },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text(desktopText("Cancel")) } },
            confirmButton = { Button(onClick = { confirmDisconnect = false; cloud.launch { disconnect() } }) { Text(desktopText("Disconnect")) } },
        )
    }
    importCandidate?.let { candidate ->
        fun check() {
            if (importPassword.length < 8 || importChecking) return
            val sourcePassword = importPassword.toCharArray()
            importChecking = true
            scope.launch {
                val preview = dataStore.previewEncrypted(candidate, sourcePassword.copyOf())
                importChecking = false
                if (preview == null) {
                    sourcePassword.fill(' ')
                    importFailed = true
                } else {
                    importPassword = ""
                    importCandidate = null
                    importPreviewPassword = sourcePassword
                    importPreview = candidate to preview
                }
            }
        }
        AlertDialog(
            onDismissRequest = { importCandidate = null; importPassword = "" },
            title = { Text(desktopText("Open the backup")) },
            text = {
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Text(candidate.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(desktopText("Enter the password this backup was made with. You will see what it contains before anything is replaced."))
                    PasswordField(importPassword, { importPassword = it; importFailed = false }, desktopText("Backup password"), Modifier.fillMaxWidth().onEnter(::check), isError = importFailed)
                    if (importFailed) Text(desktopText("The source password is incorrect or the backup is damaged."), color = LifeTheme.colors.danger, style = MaterialTheme.typography.bodySmall)
                }
            },
            dismissButton = { TextButton(onClick = { importCandidate = null; importPassword = "" }) { Text(desktopText("Cancel")) } },
            confirmButton = { Button(enabled = importPassword.length >= 8 && !importChecking, onClick = ::check) { Text(desktopText(if (importChecking) "Checking…" else "Review backup")) } },
        )
    }
    if (exportDialog) {
        ExportBackupDialog(
            passwordChosen = configStore.passwordChosen(),
            onDismiss = { exportDialog = false },
            onExport = { backupPassword ->
                exportDialog = false
                val chooser = JFileChooser().apply { selectedFile = File("LifeAssistant-backup-${java.time.LocalDate.now()}.tlb") }
                val destination = chooser.takeIf { it.showSaveDialog(null) == JFileChooser.APPROVE_OPTION }?.selectedFile
                if (destination == null) {
                    backupPassword?.fill(' ')
                } else scope.launch {
                    if (backupPassword != null) {
                        val done = dataStore.exportWithPassword(destination, backupPassword)
                        backupMessage = desktopText(if (done) "Encrypted backup exported." else "Export failed.", language)
                    } else {
                        val upload = dataStore.createUploadSnapshot()
                        try {
                            withContext(Dispatchers.IO) { upload.file.copyTo(destination, overwrite = true) }
                            backupMessage = desktopText("Encrypted backup exported.", language)
                        } finally {
                            upload.file.delete()
                        }
                    }
                }
            },
        )
    }
    importPreview?.let { (candidate, preview) ->
        fun dismissPreview() {
            importPreviewPassword?.fill(' ')
            importPreviewPassword = null
            importPreview = null
        }
        AlertDialog(
            onDismissRequest = ::dismissPreview,
            title = { Text(desktopText("Replace this PC's data with the backup?")) },
            text = {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(desktopBackupCreated(formatTimestamp(preview.createdAt), language), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        Text("", Modifier.weight(1f))
                        Text(desktopText("Backup"), Modifier.width(90.dp), style = MaterialTheme.typography.labelLarge)
                        Text(desktopText("This PC"), Modifier.width(90.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    listOf<Triple<String, Int, Int>>(
                        Triple("Todos", preview.todos.count { it.deletedAt == null }, snapshot.todos.count { it.deletedAt == null }),
                        Triple("Ledger entries", preview.ledgerEntries.count { it.deletedAt == null }, snapshot.ledgerEntries.count { it.deletedAt == null }),
                        Triple("Notes", preview.notes.size, snapshot.notes.size),
                        Triple("Diary", preview.diaryEntries.size, snapshot.diaryEntries.size),
                        Triple("Daily checklist", preview.checklistItems.size, snapshot.checklistItems.size),
                        Triple("Vault", preview.vaultEntries.size, snapshot.vaultEntries.size),
                        Triple("Attachments", preview.attachments.size, snapshot.attachments.size),
                    ).forEach { (label, backup, local) ->
                        Row {
                            Text(desktopText(label), Modifier.weight(1f))
                            Text(backup.toString(), Modifier.width(90.dp))
                            Text(local.toString(), Modifier.width(90.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(desktopText("Everything on this PC is replaced by the backup. Export the current data first if you may need it later."), color = LifeTheme.colors.danger, style = MaterialTheme.typography.bodySmall)
                }
            },
            dismissButton = { TextButton(onClick = ::dismissPreview) { Text(desktopText("Cancel")) } },
            confirmButton = {
                Button(onClick = {
                    val sourcePassword = importPreviewPassword ?: return@Button
                    importPreviewPassword = null
                    importPreview = null
                    scope.launch {
                        try {
                            val expected = dataStore.localFingerprint()
                            backupMessage = when (dataStore.importFromEncrypted(candidate, sourcePassword, expected)) {
                                is DesktopReplaceResult.Applied -> desktopText("Backup imported.", language)
                                DesktopReplaceResult.LocalChanged -> desktopText("Local data changed; import was cancelled.", language)
                                DesktopReplaceResult.Invalid -> desktopText("The source password is incorrect or the backup is damaged.", language)
                            }
                        } finally {
                            sourcePassword.fill(' ')
                        }
                    }
                }) { Text(desktopText("Replace")) }
            },
        )
    }
}

/** Yesterday's and the day before's data, kept on this PC, with a way back. */
@Composable
private fun DailyBackups(snapshot: BackupSnapshot, dataStore: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    // Re-read the folder whenever the data changes (a restore or the daily copy changes it too).
    val backups = remember(snapshot) { dataStore.dailyBackups() }
    var restoring by remember { mutableStateOf<DesktopDailyBackup?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val undoAvailable = remember(snapshot, message) { dataStore.beforeRestoreFile.isFile }
    Group(
        "Daily backups",
        "Once a day this PC keeps a copy of the day before. Each copy is removed on its third day, so yesterday and the day before are always here.",
    ) {
        if (backups.isEmpty()) {
            SettingRow("No copies yet", "The first one is made tomorrow, when Life Assistant is open.")
        }
        backups.forEachIndexed { index, backup ->
            if (index > 0) RowDivider(inset = Space.xl)
            SettingRow(
                title = formatDeadline(backup.day.toEpochDay(), null, snapshot, language) + " · " + UserFormatting.formatDate(backup.day, snapshot.settings.dateFormat, locale),
                description = desktopText("Your data as it was at the end of that day") + " · " + if (backup.sizeBytes < 1_048_576) "${(backup.sizeBytes / 1024).coerceAtLeast(1)} KB" else "%.1f MB".format(backup.sizeBytes / 1_048_576.0),
                translate = false,
            ) {
                OutlinedButton(onClick = { restoring = backup }) { Text(desktopText("Restore")) }
            }
        }
        if (undoAvailable) {
            RowDivider(inset = Space.xl)
            SettingRow("Undo the last restore", "Puts back the data from right before you restored a daily copy.") {
                TextButton(onClick = {
                    scope.launch {
                        val result = dataStore.replaceFromEncrypted(dataStore.beforeRestoreFile)
                        if (result is DesktopReplaceResult.Applied) dataStore.beforeRestoreFile.delete()
                        message = desktopText(if (result is DesktopReplaceResult.Applied) "Restore undone." else "The restore could not be undone.", language)
                    }
                }) { Text(desktopText("Undo restore")) }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.sm)) }
    }
    restoring?.let { backup ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text(desktopText("Go back to this copy?")) },
            text = {
                Text(
                    UserFormatting.formatDate(backup.day, snapshot.settings.dateFormat, locale) + "\n\n" +
                        desktopText("Everything on this PC goes back to how it was at the end of that day. What you have now is kept, so you can undo this."),
                )
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text(desktopText("Cancel")) } },
            confirmButton = {
                Button(onClick = {
                    restoring = null
                    scope.launch {
                        val result = dataStore.restoreDailyBackup(backup)
                        message = desktopText(if (result is DesktopReplaceResult.Applied) "Restored. You can undo this below." else "The copy could not be restored.", language)
                    }
                }) { Text(desktopText("Restore")) }
            },
        )
    }
}

/** Chooses how an exported backup is locked: with the data password, or with its own password. */
@Composable
private fun ExportBackupDialog(passwordChosen: Boolean, onDismiss: () -> Unit, onExport: (CharArray?) -> Unit) {
    // A PC still on its own random key has no password the user knows, so the file needs one.
    var separate by remember { mutableStateOf(!passwordChosen) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = !separate || (password.length >= 8 && password == confirmation)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(desktopText("Export backup")) },
        text = {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (passwordChosen) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).selectable(!separate, role = Role.RadioButton) { separate = false }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(!separate, null, Modifier.padding(Space.sm))
                        Text(desktopText("Lock with my data password"))
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).selectable(separate, role = Role.RadioButton) { separate = true }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(separate, null, Modifier.padding(Space.sm))
                        Text(desktopText("Lock with a separate backup password"))
                    }
                } else {
                    Text(desktopText("Choose a password for this backup file. You need it to open the file later."))
                }
                if (separate) {
                    PasswordField(password, { password = it }, desktopText("Backup password"), Modifier.fillMaxWidth())
                    PasswordField(confirmation, { confirmation = it }, desktopText("Repeat the password"), Modifier.fillMaxWidth(), isError = confirmation.isNotEmpty() && confirmation != password)
                    Text(desktopText("At least 8 characters. Nobody can recover it for you."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } },
        confirmButton = {
            Button(enabled = valid, onClick = {
                val chosen = if (separate) password.toCharArray() else null
                password = ""
                confirmation = ""
                onExport(chosen)
            }) { Text(desktopText("Choose where to save")) }
        },
    )
}

// ---- Appearance ----------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(
    snapshot: BackupSnapshot,
    configStore: DesktopConfigStore,
    dataStore: DesktopDataStore,
    onUiLanguageChanged: (UiLanguage) -> Unit,
    visibleDestinations: Set<TopLevelDestination>,
    onVisibleDestinationsChanged: (Set<TopLevelDestination>) -> Unit,
    moduleLabel: (TopLevelDestination) -> String,
) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val settings = snapshot.settings
    Group("Look") {
        SettingRow("Theme") {
            Segmented(ThemeMode.entries, settings.themeMode, { scope.launch { dataStore.setThemeMode(it) } }, { desktopText(themeLabel(it)) })
        }
        RowDivider(inset = Space.xl)
        SettingRow("Accent color") {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                AccentColor.entries.forEach { accent ->
                    val color = accentOf(accent, LifeTheme.colors.dark)
                    val selected = settings.accentColor == accent
                    val name = desktopText(accent.name.lowercase().replaceFirstChar(Char::uppercase))
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .padding(4.dp).clip(CircleShape).background(color)
                            .selectable(selected, role = Role.RadioButton) { scope.launch { dataStore.setAccentColor(accent) } }
                            .semantics { contentDescription = name },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        RowDivider(inset = Space.xl)
        SettingRow("Language") {
            Segmented(UiLanguage.entries, language, { configStore.setUiLanguage(it); onUiLanguageChanged(it) }, { if (it == UiLanguage.ENGLISH) "English" else "简体中文" })
        }
    }
    Group("Menu", "Choose what shows in the menu. Hidden modules keep their data. At least one stays visible.") {
        FlowRow(Modifier.padding(horizontal = Space.xl, vertical = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            TopLevelDestination.entries.forEach { module ->
                val shown = module in visibleDestinations
                Pill(desktopText(moduleLabel(module)), shown, {
                    val next = if (shown) visibleDestinations - module else visibleDestinations + module
                    if (next.isNotEmpty()) onVisibleDestinationsChanged(next)
                })
            }
        }
    }
    Group("Dates and times") {
        SettingRow("Week starts on") {
            Segmented(WeekStart.entries, settings.weekStart, { scope.launch { dataStore.setWeekStart(it) } }, { desktopText(weekStartLabel(it)) })
        }
        RowDivider(inset = Space.xl)
        SettingRow("Time format") {
            Segmented(TimeFormatOption.entries, settings.timeFormat, { scope.launch { dataStore.setTimeFormat(it) } }, { desktopText(timeFormatLabel(it)) })
        }
        RowDivider(inset = Space.xl)
        SettingBlock("Date format", "Also the order dates are read in when you type them.") {
            Segmented(DateFormatOption.entries, settings.dateFormat, { scope.launch { dataStore.setDateFormat(it) } }, { dateFormatLabel(it, language) })
        }
    }
}

// ---- Reminders -----------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindersSection(settings: BackupSettings, configStore: DesktopConfigStore, dataStore: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val change: ((BackupSettings) -> BackupSettings) -> Unit = { transform -> scope.launch { dataStore.updateSettings(transform) } }
    var onThisPc by remember { mutableStateOf(configStore.desktopReminders()) }
    var keepInTray by remember { mutableStateOf(configStore.keepInTray()) }
    var allDayText by remember(settings.defaultAllDayReminderMinute) {
        mutableStateOf("%d:%02d".format(settings.defaultAllDayReminderMinute / 60, settings.defaultAllDayReminderMinute % 60))
    }
    Group("Notifications") {
        SettingRow("Reminder notifications", "For todos with a date. This setting is shared with your phone through sync.") {
            Switch(settings.notificationsEnabled, { enabled -> change { it.copy(notificationsEnabled = enabled) } })
        }
        RowDivider(inset = Space.xl)
        SettingRow("Show reminders on this PC", "As system notifications while Life Assistant is running.") {
            Switch(onThisPc, { enabled -> onThisPc = enabled; configStore.setDesktopReminders(enabled) }, enabled = settings.notificationsEnabled)
        }
        RowDivider(inset = Space.xl)
        SettingRow("Keep running in the tray when closed", "Reminders, sync and AI assistants keep working after the window is closed. Quit from the tray icon.") {
            Switch(keepInTray, { enabled -> keepInTray = enabled; configStore.setKeepInTray(enabled) })
        }
    }
    Group("Defaults for new todos") {
        SettingBlock("Remind me", "Added automatically to new todos that have a date.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReminderOffsetPreset.entries.forEach { preset ->
                    val on = preset.minutesBeforeDue in settings.defaultReminderOffsetsMinutes
                    Pill(desktopText(reminderLabel(preset)), on, {
                        change { current ->
                            current.copy(defaultReminderOffsetsMinutes = if (on) current.defaultReminderOffsetsMinutes - preset.minutesBeforeDue else current.defaultReminderOffsetsMinutes + preset.minutesBeforeDue)
                        }
                    })
                }
                settings.defaultReminderOffsetsMinutes.filter { offset -> ReminderOffsetPreset.entries.none { it.minutesBeforeDue == offset } }.sorted().forEach { custom ->
                    Pill(desktopReminderMinutes(custom, language), true, { change { it.copy(defaultReminderOffsetsMinutes = it.defaultReminderOffsetsMinutes - custom) } })
                }
            }
        }
        RowDivider(inset = Space.xl)
        SettingRow("Reminder time for todos without a time", "For example 9:00 for a reminder in the morning.") {
            LifeTextField(
                allDayText,
                { value ->
                    allDayText = value
                    parseTimeOfDay(value)?.let { minute -> change { it.copy(defaultAllDayReminderMinute = minute) } }
                },
                placeholder = "9:00",
                isError = parseTimeOfDay(allDayText) == null,
                modifier = Modifier.width(110.dp),
            )
        }
    }
}

// ---- Security ------------------------------------------------------------------------------

@Composable
private fun SecuritySection(
    cloudState: DesktopCloudUiState,
    configStore: DesktopConfigStore,
    dataStore: DesktopDataStore,
    credentials: DesktopCredentialStore,
    appLockEnabled: Boolean,
    appLockTimeout: AppLockTimeout,
    onAppLockChanged: (Boolean, AppLockTimeout) -> Unit,
) {
    var chosen by remember { mutableStateOf(configStore.passwordChosen()) }
    var remembered by remember { mutableStateOf(credentials.exists(DesktopCredentialStore.LOCAL_PASSWORD)) }
    var choosing by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    var verifyingForChange by remember { mutableStateOf(false) }
    var confirmDisableLock by remember { mutableStateOf(false) }
    var enablingLock by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }

    Group(
        "Password",
        "Your data is always stored encrypted on this PC. A password is only needed for the Vault, the app lock, sealed confessions and cloud sync.",
    ) {
        if (!chosen) {
            SettingRow("No password yet", DesktopPlatform.text("Life Assistant opens without asking. This PC keeps the key itself, protected by your Windows account.", "Life Assistant opens without asking. This PC keeps the key itself, protected by your Linux account.")) {
                Button(onClick = { choosing = true }) { Text(desktopText("Set a password")) }
            }
        } else {
            SettingRow(
                "Password is set",
                if (cloudState.connected) "To change it, disconnect cloud sync on every device first; they must all use the same password." else "Changing it re-encrypts the data on this PC.",
            ) {
                OutlinedButton(enabled = !cloudState.connected, onClick = { verifyingForChange = true }) { Text(desktopText("Change password")) }
            }
            RowDivider(inset = Space.xl)
            SettingRow("Open without asking", DesktopPlatform.text("This PC remembers the password, protected by your Windows account. Turn off to type it at every start.", "This PC remembers the password, protected by your Linux account. Turn off to type it at every start.")) {
                Switch(remembered, { on -> if (!on) confirmForget = true })
            }
        }
    }
    Group("App lock") {
        SettingRow("Lock when I step away", "Asks for the password when you return to the app.") {
            Switch(appLockEnabled, { enabled ->
                when {
                    // Turning the lock off needs the password, so an unattended PC cannot drop it.
                    !enabled -> confirmDisableLock = true
                    !chosen -> enablingLock = true
                    else -> onAppLockChanged(true, appLockTimeout)
                }
            })
        }
        if (appLockEnabled) {
            RowDivider(inset = Space.xl)
            SettingRow("Lock after leaving the app") {
                Segmented(AppLockTimeout.entries, appLockTimeout, { onAppLockChanged(true, it) }, { desktopText(it.desktopLabel) })
            }
        }
    }

    if (choosing || enablingLock) {
        ChoosePasswordDialog(
            title = "Choose a password",
            message = "It protects Vault, sealed confessions and the app lock, and encrypts cloud sync. Use the same one on your other devices.",
            onChosen = {
                chosen = true
                remembered = true
                if (enablingLock) onAppLockChanged(true, appLockTimeout)
                choosing = false
                enablingLock = false
            },
            onDismiss = { choosing = false; enablingLock = false },
        )
    }
    if (verifyingForChange) {
        DataPasswordDialog(
            title = "Change password",
            message = "Enter your current password first.",
            verify = dataStore::verifyPassword,
            onVerified = { verifyingForChange = false; changing = true },
            onDismiss = { verifyingForChange = false },
        )
    }
    if (changing) {
        ChoosePasswordDialog(
            title = "Choose a new password",
            message = "The data on this PC is encrypted again with the new password.",
            onChosen = { changing = false; remembered = true },
            onDismiss = { changing = false },
        )
    }
    if (confirmDisableLock) {
        DataPasswordDialog(
            title = "Turn off app lock",
            message = "Enter your data password to turn off the app lock.",
            verify = dataStore::verifyPassword,
            onVerified = { confirmDisableLock = false; onAppLockChanged(false, appLockTimeout) },
            onDismiss = { confirmDisableLock = false },
        )
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(desktopText("Ask for the password at every start?")) },
            text = { Text(desktopText("This PC forgets the password. From the next start on you type it to open your data. If you forget it, the data cannot be opened.")) },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text(desktopText("Cancel")) } },
            confirmButton = {
                Button(onClick = {
                    credentials.delete(DesktopCredentialStore.LOCAL_PASSWORD)
                    remembered = false
                    confirmForget = false
                }) { Text(desktopText("Forget password")) }
            },
        )
    }
}

internal val AppLockTimeout.desktopLabel: String
    get() = when (this) {
        AppLockTimeout.IMMEDIATELY -> "Immediately"
        AppLockTimeout.ONE_MINUTE -> "After 1 minute"
        AppLockTimeout.FIVE_MINUTES -> "After 5 minutes"
    }

// ---- AI assistants -------------------------------------------------------------------------

@Composable
private fun AiSection(configStore: DesktopConfigStore, agentActivity: StateFlow<List<DesktopAgentActivity>>) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    var access by remember { mutableStateOf(configStore.agentAccess()) }
    var changes by remember { mutableStateOf(configStore.agentChanges()) }
    val activity by agentActivity.collectAsDesktopState()
    val launcher = remember { DesktopAgentSetup.launcher() }
    var claudeDesktop by remember { mutableStateOf(DesktopAgentSetup.claudeDesktopConnected(launcher)) }
    var message by remember { mutableStateOf<String?>(null) }
    var showManual by remember { mutableStateOf(false) }

    Group(
        "Access",
        "AI assistants on this PC, such as Claude, can look things up for you and make changes. Ask things like: what is due this week, add milk to my todos, how much did I spend on food. The Vault and sealed confessions are never available to them.",
    ) {
        SettingRow("Let AI assistants use Life Assistant", "Off by default. Works while Life Assistant is running (also in the tray).") {
            Switch(access, { on -> access = on; configStore.setAgentAccess(on) })
        }
        RowDivider(inset = Space.xl)
        SettingRow("Allow changes", "When off, assistants can only read. When on, they can add, change, complete and delete todos, ledger entries, notes and diary pages.") {
            Switch(changes, { on -> changes = on; configStore.setAgentChanges(on) }, enabled = access)
        }
    }
    Group("Connect an assistant", "One click sets it up. Restart the assistant afterwards so it notices Life Assistant.") {
        if (launcher == null) {
            SettingRow("Available in the installed app", "This is a development build; install Life Assistant to connect assistants.")
        } else {
            SettingRow(
                "Claude Desktop",
                when {
                    claudeDesktop -> "Connected. Restart Claude Desktop if it was open."
                    DesktopAgentSetup.claudeDesktopInstalled() -> "Adds Life Assistant to Claude Desktop's settings."
                    else -> "Claude Desktop was not found on this PC."
                },
            ) {
                OutlinedButton(
                    enabled = DesktopAgentSetup.claudeDesktopInstalled() && !claudeDesktop,
                    onClick = {
                        val result = DesktopAgentSetup.connectClaudeDesktop(launcher)
                        claudeDesktop = DesktopAgentSetup.claudeDesktopConnected(launcher)
                        message = desktopText(if (result.isSuccess) "Connected to Claude Desktop. Restart it to finish." else "Could not change Claude Desktop's settings.", language)
                        if (result.isSuccess && !access) { access = true; configStore.setAgentAccess(true) }
                    },
                ) { Text(desktopText(if (claudeDesktop) "Connected" else "Connect")) }
            }
            RowDivider(inset = Space.xl)
            val claudeCode = remember { DesktopAgentSetup.claudeCodeCommand() }
            SettingRow("Claude Code", if (claudeCode != null) "Adds Life Assistant for all your projects." else "Claude Code was not found on this PC.") {
                OutlinedButton(
                    enabled = claudeCode != null,
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { DesktopAgentSetup.connectClaudeCode(launcher, claudeCode!!) }
                            message = desktopText(if (result.isSuccess) "Connected to Claude Code." else "Claude Code could not add Life Assistant.", language)
                            if (result.isSuccess && !access) { access = true; configStore.setAgentAccess(true) }
                        }
                    },
                ) { Text(desktopText("Connect")) }
            }
            RowDivider(inset = Space.xl)
            SettingRow("Other assistants", "Any assistant that supports MCP servers can use these settings.") {
                TextButton(onClick = { showManual = !showManual }) { Text(desktopText(if (showManual) "Hide" else "Show settings")) }
            }
            if (showManual) {
                val manual = remember { DesktopAgentSetup.manualConfig(launcher) }
                Column(Modifier.padding(horizontal = Space.xl).padding(bottom = Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SelectionContainer {
                        Text(
                            manual,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(Space.md),
                        )
                    }
                    OutlinedButton(onClick = { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(manual), null) }) { Text(desktopText("Copy")) }
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.sm)) }
    }
    Group("Recent changes by assistants", "Since Life Assistant was started.") {
        if (activity.isEmpty()) {
            SettingRow("Nothing yet")
        }
        activity.take(12).forEachIndexed { index, item ->
            if (index > 0) RowDivider(inset = Space.xl)
            SettingRow(
                title = DesktopAgentTools.TOOLS.firstOrNull { it.name == item.tool }?.title?.let { desktopText(it, language) } ?: item.tool,
                description = listOf(formatTimestamp(item.at), item.summary).filter(String::isNotBlank).joinToString(" · "),
                translate = false,
            )
        }
    }
}

// ---- About ---------------------------------------------------------------------------------

@Composable
private fun AboutSection() {
    val language = LocalUiLanguage.current
    var showLicense by remember { mutableStateOf(false) }
    Group {
        SettingRow(
            title = desktopText(AppIdentity.NAME) + " " + AppIdentity.VERSION,
            description = AppIdentity.AUTHOR + " · " + AppIdentity.COPYRIGHT,
            translate = false,
        )
        RowDivider(inset = Space.xl)
        SettingRow("Private and offline", "Your data stays on this PC unless you turn on cloud sync, and then it is encrypted before it leaves.")
        RowDivider(inset = Space.xl)
        SettingRow(desktopText("License") + ": " + AppIdentity.LICENSE_LABEL, desktopText("This software is provided without warranty."), translate = false) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedButton(onClick = { showLicense = true }) { Text(desktopText("View license")) }
                OutlinedButton(
                    enabled = Desktop.isDesktopSupported(),
                    onClick = { runCatching { Desktop.getDesktop().browse(URI(AppIdentity.SOURCE_URL)) } },
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(desktopText("Source code"))
                }
            }
        }
    }
    Text(desktopAppVersion(language), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = Space.xs))
    if (showLicense) {
        val legalText = remember { loadLegalResource(AppIdentity.LICENSE_RESOURCE) }
        val permissionText = remember { loadLegalResource(AppIdentity.PERMISSION_RESOURCE) }
        val noticeText = remember { loadLegalResource(AppIdentity.NOTICE_RESOURCE) }
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text(desktopText("License")) },
            text = {
                Column(Modifier.widthIn(max = 760.dp).heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(AppIdentity.LICENSE_LABEL, style = MaterialTheme.typography.titleMedium)
                    Text(legalText, style = MaterialTheme.typography.bodySmall)
                    Text(permissionText, style = MaterialTheme.typography.bodySmall)
                    Text(noticeText, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text(desktopText("Close")) } },
        )
    }
}

private fun loadLegalResource(path: String): String =
    Thread.currentThread().contextClassLoader.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: "$path is not available in this build."
