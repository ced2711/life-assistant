package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.AppIdentity
import com.ced2711.lifetracker.domain.model.AppLockTimeout
import com.ced2711.lifetracker.domain.model.appLockExpired
import com.ced2711.lifetracker.domain.model.diaryPreview
import com.ced2711.lifetracker.domain.model.normalizeVisibleDestinations
import com.ced2711.lifetracker.domain.model.resolveVisibleDestination
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.domain.model.WeekStart
import com.ced2711.lifetracker.domain.date.SmartDateParser
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.TaskLedgerTheme
import java.io.File
import java.io.IOException
import java.awt.Desktop
import java.net.URI
import java.text.NumberFormat
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Currency
import java.util.Locale
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DesktopDestination(val label: String, val icon: ImageVector) {
    TODO("Todo", Icons.Default.TaskAlt),
    LEDGER("Ledger", Icons.Default.Payments),
    CALENDAR("Calendar", Icons.Default.CalendarMonth),
    NOTES("Notes", Icons.AutoMirrored.Filled.Notes),
    DIARY("Diary", Icons.Default.Book),
    CONFESSIONAL("Confessional", Icons.Default.LocalFireDepartment),
    VAULT("Vault", Icons.Default.Lock),
    SETTINGS("Settings", Icons.Default.Settings),
}

/** Small count shown next to a module in the sidebar. */
private fun sidebarBadge(destination: DesktopDestination, snapshot: BackupSnapshot): String? = when (destination) {
    DesktopDestination.TODO -> snapshot.todos.count { it.deletedAt == null && it.completedAt == null }.takeIf { it > 0 }?.toString()
    DesktopDestination.NOTES -> snapshot.notes.size.takeIf { it > 0 }?.toString()
    else -> null
}

private val LocalDesktopErrorReporter = staticCompositionLocalOf<(Throwable) -> Unit> { {} }

private fun lifeTrackerColors(accentColor: AccentColor): ColorScheme = darkColorScheme(
    primary = when (accentColor) {
        AccentColor.TEAL -> Color(0xFF5FD1C6)
        AccentColor.BLUE -> Color(0xFF87B9FF)
        AccentColor.VIOLET -> Color(0xFFC6A7FF)
        AccentColor.ROSE -> Color(0xFFFFA9C2)
        AccentColor.ORANGE -> Color(0xFFFFB673)
        AccentColor.GREEN -> Color(0xFF78D993)
    },
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF31413F),
    onPrimaryContainer = Color(0xFFE0F3EF),
    secondary = Color(0xFFB0CCC8),
    background = Color(0xFF111414),
    surface = Color(0xFF171A1A),
    surfaceVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB4AB),
)

@Composable
fun LifeTrackerDesktopApp() {
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
    // Optional app lock. It re-asks for the data password even when Windows remembers it.
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
    LaunchedEffect(Unit) {
        val saved = credentials.load(DesktopCredentialStore.LOCAL_PASSWORD)
        if (saved != null) {
            if (dataStore.open(saved.copyOf())) {
                appLocked = appLockEnabled
                cloud.start(scope)
            }
            saved.fill('\u0000')
        }
    }

    val accent = (storeState as? DesktopStoreState.Open)?.snapshot?.settings?.accentColor ?: AccentColor.TEAL
    val theme = (storeState as? DesktopStoreState.Open)?.snapshot?.settings?.themeMode ?: ThemeMode.DARK
    CompositionLocalProvider(LocalDesktopErrorReporter provides reportError) {
        CompositionLocalProvider(LocalUiLanguage provides uiLanguage) {
            TaskLedgerTheme(theme, accent) {
            Surface(Modifier.fillMaxSize()) {
                when (val current = storeState) {
                DesktopStoreState.Locked -> UnlockScreen(
                    error = null,
                    existingData = dataStore.encryptedFile.isFile,
                    onUnlock = { password, rememberOnPc ->
                        scope.launch {
                            val copyForStore = password.copyOf()
                            val opened = dataStore.open(copyForStore)
                            if (opened) {
                                appLocked = false
                                if (rememberOnPc) {
                                    credentials.save(
                                        DesktopCredentialStore.LOCAL_PASSWORD,
                                        password.copyOf(),
                                    )
                                }
                                cloud.start(scope)
                            }
                            password.fill('\u0000')
                        }
                    },
                )
                is DesktopStoreState.Error -> UnlockScreen(
                    error = current.message,
                    existingData = dataStore.encryptedFile.isFile,
                    onUnlock = { password, rememberOnPc ->
                        scope.launch {
                            val opened = dataStore.open(password.copyOf())
                            if (opened) {
                                appLocked = false
                                if (rememberOnPc) credentials.save(
                                    DesktopCredentialStore.LOCAL_PASSWORD,
                                    password.copyOf(),
                                )
                                cloud.start(scope)
                            }
                            password.fill('\u0000')
                        }
                    },
                )
                is DesktopStoreState.Open -> if (appLockEnabled && appLocked) {
                    DesktopAppLockScreen(verify = dataStore::verifyPassword, onUnlocked = { appLocked = false })
                } else DesktopHome(
                    snapshot = current.snapshot,
                    dataStore = dataStore,
                    cloud = cloud,
                    configStore = config,
                    credentials = credentials,
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
}

@Composable
private fun UnlockScreen(
    error: String?,
    existingData: Boolean,
    onUnlock: (CharArray, Boolean) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var rememberOnPc by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 460.dp).padding(24.dp)) {
            Column(
                Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(desktopText(AppIdentity.NAME), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    desktopText(if (existingData) "Unlock your encrypted local data." else
                        "Create an encrypted local data file. Use this same password for Google Drive sync."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(desktopText("Data password")) },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rememberOnPc, { rememberOnPc = it })
                    Text(desktopText(DesktopPlatform.text("Remember securely with Windows", "Remember securely on this computer")))
                }
                if (error != null) Text(desktopText(error), color = MaterialTheme.colorScheme.error)
                Button(
                    enabled = password.length >= 8,
                    onClick = {
                        val transferred = password.toCharArray()
                        password = ""
                        onUnlock(transferred, rememberOnPc)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(desktopText(if (existingData) "Unlock" else "Create local data"))
                }
                Text(
                    desktopText(DesktopPlatform.text("Your password is never uploaded. If remembered, it is protected by Windows DPAPI for this Windows account.", "Your password is never uploaded. If remembered, it is encrypted with a key kept in your desktop keyring or a file only you can read.")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DesktopHome(
    snapshot: BackupSnapshot,
    dataStore: DesktopDataStore,
    cloud: DesktopCloudSyncController,
    configStore: DesktopConfigStore,
    credentials: DesktopCredentialStore,
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
    // Ctrl+1…9 opens the modules in menu order, Ctrl+R syncs, Ctrl+, opens Settings.
    val shortcuts = LocalDesktopShortcuts.current
    DisposableEffect(shortcuts, mainDestinations) {
        shortcuts.onNavigate = { index -> mainDestinations.getOrNull(index)?.let(::navigate) }
        shortcuts.onSync = { cloud.launch { synchronize() } }
        shortcuts.onSettings = { destination = DesktopDestination.SETTINGS }
        onDispose {
            shortcuts.onNavigate = null
            shortcuts.onSync = null
            shortcuts.onSettings = null
        }
    }
    // Due recurring items appear and expired deletions are cleared, also across midnight.
    LaunchedEffect(dataStore) {
        while (true) {
            runCatching { dataStore.runMaintenance() }
            kotlinx.coroutines.delay(60_000)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Wide windows get a labelled sidebar; narrow ones a compact icon rail.
        val expanded = maxWidth >= 1_100.dp
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                DesktopSidebar(
                    expanded = expanded,
                    destinations = mainDestinations.map { SidebarItem(it.label, it.icon, sidebarBadge(it, snapshot)) },
                    selectedIndex = mainDestinations.indexOf(destination),
                    onSelect = { navigate(mainDestinations[it]) },
                    vaultSelected = destination == DesktopDestination.VAULT,
                    settingsSelected = destination == DesktopDestination.SETTINGS,
                    onVault = { destination = DesktopDestination.VAULT },
                    onSettings = { destination = DesktopDestination.SETTINGS },
                    syncStatus = {
                        DesktopSyncStatusButton(
                            state = cloudState,
                            onSync = { cloud.launch { synchronize() } },
                            onOpenSettings = { destination = DesktopDestination.SETTINGS },
                        )
                    },
                )
                VerticalDivider()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    when (destination) {
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
                        DesktopDestination.NOTES -> NotesPage(snapshot, dataStore) { destination = DesktopDestination.VAULT }
                        DesktopDestination.VAULT -> VaultPage(snapshot, dataStore)
                        DesktopDestination.SETTINGS -> SettingsPage(
                            cloudState = cloudState,
                            cloud = cloud,
                            config = configStore.read(),
                            configStore = configStore,
                            dataStore = dataStore,
                            openVault = { destination = DesktopDestination.VAULT },
                            onUiLanguageChanged = onUiLanguageChanged,
                            forgetLocalPassword = { credentials.delete(DesktopCredentialStore.LOCAL_PASSWORD) },
                            visibleDestinations = visibleDestinations.toSet(),
                            onVisibleDestinationsChanged = { selected ->
                                configStore.setVisibleDestinations(selected)
                                visibleDestinations = normalizeVisibleDestinations(selected)
                            },
                            appLockEnabled = appLockEnabled,
                            appLockTimeout = appLockTimeout,
                            onAppLockChanged = onAppLockChanged,
                            verifyPassword = dataStore::verifyPassword,
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
    }
}

@Composable
internal fun PageHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Text(desktopText(title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun FilterChipSimple(
label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) { Text(label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) }
}

@Composable
internal fun SimpleNameDialog(title: String, onDismiss: () -> Unit, initial: String = "", onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(desktopText("Name")) }) }, dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } }, confirmButton = { Button(enabled = value.isNotBlank(), onClick = { onSave(value.trim()) }) { Text(desktopText("Save")) } })
}

@Composable
private fun SettingsPage(
    cloudState: DesktopCloudUiState,
    cloud: DesktopCloudSyncController,
    config: DesktopCloudConfig,
    configStore: DesktopConfigStore,
    dataStore: DesktopDataStore,
    openVault: () -> Unit,
    onUiLanguageChanged: (UiLanguage) -> Unit,
    forgetLocalPassword: () -> Unit,
    visibleDestinations: Set<TopLevelDestination>,
    onVisibleDestinationsChanged: (Set<TopLevelDestination>) -> Unit,
    appLockEnabled: Boolean,
    appLockTimeout: AppLockTimeout,
    onAppLockChanged: (Boolean, AppLockTimeout) -> Unit,
    verifyPassword: (CharArray) -> Boolean,
) {
    val scope = rememberSafeCoroutineScope()
    var connectDialog by remember { mutableStateOf(false) }
    var gitHubDialog by remember { mutableStateOf(false) }
    // Installers carry built-in sign-in clients, so connecting is a single click. Builds without
    // them ask for the client details instead.
    val cloudDefaults = DesktopCloudDefaults.builtIn
    fun connectGoogle() {
        if (cloudDefaults.hasGoogle) cloud.launch { connect(cloudDefaults.googleClientId, cloudDefaults.googleClientSecret.toCharArray()) }
        else connectDialog = true
    }
    fun connectGitHub() {
        if (cloudDefaults.hasGitHub) cloud.startGitHubConnect(cloudDefaults.gitHubClientId, "") else gitHubDialog = true
    }
    fun reconnectGitHub() {
        val clientId = config.gitHubClientId.ifBlank { cloudDefaults.gitHubClientId }
        if (clientId.isNotBlank() && config.gitHubRepository.isNotBlank()) cloud.startGitHubConnect(clientId, config.gitHubRepository)
        else gitHubDialog = true
    }
    var importCandidate by remember { mutableStateOf<File?>(null) }
    var importPassword by remember { mutableStateOf("") }
    var importPasswordVisible by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var confirmDisableLock by remember { mutableStateOf(false) }
    if (confirmDisableLock) {
        DataPasswordDialog(
            title = "Turn off app lock",
            message = "Enter your data password to turn off the app lock.",
            verify = verifyPassword,
            onVerified = { confirmDisableLock = false; onAppLockChanged(false, appLockTimeout) },
            onDismiss = { confirmDisableLock = false },
        )
    }
    val language = LocalUiLanguage.current
    // Settings read best as one comfortable column, not stretched across a wide window.
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 920.dp).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader("Settings")
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Cloud, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(desktopText(when { !cloudState.connected -> "Cloud sync"; cloudState.provider == DesktopCloudProvider.GITHUB -> "GitHub sync"; else -> "Google Drive sync" }), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(if (cloudState.connected && cloudState.provider == DesktopCloudProvider.GITHUB) desktopText("Connected") + " · " + cloudState.gitHubRepository else desktopText(if (cloudState.connected) "Connected" else "Not connected"), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            Text(desktopText("Encrypted snapshots are stored in Life Assistant's private Google Drive app folder or a private GitHub repository. Nothing else in those accounts is read."))
            Text(desktopText("Changes made on this PC and your other devices are merged automatically. The 10 most recent encrypted versions are kept."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (cloudState.connected) {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(desktopText("Automatic sync")); Text(desktopText("Syncs a few seconds after each change, when the window is focused, and every 2 minutes while Life Assistant is open."), style = MaterialTheme.typography.bodySmall) }; Switch(cloudState.automaticSync, cloud::setAutomaticSync) }
                cloudState.lastSyncAt?.let { Text(desktopLastSync(formatTimestamp(it), LocalUiLanguage.current), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(enabled = !cloudState.syncing, onClick = { cloud.launch { synchronize() } }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(desktopText(if (cloudState.syncing) "Syncing…" else "Sync now")) }; TextButton(enabled = !cloudState.syncing, onClick = { if (cloudState.provider == DesktopCloudProvider.GITHUB) reconnectGitHub() else connectGoogle() }) { Text(desktopText("Reconnect")) }; TextButton(enabled = !cloudState.syncing, onClick = { cloud.launch { disconnect() } }) { Text(desktopText("Disconnect / switch account")) } }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(enabled = !cloudState.syncing, onClick = ::connectGitHub) { Text(desktopText("Connect GitHub")) }; OutlinedButton(enabled = false, onClick = ::connectGoogle) { Text(desktopText("Google Drive (coming soon)")) } }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(desktopText("Local security"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(desktopText(DesktopPlatform.text("Local data is password-encrypted. Windows can remember the password using DPAPI for this Windows account.", "Local data is password-encrypted. Life Assistant can remember the password for this Linux user."))); OutlinedButton(onClick = forgetLocalPassword) { Text(desktopText("Forget remembered password")) } } }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(desktopText("App lock"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(desktopText("Ask for the data password when returning to the app"))
                        Text(
                            desktopText(DesktopPlatform.text("Applies even when Windows remembers the password.", "Applies even when the password is remembered.")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(appLockEnabled, { enabled ->
                        // Turning the lock off needs the password, so an unattended PC cannot drop it.
                        if (enabled) onAppLockChanged(true, appLockTimeout) else confirmDisableLock = true
                    })
                }
                if (appLockEnabled) {
                    Text(desktopText("Lock after leaving the app"))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AppLockTimeout.entries, key = { it.name }) { timeout ->
                            FilterChipSimple(desktopText(timeout.desktopLabel), timeout == appLockTimeout) {
                                onAppLockChanged(true, timeout)
                            }
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(desktopText("Modules in menu"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    desktopText("Hidden modules keep their data. At least one module stays visible."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TopLevelDestination.entries, key = { it.name }) { module ->
                        val shown = module in visibleDestinations
                        FilterChipSimple(desktopText(module.toDesktopDestination().label), shown) {
                            val next = if (shown) visibleDestinations - module else visibleDestinations + module
                            if (next.isNotEmpty()) onVisibleDestinationsChanged(next)
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, null)
                    Spacer(Modifier.width(10.dp))
                    Text(desktopText("Appearance"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(desktopText("Theme"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ThemeMode.entries, key = { it.name }) { mode ->
                        FilterChipSimple(desktopText(themeLabel(mode)), dataStore.currentSnapshot()?.settings?.themeMode == mode) {
                            scope.launch { dataStore.setThemeMode(mode) }
                        }
                    }
                }
                Text(desktopText("Accent color"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AccentColor.entries, key = { it.name }) { accent ->
                        FilterChipSimple(
                            accent.name.lowercase().replaceFirstChar(Char::uppercase),
                            dataStore.currentSnapshot()?.settings?.accentColor == accent,
                        ) { scope.launch { dataStore.setAccentColor(accent) } }
                    }
                }
                Text(desktopText("UI language"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(UiLanguage.entries, key = { it.name }) { language ->
                        FilterChipSimple(if (language == UiLanguage.ENGLISH) "English" else "简体中文", config.uiLanguage == language) {
                            configStore.setUiLanguage(language)
                            onUiLanguageChanged(language)
                        }
                    }
                }
                Text(desktopText("Week starts on"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(WeekStart.entries, key = { it.name }) { value ->
                        FilterChipSimple(desktopText(weekStartLabel(value)), dataStore.currentSnapshot()?.settings?.weekStart == value) { scope.launch { dataStore.setWeekStart(value) } }
                    }
                }
                Text(desktopText("Time format"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TimeFormatOption.entries, key = { it.name }) { value ->
                        FilterChipSimple(desktopText(timeFormatLabel(value)), dataStore.currentSnapshot()?.settings?.timeFormat == value) { scope.launch { dataStore.setTimeFormat(value) } }
                    }
                }
                Text(desktopText("Date format"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DateFormatOption.entries, key = { it.name }) { value ->
                        FilterChipSimple(dateFormatLabel(value, language), dataStore.currentSnapshot()?.settings?.dateFormat == value) { scope.launch { dataStore.setDateFormat(value) } }
                    }
                }
            }
        }
        dataStore.currentSnapshot()?.settings?.let { settings -> ReminderSettingsCard(settings) { change -> scope.launch { dataStore.updateSettings(change) } } }
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(desktopText("Vault"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(desktopText("Encrypted account and password entries"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = openVault) { Text(desktopText("Open")) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(desktopText("Encrypted backup"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(desktopText("The .tlb file includes todos, ledger entries, notes, Vault entries, and attached files. Manual import can migrate an older Android backup password to this PC's current data password."))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val chooser = JFileChooser().apply { selectedFile = File("LifeAssistant-backup.tlb") }
                        val destination = chooser.takeIf {
                            it.showSaveDialog(null) == JFileChooser.APPROVE_OPTION
                        }?.selectedFile
                        if (destination != null) scope.launch {
                            val upload = dataStore.createUploadSnapshot()
                            try {
                                withContext(Dispatchers.IO) { upload.file.copyTo(destination, overwrite = true) }
                                backupMessage = desktopText("Encrypted backup exported.", language)
                            } finally {
                                upload.file.delete()
                            }
                        }
                    }) { Text(desktopText("Export backup")) }
                    OutlinedButton(onClick = {
                        val chooser = JFileChooser()
                        importCandidate = chooser.takeIf {
                            it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
                        }?.selectedFile
                        importPassword = ""
                    }) { Text(desktopText("Import backup")) }
                }
                Text(
                    desktopText("Before using a cloud version, Life Assistant keeps an encrypted local recovery copy. Import a copy to recover earlier local data. The 5 most recent recovery copies are kept."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val recoveryDirectory = dataStore.cloudRecoveryDirectory
                OutlinedButton(
                    enabled = recoveryDirectory.isDirectory && Desktop.isDesktopSupported(),
                    onClick = {
                        if (recoveryDirectory.isDirectory && Desktop.isDesktopSupported()) {
                            scope.launch(Dispatchers.IO) {
                                runCatching { Desktop.getDesktop().open(recoveryDirectory) }
                            }
                        }
                    },
                ) { Text(desktopText("Open sync recovery folder")) }
                backupMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null)
                    Spacer(Modifier.width(10.dp))
                    Text(desktopText("About"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(desktopText(AppIdentity.NAME), style = MaterialTheme.typography.headlineSmall)
                Text("${desktopText("Version")} ${AppIdentity.VERSION} • ${AppIdentity.AUTHOR}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(AppIdentity.COPYRIGHT, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(desktopText("License") + ": ${AppIdentity.LICENSE_LABEL}")
                Text(desktopText("This software is provided without warranty."), style = MaterialTheme.typography.bodySmall)
                Text(desktopText("The full license and additional permissions are available offline."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showLicenseDialog = true }) { Text(desktopText("View license")) }
                    OutlinedButton(
                        enabled = Desktop.isDesktopSupported(),
                        onClick = {
                            if (Desktop.isDesktopSupported()) {
                                runCatching { Desktop.getDesktop().browse(URI(AppIdentity.SOURCE_URL)) }
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text(desktopText("Source code"))
                    }
                }
            }
        }
        Text(desktopAppVersion(language), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    }
    if (connectDialog) GoogleConnectDialog(config.clientId, { connectDialog = false }) { clientId, clientSecret -> connectDialog = false; cloud.launch { connect(clientId, clientSecret) } }
    if (gitHubDialog) GitHubConnectDialog(config.gitHubClientId, config.gitHubRepository, { gitHubDialog = false }) { clientId, repository -> gitHubDialog = false; cloud.startGitHubConnect(clientId, repository) }
    cloudState.gitHubCode?.let { code -> GitHubCodeDialog(code.userCode, code.verificationUri, cloud::cancelGitHubConnect) }
    importCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { importCandidate = null; importPassword = "" },
            title = { Text(desktopText("Import encrypted backup?")) },
            text = {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(desktopText("Enter the password used when this backup was created. After validation, its contents will be encrypted with this PC's current data password."))
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text(desktopText("Source backup password")) },
                        singleLine = true,
                        visualTransformation = if (importPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { importPasswordVisible = !importPasswordVisible }) {
                                Icon(if (importPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(desktopText("This replaces the local records. Export the current data first if you may need it later."), style = MaterialTheme.typography.bodySmall)
                }
            },
            dismissButton = { TextButton(onClick = { importCandidate = null; importPassword = "" }) { Text(desktopText("Cancel")) } },
            confirmButton = {
                Button(enabled = importPassword.length >= 8, onClick = {
                    val sourcePassword = importPassword.toCharArray()
                    importPassword = ""
                    importCandidate = null
                    scope.launch {
                        try {
                            val expected = dataStore.localFingerprint()
                            backupMessage = when (dataStore.importFromEncrypted(candidate, sourcePassword, expected)) {
                                is DesktopReplaceResult.Applied -> desktopText("Backup imported and encrypted with the current data password.", language)
                                DesktopReplaceResult.LocalChanged -> desktopText("Local data changed; import was cancelled.", language)
                                DesktopReplaceResult.Invalid -> desktopText("The source password is incorrect or the backup is damaged.", language)
                            }
                        } finally {
                            sourcePassword.fill('\u0000')
                        }
                    }
                }) { Text(desktopText("Restore")) }
            },
        )
    }
    if (showLicenseDialog) {
        val legalText = remember { loadLegalResource(AppIdentity.LICENSE_RESOURCE) }
        val permissionText = remember { loadLegalResource(AppIdentity.PERMISSION_RESOURCE) }
        val noticeText = remember { loadLegalResource(AppIdentity.NOTICE_RESOURCE) }
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text(desktopText("License")) },
            text = {
                Column(
                    Modifier.widthIn(max = 760.dp).heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(AppIdentity.LICENSE_LABEL, style = MaterialTheme.typography.titleMedium)
                    Text(legalText)
                    Text(permissionText)
                    Text(noticeText)
                }
            },
            confirmButton = { TextButton(onClick = { showLicenseDialog = false }) { Text(desktopText("Close")) } },
        )
    }
}

private fun loadLegalResource(path: String): String =
    Thread.currentThread().contextClassLoader.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: "${path} is not available in this build."

@Composable
private fun GoogleConnectDialog(defaultClientId: String, onDismiss: () -> Unit, onConnect: (String, CharArray) -> Unit) {
    var clientId by remember { mutableStateOf(defaultClientId) }; var secret by remember { mutableStateOf("") }; var visible by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(desktopText("Connect Google Drive")) }, text = { Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(desktopText("A Google Cloud Desktop OAuth client from the same project as the Android app is required for this open-source build. Sign-in opens in your system browser.")); OutlinedTextField(clientId, { clientId = it.trim() }, label = { Text(desktopText("Desktop OAuth client ID")) }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(secret, { secret = it }, label = { Text(desktopText("Desktop OAuth client secret")) }, modifier = Modifier.fillMaxWidth(), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } }); Text(desktopText("Google requires the client secret for Desktop clients. It is protected by Windows DPAPI and is application configuration, not a replacement for PKCE."), style = MaterialTheme.typography.bodySmall) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } }, confirmButton = { Button(enabled = clientId.endsWith(".apps.googleusercontent.com") && secret.isNotBlank(), onClick = { val transferred = secret.toCharArray(); secret = ""; onConnect(clientId, transferred) }) { Text(desktopText("Open Google sign-in")) } })
}

internal fun chooseAndAttach(scope: kotlinx.coroutines.CoroutineScope, store: DesktopDataStore, ownerType: AttachmentOwnerType, ownerId: Long) {
    scope.launch {
        val chooser = JFileChooser()
        val selected = chooser.takeIf { it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION }?.selectedFile
        if (selected != null) store.attachFile(ownerType, ownerId, selected)
    }
}

@Composable
internal fun AttachmentList(
    snapshot: BackupSnapshot,
    ownerType: AttachmentOwnerType,
    ownerId: Long,
    store: DesktopDataStore,
) {
    val scope = rememberSafeCoroutineScope()
    // Attachments waiting for removal after a delete are not shown.
    val attachments = snapshot.attachments.filter { it.ownerType == ownerType && it.ownerId == ownerId && it.pendingDeleteAt == null }
    attachments.forEach { attachment ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                attachment.originalName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            IconButton(
                onClick = {
                    store.attachmentFile(attachment.id)?.let { file ->
                        scope.launch(Dispatchers.IO) { runCatching { Desktop.getDesktop().open(file) } }
                    }
                },
            ) { Icon(Icons.AutoMirrored.Filled.OpenInNew, desktopText("Open attachment"), Modifier.size(18.dp)) }
            IconButton(
                onClick = {
                    val chooser = JFileChooser().apply { selectedFile = File(attachment.originalName) }
                    val destination = chooser.takeIf {
                        it.showSaveDialog(null) == JFileChooser.APPROVE_OPTION
                    }?.selectedFile
                    val source = store.attachmentFile(attachment.id)
                    if (source != null && destination != null) {
                        scope.launch(Dispatchers.IO) { source.copyTo(destination, overwrite = true) }
                    }
                },
            ) { Icon(Icons.Default.Download, desktopText("Save a copy"), Modifier.size(18.dp)) }
            IconButton(
                onClick = { scope.launch { store.removeAttachment(attachment.id) } },
            ) { Icon(Icons.Default.Delete, desktopText("Remove attachment"), Modifier.size(18.dp)) }
        }
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
    DesktopDestination.TODO -> TopLevelDestination.TODO
    DesktopDestination.LEDGER -> TopLevelDestination.LEDGER
    DesktopDestination.CALENDAR -> TopLevelDestination.CALENDAR
    DesktopDestination.NOTES -> TopLevelDestination.NOTES
    DesktopDestination.DIARY -> TopLevelDestination.DIARY
    DesktopDestination.CONFESSIONAL -> TopLevelDestination.CONFESSIONAL
    DesktopDestination.VAULT, DesktopDestination.SETTINGS -> TopLevelDestination.NOTES
}

private fun TopLevelDestination.toDesktopDestination(): DesktopDestination = when (this) {
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
private fun compactMoney(cents: Long): String = when {
    cents >= 100_000_000L -> "$" + "%.1fM".format(Locale.US, cents / 100_000_000.0)
    cents >= 100_000L -> "$" + "%.1fk".format(Locale.US, cents / 100_000.0)
    else -> formatMoney(cents)
}
private fun formatCalendarNet(cents: Long): String = BigDecimal.valueOf(cents, 2)
    .stripTrailingZeros()
    .toPlainString()
private fun formatTimestamp(timestamp: Long): String = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US).format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

internal fun parseAmountCents(value: String): Long? {
    val decimal = value.toBigDecimalOrNull() ?: return null
    if (decimal.signum() <= 0 || decimal.scale() !in 0..2) return null
    return runCatching { decimal.movePointRight(2).longValueExact() }
        .getOrNull()
        ?.takeIf { it in 1..99_999_999_999L }
}

@Composable
internal fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsDesktopState(): androidx.compose.runtime.State<T> = collectAsState()

private val AppLockTimeout.desktopLabel: String
    get() = when (this) {
        AppLockTimeout.IMMEDIATELY -> "Immediately"
        AppLockTimeout.ONE_MINUTE -> "After 1 minute"
        AppLockTimeout.FIVE_MINUTES -> "After 5 minutes"
    }
