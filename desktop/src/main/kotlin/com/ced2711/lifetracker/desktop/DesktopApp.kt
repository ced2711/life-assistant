package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.AppIdentity
import com.ced2711.lifetracker.domain.model.AppLockTimeout
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.appLockExpired
import com.ced2711.lifetracker.domain.model.normalizeVisibleDestinations
import com.ced2711.lifetracker.domain.model.resolveVisibleDestination
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.PageTitle
import com.ced2711.lifetracker.ui.design.Pill
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.theme.LifeTheme
import com.ced2711.lifetracker.ui.theme.TaskLedgerTheme
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import javax.swing.JFileChooser
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private enum class DesktopDestination(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.WbSunny),
    TODO("Todo", Icons.Rounded.TaskAlt),
    LEDGER("Ledger", Icons.Rounded.AccountBalanceWallet),
    CALENDAR("Calendar", Icons.Rounded.CalendarMonth),
    NOTES("Notes", Icons.AutoMirrored.Rounded.Notes),
    DIARY("Diary", Icons.Rounded.AutoStories),
    CONFESSIONAL("Confessional", Icons.Rounded.LocalFireDepartment),
    VAULT("Vault", Icons.Rounded.Lock),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

/** Small count shown next to a module in the sidebar; Today's is red while something is overdue. */
private fun sidebarItem(destination: DesktopDestination, snapshot: BackupSnapshot): SidebarItem {
    val today = LocalDate.now().toEpochDay()
    val open = snapshot.todos.filter { it.deletedAt == null && it.completedAt == null }
    return when (destination) {
        DesktopDestination.TODAY -> {
            val due = open.count { (it.deadlineEpochDay ?: Long.MAX_VALUE) <= today }
            SidebarItem(destination.label, destination.icon, due.takeIf { it > 0 }?.toString(), badgeAlert = open.any { (it.deadlineEpochDay ?: Long.MAX_VALUE) < today })
        }
        DesktopDestination.NOTES -> SidebarItem(destination.label, destination.icon, snapshot.notes.size.takeIf { it > 0 }?.toString())
        else -> SidebarItem(destination.label, destination.icon)
    }
}

private val LocalDesktopErrorReporter = staticCompositionLocalOf<(Throwable) -> Unit> { {} }

/**
 * The whole desktop app. It opens straight into the data: a new PC keeps its own random key, and
 * a PC whose password is remembered uses that. Only data whose password is not remembered asks.
 */
@Composable
fun LifeTrackerDesktopApp(onShowWindow: () -> Unit = {}) {
    val dataStore = remember { DesktopDataStore() }
    val credentials = remember { DesktopCredentialStore() }
    val config = remember { DesktopConfigStore() }
    val oauth = remember { DesktopGoogleOAuth(config, credentials) }
    val cloud = remember { DesktopCloudSyncController(dataStore, config, oauth, credentials = credentials) }
    val storeState by dataStore.state.collectAsDesktopState()
    var uiLanguage by remember { mutableStateOf(config.read().uiLanguage) }
    var appError by remember { mutableStateOf<String?>(null) }
    val reportError: (Throwable) -> Unit = remember(uiLanguage) {
        { error -> appError = desktopErrorMessage(error, uiLanguage) }
    }
    val rootHandler = remember(reportError) {
        CoroutineExceptionHandler { _, error -> reportError(error) }
    }
    val scope = rememberCoroutineScope { rootHandler }
    // Optional app lock. It re-asks for the data password even when this PC remembers it.
    var appLockEnabled by remember { mutableStateOf(config.read().appLockEnabled) }
    var appLockTimeout by remember { mutableStateOf(config.read().appLockTimeout) }
    var appLocked by remember { mutableStateOf(false) }
    var unfocusedAt by remember { mutableStateOf<Long?>(null) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        val now = System.nanoTime() / 1_000_000
        if (!windowFocused) {
            if (!appLocked) unfocusedAt = now
        } else {
            if (appLockEnabled && appLockExpired(unfocusedAt, now, appLockTimeout)) appLocked = true
            unfocusedAt = null
            cloud.onWindowFocused()
        }
    }

    DisposableEffect(Unit) {
        onDispose { dataStore.close() }
    }
    // AI assistants (through `--mcp`) and a second start of the app reach this window here.
    val agentLocked by rememberUpdatedState(appLockEnabled && appLocked)
    val agentServer = remember {
        DesktopAgentServer(
            appDirectory = dataStore.appDirectory,
            tools = DesktopAgentTools(dataStore),
            accessEnabled = config::agentAccess,
            changesAllowed = config::agentChanges,
            locked = { agentLocked || dataStore.currentSnapshot() == null },
            onShowWindow = onShowWindow,
        )
    }
    DisposableEffect(agentServer) {
        runCatching { agentServer.start() }
        onDispose { agentServer.stop() }
    }
    // Nothing is asked at start: the remembered password, or a new PC's own random one, opens the data.
    var starting by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (DesktopLocalKey.openWithoutAsking(dataStore, credentials, config)) {
            appLocked = appLockEnabled
            cloud.start(scope)
        }
        starting = false
    }
    val security = remember {
        DesktopSecurity(
            passwordChosen = config::passwordChosen,
            verify = dataStore::verifyPassword,
            choose = { password -> DesktopLocalKey.choose(password, dataStore, credentials, config) },
        )
    }

    val accent = (storeState as? DesktopStoreState.Open)?.snapshot?.settings?.accentColor ?: AccentColor.TEAL
    val theme = (storeState as? DesktopStoreState.Open)?.snapshot?.settings?.themeMode ?: ThemeMode.DARK
    CompositionLocalProvider(LocalDesktopErrorReporter provides reportError, LocalUiLanguage provides uiLanguage, LocalDesktopSecurity provides security) {
        TaskLedgerTheme(theme, accent) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val unlock: (CharArray, Boolean) -> Unit = { password, rememberOnPc ->
                    scope.launch {
                        if (dataStore.open(password.copyOf())) {
                            appLocked = false
                            if (rememberOnPc) credentials.save(DesktopCredentialStore.LOCAL_PASSWORD, password.copyOf())
                            cloud.start(scope)
                        }
                        password.fill(' ')
                    }
                }
                when (val current = storeState) {
                    DesktopStoreState.Locked -> if (starting) Box(Modifier.fillMaxSize()) else UnlockScreen(error = null, onUnlock = unlock)
                    is DesktopStoreState.Error -> UnlockScreen(error = current.message, onUnlock = unlock)
                    is DesktopStoreState.Open -> if (appLockEnabled && appLocked) {
                        DesktopAppLockScreen(verify = dataStore::verifyPassword, onUnlocked = { appLocked = false })
                    } else {
                        DesktopHome(
                            snapshot = current.snapshot,
                            dataStore = dataStore,
                            cloud = cloud,
                            configStore = config,
                            credentials = credentials,
                            agentActivity = agentServer.activity,
                            onUiLanguageChanged = { uiLanguage = it },
                            appLockEnabled = appLockEnabled,
                            appLockTimeout = appLockTimeout,
                            onAppLockChanged = { enabled, timeout ->
                                config.setAppLock(enabled, timeout)
                                appLockEnabled = enabled
                                appLockTimeout = timeout
                            },
                        )
                    }
                }
            }
            appError?.let { message ->
                AlertDialog(
                    onDismissRequest = { appError = null },
                    title = { Text(desktopText("Operation failed")) },
                    text = { Text(message) },
                    confirmButton = { Button(onClick = { appError = null }) { Text(desktopText("OK")) } },
                )
            }
        }
    }
}

/** Shown only when existing data cannot be opened without the password (it is not remembered). */
@Composable
private fun UnlockScreen(error: String?, onUnlock: (CharArray, Boolean) -> Unit) {
    var password by remember { mutableStateOf("") }
    var rememberOnPc by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun submit() {
        if (password.length < 8) return
        val transferred = password.toCharArray()
        password = ""
        onUnlock(transferred, rememberOnPc)
    }
    CenteredCard {
        Icon(Icons.Rounded.TaskAlt, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(desktopText(AppIdentity.NAME), style = MaterialTheme.typography.headlineSmall)
        Text(desktopText("Enter your data password to open your data."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        PasswordField(password, { password = it }, desktopText("Data password"), Modifier.fillMaxWidth().focusRequester(focus).onEnter(::submit), isError = error != null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(rememberOnPc, { rememberOnPc = it })
            Text(desktopText("Remember on this PC, so it opens without asking"), style = MaterialTheme.typography.bodyMedium)
        }
        if (error != null) Text(desktopText(error), color = LifeTheme.colors.danger, style = MaterialTheme.typography.bodySmall)
        Button(enabled = password.length >= 8, onClick = ::submit, modifier = Modifier.fillMaxWidth()) { Text(desktopText("Unlock")) }
        Text(
            desktopText(DesktopPlatform.text("Your password is never uploaded. If remembered, it is protected by Windows DPAPI for this Windows account.", "Your password is never uploaded. If remembered, it is encrypted with a key kept in your desktop keyring or a file only you can read.")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DesktopHome(
    snapshot: BackupSnapshot,
    dataStore: DesktopDataStore,
    cloud: DesktopCloudSyncController,
    configStore: DesktopConfigStore,
    credentials: DesktopCredentialStore,
    agentActivity: StateFlow<List<DesktopAgentActivity>>,
    onUiLanguageChanged: (UiLanguage) -> Unit,
    appLockEnabled: Boolean,
    appLockTimeout: AppLockTimeout,
    onAppLockChanged: (Boolean, AppLockTimeout) -> Unit,
) {
    var visibleDestinations by remember { mutableStateOf(normalizeVisibleDestinations(configStore.read().visibleDestinations)) }
    val configuredDestination = resolveVisibleDestination(configStore.read().lastDestination, visibleDestinations)
        .toDesktopDestination()
    var destination by remember { mutableStateOf(configuredDestination) }
    var requestedDiaryDay by remember { mutableStateOf<Long?>(null) }
    var requestedNoteId by remember { mutableStateOf<Long?>(null) }
    val confessionStore = remember { DesktopConfessionStore() }
    val scope = rememberSafeCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val cloudState by cloud.state.collectAsDesktopState()
    val language = LocalUiLanguage.current

    LaunchedEffect(cloudState.message) {
        cloudState.message?.let {
            snackbar.showSnackbar(desktopText(it, language))
            cloud.acknowledgeMessage()
        }
    }

    val mainDestinations = visibleDestinations.map { it.toDesktopDestination() }
    fun navigate(to: DesktopDestination) {
        destination = to
        if (to in mainDestinations) configStore.setLastDestination(to.toTopLevelDestination())
    }
    var sidebarCollapsed by remember { mutableStateOf(configStore.sidebarCollapsed()) }
    val toggleSidebar: () -> Unit = {
        sidebarCollapsed = !sidebarCollapsed
        configStore.setSidebarCollapsed(sidebarCollapsed)
    }
    // Ctrl+1…9 opens the modules in menu order, Ctrl+R syncs, Ctrl+, opens Settings, Ctrl+B folds the menu.
    val shortcuts = LocalDesktopShortcuts.current
    DisposableEffect(shortcuts, mainDestinations) {
        shortcuts.onNavigate = { index -> mainDestinations.getOrNull(index)?.let(::navigate) }
        shortcuts.onSync = { cloud.launch { synchronize() } }
        shortcuts.onSettings = { destination = DesktopDestination.SETTINGS }
        shortcuts.onToggleSidebar = toggleSidebar
        onDispose {
            shortcuts.onToggleSidebar = null
            shortcuts.onNavigate = null
            shortcuts.onSync = null
            shortcuts.onSettings = null
        }
    }
    // Todo reminders become system notifications while the app runs (missed ones up to an hour late).
    val notifier = LocalDesktopNotifier.current
    LaunchedEffect(dataStore, notifier) {
        if (notifier == null) return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            val from = maxOf(configStore.remindersCheckedAt() ?: now, now - MISSED_REMINDER_GRACE_MILLIS)
            val current = dataStore.currentSnapshot()
            if (current != null && current.settings.notificationsEnabled && configStore.desktopReminders()) {
                dueReminders(current, from, now).forEach { reminder ->
                    notifier.notify(reminder.title, desktopReminderBody(reminder.dueAtMillis, current, language))
                }
            }
            configStore.setRemindersCheckedAt(now)
            kotlinx.coroutines.delay(30_000)
        }
    }
    // Due recurring items appear and expired deletions are cleared, also across midnight.
    LaunchedEffect(dataStore) {
        while (true) {
            // The first run of a day keeps yesterday's data, before anything changes today.
            runCatching { dataStore.backUpDaily(LocalDate.now()) }
            runCatching { dataStore.runMaintenance() }
            kotlinx.coroutines.delay(60_000)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Wide windows get a labelled sidebar unless it was folded; narrow ones a compact icon rail.
        val roomy = maxWidth >= 1_100.dp
        val expanded = roomy && !sidebarCollapsed
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                DesktopSidebar(
                    expanded = expanded,
                    destinations = mainDestinations.map { sidebarItem(it, snapshot) },
                    selectedIndex = mainDestinations.indexOf(destination),
                    onSelect = { navigate(mainDestinations[it]) },
                    vaultSelected = destination == DesktopDestination.VAULT,
                    settingsSelected = destination == DesktopDestination.SETTINGS,
                    onVault = { destination = DesktopDestination.VAULT },
                    onSettings = { destination = DesktopDestination.SETTINGS },
                    onToggle = toggleSidebar.takeIf { roomy },
                    syncStatus = {
                        DesktopSyncStatusButton(
                            state = cloudState,
                            onSync = { cloud.launch { synchronize() } },
                            onOpenSettings = { destination = DesktopDestination.SETTINGS },
                            expanded = expanded,
                        )
                    },
                )
                ColumnDivider()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    when (destination) {
                        DesktopDestination.TODAY -> TodayPage(
                            snapshot = snapshot,
                            store = dataStore,
                            showDiary = DesktopDestination.DIARY in mainDestinations,
                            showNotes = DesktopDestination.NOTES in mainDestinations,
                            showLedger = DesktopDestination.LEDGER in mainDestinations,
                            onOpenDiary = { day ->
                                requestedDiaryDay = day
                                navigate(DesktopDestination.DIARY)
                            },
                            onOpenNote = { id ->
                                requestedNoteId = id
                                navigate(DesktopDestination.NOTES)
                            },
                            onOpenLedger = { navigate(DesktopDestination.LEDGER) },
                        )
                        DesktopDestination.TODO -> TodoPage(snapshot, dataStore)
                        DesktopDestination.LEDGER -> LedgerPage(snapshot, dataStore)
                        DesktopDestination.CALENDAR -> CalendarPage(
                            snapshot = snapshot,
                            store = dataStore,
                            showDiary = DesktopDestination.DIARY in mainDestinations,
                            onOpenDiary = { day ->
                                requestedDiaryDay = day
                                navigate(DesktopDestination.DIARY)
                            },
                        )
                        DesktopDestination.DIARY -> DiaryPage(snapshot, dataStore, requestedDiaryDay) { requestedDiaryDay = null }
                        DesktopDestination.CONFESSIONAL -> ConfessionalPage(confessionStore, dataStore::verifyPassword)
                        DesktopDestination.NOTES -> NotesPage(snapshot, dataStore, requestedNoteId, { requestedNoteId = null }) { destination = DesktopDestination.VAULT }
                        DesktopDestination.VAULT -> VaultPage(snapshot, dataStore)
                        DesktopDestination.SETTINGS -> SettingsPage(
                            snapshot = snapshot,
                            cloudState = cloudState,
                            cloud = cloud,
                            configStore = configStore,
                            dataStore = dataStore,
                            credentials = credentials,
                            agentActivity = agentActivity,
                            onUiLanguageChanged = onUiLanguageChanged,
                            visibleDestinations = visibleDestinations.toSet(),
                            onVisibleDestinationsChanged = { selected ->
                                configStore.setVisibleDestinations(selected)
                                visibleDestinations = normalizeVisibleDestinations(selected)
                            },
                            moduleLabel = { it.toDesktopDestination().label },
                            appLockEnabled = appLockEnabled,
                            appLockTimeout = appLockTimeout,
                            onAppLockChanged = onAppLockChanged,
                        )
                    }
                }
            }
        }

        cloudState.conflict?.let {
            AlertDialog(
                onDismissRequest = cloud::dismissConflict,
                title = { Text(desktopText("Sync conflict")) },
                text = { Text(desktopText("This PC and the cloud could not be merged automatically, for example because the cloud uses a different data password. Use newest cloud replaces this PC's data; Keep this PC uploads this PC's data. Previous encrypted cloud versions are kept.")) },
                dismissButton = { TextButton(onClick = cloud::dismissConflict) { Text(desktopText("Cancel")) } },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { scope.launch { cloud.synchronize(ConflictResolution.USE_CLOUD) } }) {
                            Text(desktopText("Use newest cloud"))
                        }
                        Button(onClick = { scope.launch { cloud.synchronize(ConflictResolution.KEEP_LOCAL) } }) {
                            Text(desktopText("Keep this PC"))
                        }
                    }
                },
            )
        }
        cloudState.gitHubCode?.let { code -> GitHubCodeDialog(code.userCode, code.verificationUri, cloud::cancelGitHubConnect) }
        // Connecting sync asks for the password all devices share, only when it is needed.
        cloudState.passwordPrompt?.let { prompt ->
            when (prompt.kind) {
                DesktopPasswordPromptKind.CHOOSE -> ChoosePasswordDialog(
                    title = "Choose your sync password",
                    message = "Everything is encrypted with this password before it leaves this PC. Enter the same one when you connect your phone.",
                    onChosen = {},
                    onDismiss = cloud::cancelSyncPassword,
                    onSubmit = cloud::submitSyncPassword,
                )
                DesktopPasswordPromptKind.MATCH, DesktopPasswordPromptKind.MATCH_RETRY -> SyncPasswordDialog(
                    retry = prompt.kind == DesktopPasswordPromptKind.MATCH_RETRY,
                    onSubmit = cloud::submitSyncPassword,
                    onDismiss = cloud::cancelSyncPassword,
                )
            }
        }
    }
}

/** Asks for the password the user's other devices already sync with. */
@Composable
private fun SyncPasswordDialog(retry: Boolean, onSubmit: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by remember(retry) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(retry) { runCatching { focus.requestFocus() } }
    fun submit() {
        if (password.length < 8) return
        val typed = password.toCharArray()
        password = ""
        onSubmit(typed)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(desktopText("Enter your sync password")) },
        text = {
            Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(desktopText("Your cloud already holds data from another device. Enter the password that device uses (on the phone: the sync password), so this PC can read it."))
                PasswordField(password, { password = it }, desktopText("Sync password"), Modifier.fillMaxWidth().focusRequester(focus).onEnter(::submit), isError = retry)
                if (retry) Text(desktopText("That password does not open the cloud data. Try again."), color = LifeTheme.colors.danger, style = MaterialTheme.typography.bodySmall)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } },
        confirmButton = { Button(enabled = password.length >= 8, onClick = ::submit) { Text(desktopText("Continue")) } },
    )
}

@Composable
internal fun PageHeader(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    PageTitle(
        title = desktopText(title),
        subtitle = subtitle,
        actions = actions,
        modifier = Modifier.padding(start = PagePadding, end = PagePadding, top = 28.dp, bottom = 16.dp),
    )
}

/** A choice pill (see Pill in the shared design); a check box or radio button for screen readers. */
@Composable
internal fun FilterChipSimple(label: String, selected: Boolean, exclusive: Boolean = false, onClick: () -> Unit) {
    Pill(text = label, selected = selected, onClick = onClick, exclusive = exclusive)
}

@Composable
internal fun SimpleNameDialog(title: String, onDismiss: () -> Unit, initial: String = "", onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LifeTextField(
                value, { value = it },
                placeholder = desktopText("Name"),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onEnter { if (value.isNotBlank()) onSave(value.trim()) },
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } },
        confirmButton = { Button(enabled = value.isNotBlank(), onClick = { onSave(value.trim()) }) { Text(desktopText("Save")) } },
    )
}

internal fun chooseAndAttach(scope: CoroutineScope, store: DesktopDataStore, ownerType: AttachmentOwnerType, ownerId: Long) {
    scope.launch {
        val chooser = JFileChooser()
        val selected = chooser.takeIf { it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION }?.selectedFile
        if (selected != null) store.attachFile(ownerType, ownerId, selected)
    }
}

/** The files of a todo, ledger entry or note: open, save a copy, or remove (after asking). */
@Composable
internal fun AttachmentList(
    snapshot: BackupSnapshot,
    ownerType: AttachmentOwnerType,
    ownerId: Long,
    store: DesktopDataStore,
) {
    val scope = rememberSafeCoroutineScope()
    var removing by remember { mutableStateOf<Long?>(null) }
    // Attachments waiting for removal after a delete are not shown.
    val attachments = snapshot.attachments.filter { it.ownerType == ownerType && it.ownerId == ownerId && it.pendingDeleteAt == null }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        attachments.forEach { attachment ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = Space.md, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.InsertDriveFile, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(attachment.originalName, modifier = Modifier.weight(1f).padding(horizontal = Space.sm), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(
                    onClick = {
                        store.attachmentFile(attachment.id)?.let { file ->
                            scope.launch(Dispatchers.IO) { runCatching { Desktop.getDesktop().open(file) } }
                        }
                    },
                ) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, desktopText("Open attachment"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(
                    onClick = {
                        val chooser = JFileChooser().apply { selectedFile = File(attachment.originalName) }
                        val destination = chooser.takeIf { it.showSaveDialog(null) == JFileChooser.APPROVE_OPTION }?.selectedFile
                        val source = store.attachmentFile(attachment.id)
                        if (source != null && destination != null) {
                            scope.launch(Dispatchers.IO) { source.copyTo(destination, overwrite = true) }
                        }
                    },
                ) { Icon(Icons.Rounded.Download, desktopText("Save a copy"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { removing = attachment.id }) { Icon(Icons.Rounded.DeleteOutline, desktopText("Remove attachment"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    removing?.let { id ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(desktopText("Remove this file?")) },
            text = { Text(attachments.firstOrNull { it.id == id }?.originalName.orEmpty()) },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(desktopText("Cancel")) } },
            confirmButton = { Button(onClick = { removing = null; scope.launch { store.removeAttachment(id) } }) { Text(desktopText("Remove")) } },
        )
    }
}

internal fun categoryPath(categoryId: Long, snapshot: BackupSnapshot): String {
    val byId = snapshot.categories.associateBy { it.id }
    val path = mutableListOf<String>()
    val visited = mutableSetOf<Long>()
    var cursor: Long? = categoryId
    while (cursor != null && visited.add(cursor)) {
        val category = byId[cursor] ?: break
        path += category.name
        cursor = category.parentId
    }
    return path.asReversed().joinToString(" / ")
}

internal fun noteFolderPath(folderId: Long, snapshot: BackupSnapshot): String {
    val byId = snapshot.noteFolders.associateBy { it.id }
    val path = mutableListOf<String>()
    val visited = mutableSetOf<Long>()
    var cursor: Long? = folderId
    while (cursor != null && visited.add(cursor)) {
        val folder = byId[cursor] ?: break
        path += folder.name
        cursor = folder.parentId
    }
    return path.asReversed().joinToString(" / ")
}

private fun DesktopDestination.toTopLevelDestination(): TopLevelDestination = when (this) {
    DesktopDestination.TODAY -> TopLevelDestination.TODAY
    DesktopDestination.TODO -> TopLevelDestination.TODO
    DesktopDestination.LEDGER -> TopLevelDestination.LEDGER
    DesktopDestination.CALENDAR -> TopLevelDestination.CALENDAR
    DesktopDestination.NOTES -> TopLevelDestination.NOTES
    DesktopDestination.DIARY -> TopLevelDestination.DIARY
    DesktopDestination.CONFESSIONAL -> TopLevelDestination.CONFESSIONAL
    DesktopDestination.VAULT, DesktopDestination.SETTINGS -> TopLevelDestination.TODAY
}

private fun TopLevelDestination.toDesktopDestination(): DesktopDestination = when (this) {
    TopLevelDestination.TODAY -> DesktopDestination.TODAY
    TopLevelDestination.TODO -> DesktopDestination.TODO
    TopLevelDestination.LEDGER -> DesktopDestination.LEDGER
    TopLevelDestination.CALENDAR -> DesktopDestination.CALENDAR
    TopLevelDestination.NOTES -> DesktopDestination.NOTES
    TopLevelDestination.DIARY -> DesktopDestination.DIARY
    TopLevelDestination.CONFESSIONAL -> DesktopDestination.CONFESSIONAL
}

@Composable
internal fun rememberSafeCoroutineScope(): CoroutineScope {
    val reportError = LocalDesktopErrorReporter.current
    val handler = remember(reportError) {
        CoroutineExceptionHandler { _, error -> reportError(error) }
    }
    return rememberCoroutineScope { handler }
}

private fun desktopErrorMessage(error: Throwable, language: UiLanguage = UiLanguage.ENGLISH): String = when (error) {
    is IOException -> desktopText("The file operation could not be completed. Check available storage and file access, then try again.", language)
    is IllegalArgumentException -> error.message?.takeIf { it.isNotBlank() }?.take(220)
        ?: desktopText("One of the entered values is invalid.", language)
    else -> desktopText("The operation could not be completed. Your last saved data was kept.", language)
}

internal fun formatMoney(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.US).apply { currency = Currency.getInstance("USD") }.format(cents / 100.0)

internal fun formatTimestamp(timestamp: Long): String = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US).format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

internal fun parseAmountCents(value: String): Long? {
    val decimal = value.toBigDecimalOrNull() ?: return null
    if (decimal.signum() <= 0 || decimal.scale() !in 0..2) return null
    return runCatching { decimal.movePointRight(2).longValueExact() }
        .getOrNull()
        ?.takeIf { it in 1..99_999_999_999L }
}

@Composable
internal fun <T> StateFlow<T>.collectAsDesktopState(): androidx.compose.runtime.State<T> = collectAsState()
