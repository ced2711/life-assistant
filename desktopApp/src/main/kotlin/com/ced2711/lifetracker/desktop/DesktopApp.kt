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
    VAULT("Vault", Icons.Default.Lock),
    SETTINGS("Settings", Icons.Default.Settings),
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
    val credentials = remember { WindowsCredentialStore() }
    val config = remember { DesktopConfigStore() }
    val oauth = remember { DesktopGoogleOAuth(config, credentials) }
    val cloud = remember { DesktopCloudSyncController(dataStore, config, oauth) }
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

    DisposableEffect(Unit) {
        onDispose { dataStore.close() }
    }
    LaunchedEffect(Unit) {
        val saved = credentials.load(WindowsCredentialStore.LOCAL_PASSWORD)
        if (saved != null) {
            if (dataStore.open(saved.copyOf())) cloud.start(scope)
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
                                if (rememberOnPc) {
                                    credentials.save(
                                        WindowsCredentialStore.LOCAL_PASSWORD,
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
                                if (rememberOnPc) credentials.save(
                                    WindowsCredentialStore.LOCAL_PASSWORD,
                                    password.copyOf(),
                                )
                                cloud.start(scope)
                            }
                            password.fill('\u0000')
                        }
                    },
                )
                is DesktopStoreState.Open -> DesktopHome(
                    snapshot = current.snapshot,
                    dataStore = dataStore,
                    cloud = cloud,
                    configStore = config,
                    credentials = credentials,
                    onUiLanguageChanged = { uiLanguage = it },
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
                    Text(desktopText("Remember securely with Windows"))
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
                    desktopText("Your password is never uploaded. If remembered, it is protected by Windows DPAPI for this Windows account."),
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
    credentials: WindowsCredentialStore,
    onUiLanguageChanged: (UiLanguage) -> Unit,
) {
    val configuredDestination = configStore.read().lastDestination.toDesktopDestination()
    var destination by remember { mutableStateOf(configuredDestination) }
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

    val mainDestinations = remember { listOf(DesktopDestination.TODO, DesktopDestination.LEDGER, DesktopDestination.CALENDAR, DesktopDestination.NOTES) }
    fun navigate(to: DesktopDestination) {
        destination = to
        if (to in mainDestinations) configStore.setLastDestination(to.toTopLevelDestination())
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 850.dp
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        mainDestinations.forEach { item ->
                            NavigationBarItem(
                                selected = item == destination,
                                onClick = { navigate(item) },
                                icon = { Icon(item.icon, desktopText(item.label)) },
                                label = { Text(desktopText(item.label)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail {
                        Spacer(Modifier.height(12.dp))
                        mainDestinations.forEach { item ->
                            NavigationRailItem(
                                selected = item == destination,
                                onClick = { navigate(item) },
                                icon = { Icon(item.icon, desktopText(item.label)) },
                                label = { Text(desktopText(item.label)) },
                            )
                        }
                    }
                    HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        if (destination == DesktopDestination.SETTINGS || destination == DesktopDestination.VAULT) {
                            IconButton(onClick = { navigate(DesktopDestination.NOTES) }) {
                                Icon(Icons.Default.ChevronLeft, desktopText("Back"))
                            }
                        } else {
                            IconButton(onClick = { destination = DesktopDestination.SETTINGS }) {
                                Icon(Icons.Default.Settings, desktopText("Settings"))
                            }
                        }
                    }
                    when (destination) {
                        DesktopDestination.TODO -> TodoPage(snapshot, dataStore)
                        DesktopDestination.LEDGER -> LedgerPage(snapshot, dataStore)
                        DesktopDestination.CALENDAR -> CalendarPage(snapshot, dataStore)
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
                            forgetLocalPassword = { credentials.delete(WindowsCredentialStore.LOCAL_PASSWORD) },
                        )
                    }
                }
            }
        }

        cloudState.conflict?.let {
            AlertDialog(
                onDismissRequest = cloud::dismissConflict,
                title = { Text(desktopText("Sync conflict")) },
                text = { Text(desktopText("Use newest cloud replaces this PC's data. Keep this PC publishes this PC's full dataset. Neither option merges individual records. Previous encrypted cloud versions are kept.")) },
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
private fun PageHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Text(desktopText(title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodoPage(snapshot: BackupSnapshot, store: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    var editing by remember { mutableStateOf<TodoEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var allCategories by remember { mutableStateOf(true) }
    var selectedCategories by remember { mutableStateOf(emptySet<Long>()) }
    var includeUncategorized by remember { mutableStateOf(false) }
    var priorityFilter by remember { mutableStateOf<TodoPriority?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var sortBy by remember { mutableStateOf(DesktopTodoSort.DEADLINE) }
    var newCategory by remember { mutableStateOf(false) }
    val includedCategoryIds = descendantCategoryIds(snapshot.categories, selectedCategories)
    val filter = DesktopTodoFilter(allCategories, selectedCategories, includeUncategorized, priorityFilter, tagFilter, showCompleted)
    val todos = snapshot.todos
        .filter { it.deletedAt == null && (filter.showCompleted || it.completedAt == null) }
        .filter { categoryMatches(it.categoryId, filter, includedCategoryIds) }
        .filter { priorityFilter == null || it.priority == priorityFilter }
        .filter { tagFilter == null || it.tagsCsv.split(',').any { tag -> tag.trim().equals(tagFilter, true) } }
        .filter { query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true) }
        .sortedWith(compareBy<TodoEntity> { it.completedAt != null }.let { comparator ->
            when (sortBy) {
                DesktopTodoSort.DEADLINE -> comparator.thenBy { it.deadlineEpochDay ?: Long.MAX_VALUE }
                DesktopTodoSort.PRIORITY -> comparator.thenByDescending { it.priority.ordinal }
                DesktopTodoSort.TITLE -> comparator.thenBy { it.title.lowercase() }
            }
        })
    Scaffold(
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, null) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PageHeader("Todo", desktopActiveTasks(todos.count { it.completedAt == null }, LocalUiLanguage.current))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(query, { query = it }, label = { Text(desktopText("Search")) }, modifier = Modifier.weight(1f))
                IconButton(onClick = { showFilters = !showFilters }) { Icon(Icons.Default.FilterAlt, desktopText("Filters")) }
            }
            if (showFilters) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(showCompleted, { showCompleted = it })
                    Text(desktopText("Completed"))
                    Spacer(Modifier.width(12.dp))
                    Text(desktopText("Sort"), style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        items(DesktopTodoSort.entries.toList(), key = { it.name }) { item ->
                            FilterChipSimple(desktopText(desktopSortLabel(item)), sortBy == item) { sortBy = item }
                        }
                    }
                }
                LazyRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChipSimple(desktopText("All"), allCategories) {
                            allCategories = true
                            selectedCategories = emptySet()
                            includeUncategorized = false
                        }
                    }
                    item {
                        FilterChipSimple(desktopText("Uncategorized"), !allCategories && includeUncategorized) {
                            allCategories = false
                            includeUncategorized = !includeUncategorized
                        }
                    }
                    items(snapshot.categories, key = { it.id }) { category ->
                        FilterChipSimple(categoryPath(category.id, snapshot), !allCategories && category.id in selectedCategories) {
                            allCategories = false
                            selectedCategories = if (category.id in selectedCategories) selectedCategories - category.id else selectedCategories + category.id
                        }
                    }
                    item {
                        FilterChipSimple(desktopText("Priority"), priorityFilter != null) {
                            priorityFilter = if (priorityFilter == null) TodoPriority.MEDIUM else null
                        }
                    }
                }
                val tags = snapshot.todos.flatMap { it.tagsCsv.split(',') }.map(String::trim).filter(String::isNotBlank).distinct().sorted()
                if (tags.isNotEmpty()) LazyRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tags) { tag -> FilterChipSimple("#${tag}", tagFilter == tag) { tagFilter = if (tagFilter == tag) null else tag } }
                }
                TextButton(onClick = { newCategory = true }, modifier = Modifier.padding(horizontal = 24.dp)) { Icon(Icons.Default.Add, null); Text(desktopText("Category")) }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(todos, key = { it.id }) { todo ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(todo.completedAt != null, { scope.launch { store.toggleTodo(todo.id) } })
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(todo.title, fontWeight = FontWeight.SemiBold)
                                if (todo.description.isNotBlank()) Text(
                                    todo.description,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                todo.deadlineEpochDay?.let {
                                    Text(desktopDue(UserFormatting.formatDate(LocalDate.ofEpochDay(it), snapshot.settings.dateFormat, uiLocale(LocalUiLanguage.current)), LocalUiLanguage.current), style = MaterialTheme.typography.bodySmall)
                                }
                                AttachmentList(snapshot, AttachmentOwnerType.TODO, todo.id, store)
                            }
                            IconButton({ chooseAndAttach(scope, store, AttachmentOwnerType.TODO, todo.id) }) {
                                Icon(Icons.Default.AttachFile, desktopText("Attach"))
                            }
                            IconButton({ editing = todo }) { Icon(Icons.Default.Edit, desktopText("Edit")) }
                            IconButton({ scope.launch { store.deleteTodo(todo.id) } }) { Icon(Icons.Default.Delete, desktopText("Delete")) }
                        }
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        TodoEditorDialog(
            todo = editing,
            snapshot = snapshot,
            onDismiss = { adding = false; editing = null },
            onSave = { title, description, date, priority, categoryId, tags, completed ->
                scope.launch {
                    store.upsertTodo(
                        editing?.id,
                        title,
                        description,
                        date,
                        priority,
                        categoryId,
                        tags,
                        completed,
                    )
                }
                adding = false
                editing = null
            },
        )
    }
    if (newCategory) {
        SimpleNameDialog(desktopText("New category"), { newCategory = false }) { name ->
            scope.launch { store.addCategory(name, selectedCategories.firstOrNull()) }
            newCategory = false
        }
    }
}

@Composable
private fun TodoEditorDialog(
    todo: TodoEntity?,
    snapshot: BackupSnapshot,
    onDismiss: () -> Unit,
    onSave: (String, String, Long?, TodoPriority, Long?, String, Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(todo?.title.orEmpty()) }
    var description by remember { mutableStateOf(todo?.description.orEmpty()) }
    var deadline by remember {
        mutableStateOf(
            todo?.deadlineEpochDay?.let {
                LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US))
            }.orEmpty(),
        )
    }
    var priority by remember { mutableStateOf(todo?.priority ?: TodoPriority.NONE) }
    var categoryId by remember { mutableStateOf(todo?.categoryId) }
    var tags by remember { mutableStateOf(todo?.tagsCsv.orEmpty()) }
    var completed by remember { mutableStateOf(todo?.completedAt != null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(desktopText(if (todo == null) "New todo" else "Edit todo")) },
        text = {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(description, { description = it }, label = { Text(desktopText("Description")) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(title, { title = it }, label = { Text(desktopText("Title (optional)")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(deadline, { deadline = it }, label = { Text(desktopText("Deadline M/D/YYYY, M/D, or day (optional)")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it }, label = { Text(desktopText("Tags, comma separated (optional)")) }, modifier = Modifier.fillMaxWidth())
                Text(desktopText("Category"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChipSimple(desktopText("None"), categoryId == null) { categoryId = null } }
                    items(snapshot.categories, key = { it.id }) { category ->
                        FilterChipSimple(
                            categoryPath(category.id, snapshot),
                            categoryId == category.id,
                        ) { categoryId = category.id }
                    }
                }
                Text(desktopText("Priority"))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(TodoPriority.NONE, TodoPriority.LOW, TodoPriority.MEDIUM, TodoPriority.HIGH).forEach { item ->
                        FilterChipSimple(item.name.lowercase().replaceFirstChar(Char::uppercase), item == priority) { priority = item }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(completed, { completed = it })
                    Text(desktopText("Done"))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } },
        confirmButton = {
            val day = deadline.takeIf(String::isNotBlank)?.let { SmartDateParser.parse(it, LocalDate.now())?.toEpochDay() }
            Button(enabled = description.isNotBlank() && (deadline.isBlank() || day != null), onClick = {
                onSave(title, description, day, priority, categoryId, tags, completed)
            }) { Text(desktopText("Save")) }
        },
    )
}

@Composable
private fun FilterChipSimple(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) { Text(label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) }
}

@Composable
private fun LedgerPage(snapshot: BackupSnapshot, store: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LedgerEntryEntity?>(null) }
    val entries = snapshot.ledgerEntries.filter { it.deletedAt == null }.sortedByDescending { it.epochDay }
    val income = entries.filter { it.type == LedgerType.INCOME }.sumOf { it.amountCents }
    val expense = entries.filter { it.type == LedgerType.EXPENSE }.sumOf { it.amountCents }
    Scaffold(floatingActionButton = { FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, null) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PageHeader("Ledger", desktopNet(formatMoney(income - expense), LocalUiLanguage.current))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(desktopText("Income"), formatMoney(income), Color(0xFF65D28A), Modifier.weight(1f))
                SummaryCard(desktopText("Expense"), formatMoney(expense), Color(0xFFFF756B), Modifier.weight(1f))
            }
            LedgerTrend(entries, snapshot.settings.dateFormat, Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 24.dp, vertical = 12.dp))
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.merchant.ifBlank { entry.note.ifBlank { entry.type.name.lowercase().replaceFirstChar(Char::uppercase) } }, fontWeight = FontWeight.SemiBold)
                                Text(UserFormatting.formatDate(LocalDate.ofEpochDay(entry.epochDay), snapshot.settings.dateFormat, uiLocale(LocalUiLanguage.current)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AttachmentList(snapshot, AttachmentOwnerType.LEDGER, entry.id, store)
                            }
                            Text(
                                (if (entry.type == LedgerType.INCOME) "+" else "−") + formatMoney(entry.amountCents),
                                color = if (entry.type == LedgerType.INCOME) Color(0xFF65D28A) else Color(0xFFFF756B),
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton({ chooseAndAttach(scope, store, AttachmentOwnerType.LEDGER, entry.id) }) { Icon(Icons.Default.AttachFile, desktopText("Attach")) }
                            IconButton({ editing = entry }) { Icon(Icons.Default.Edit, desktopText("Edit")) }
                            IconButton({ scope.launch { store.deleteLedger(entry.id) } }) { Icon(Icons.Default.Delete, desktopText("Delete")) }
                        }
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        LedgerEditorDialog(
            entry = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { type, cents, day, note, merchant, tags ->
                scope.launch { store.upsertLedger(editing?.id, type, cents, day, note, merchant, tags) }
                adding = false; editing = null
            },
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp)) { Text(title); Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }
}

@Composable
private fun LedgerTrend(entries: List<LedgerEntryEntity>, dateFormat: DateFormatOption, modifier: Modifier) {
    val values = entries.groupBy { it.epochDay }.toSortedMap().entries.toList().takeLast(7).map { (day, rows) ->
        day to rows.sumOf { if (it.type == LedgerType.INCOME) it.amountCents else -it.amountCents }
    }
    val maximum = values.maxOfOrNull { kotlin.math.abs(it.second) }?.coerceAtLeast(1L) ?: 1L
    val scaleMaximum = ((maximum * 11L) + 9L) / 10L
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(desktopText("Daily net  •  green = net income  •  red = net expense"), style = MaterialTheme.typography.bodySmall)
            Text(desktopRange(formatMoney(scaleMaximum), LocalUiLanguage.current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (values.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(desktopText("No ledger data yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val slot = size.width / values.size
            val center = size.height / 2f
            drawLine(Color(0xFF52605D), start = androidx.compose.ui.geometry.Offset(0f, center), end = androidx.compose.ui.geometry.Offset(size.width, center))
            values.forEachIndexed { index, (_, value) ->
                val height = (kotlin.math.abs(value).toFloat() / scaleMaximum) * (center - 5f)
                val left = index * slot + slot * .22f
                val top = if (value >= 0) center - height else center
                drawRect(
                    if (value >= 0) Color(0xFF65D28A) else Color(0xFFFF756B),
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(slot * .56f, height.coerceAtLeast(2f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            values.forEach { (day, value) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        (if (value >= 0) "+" else "−") + compactMoney(kotlin.math.abs(value)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (value >= 0) Color(0xFF65D28A) else Color(0xFFFF756B),
                        maxLines = 1,
                    )
                    Text(
                        UserFormatting.formatDate(LocalDate.ofEpochDay(day), dateFormat, uiLocale(LocalUiLanguage.current)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerEditorDialog(
    entry: LedgerEntryEntity?,
    onDismiss: () -> Unit,
    onSave: (LedgerType, Long, Long, String, String, String) -> Unit,
) {
    var type by remember { mutableStateOf(entry?.type ?: LedgerType.EXPENSE) }
    var amount by remember { mutableStateOf(entry?.amountCents?.let { "%.2f".format(Locale.US, it / 100.0) }.orEmpty()) }
    var date by remember {
        mutableStateOf(
            (entry?.epochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now())
                .format(DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US)),
        )
    }
    var merchant by remember { mutableStateOf(entry?.merchant.orEmpty()) }
    var note by remember { mutableStateOf(entry?.note.orEmpty()) }
    var tags by remember { mutableStateOf(entry?.tagsCsv.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(desktopText(if (entry == null) "New ledger entry" else "Edit ledger entry")) },
        text = {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row { LedgerType.entries.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(type == item, { type = item }); Text(desktopText(item.name.lowercase().replaceFirstChar(Char::uppercase))) } } }
                OutlinedTextField(
                    amount,
                    { next -> if (isValidDesktopAmountInput(next)) amount = next },
                    label = { Text(desktopText("Amount")) },
                    supportingText = { Text(desktopText("Positive amount, up to 2 decimal places")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(date, { date = it }, label = { Text(desktopText("Date M/D/YYYY, M/D, or day")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(merchant, { merchant = it }, label = { Text(desktopText("Merchant / payer")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text(desktopText("Note")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it }, label = { Text(desktopText("Tags, comma separated (optional)")) }, modifier = Modifier.fillMaxWidth())
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } },
        confirmButton = {
            val cents = parseAmountCents(amount)
            val day = SmartDateParser.parse(date, LocalDate.now())?.toEpochDay()
            Button(enabled = cents != null && day != null, onClick = { onSave(type, requireNotNull(cents), requireNotNull(day), note, merchant, tags) }) { Text(desktopText("Save")) }
        },
    )
}

@Composable
private fun CalendarPage(snapshot: BackupSnapshot, store: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var editingTodo by remember { mutableStateOf<TodoEntity?>(null) }
    var editingLedger by remember { mutableStateOf<LedgerEntryEntity?>(null) }
    val first = month.atDay(1)
    val weekDays = UserFormatting.orderedDaysOfWeek(snapshot.settings.weekStart, uiLocale(LocalUiLanguage.current))
    val firstDay = weekDays.first()
    val offset = (first.dayOfWeek.value - firstDay.value + 7) % 7
    val days = List(offset) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, desktopText("Previous")) }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton({ month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, desktopText("Next")) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            weekDays.forEach { day ->
                Text(UserFormatting.formatWeekday(day, uiLocale(LocalUiLanguage.current)), Modifier.weight(1f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyVerticalGrid(GridCells.Fixed(7), Modifier.fillMaxSize().padding(16.dp)) {
            gridItems(days) { date ->
                if (date == null) Spacer(Modifier.height(110.dp)) else {
                    val epoch = date.toEpochDay()
                    val todoRows = snapshot.todos.filter { it.deadlineEpochDay == epoch && it.deletedAt == null }
                    val ledger = snapshot.ledgerEntries.filter { it.epochDay == epoch && it.deletedAt == null }
                    val net = ledger.sumOf { if (it.type == LedgerType.INCOME) it.amountCents else -it.amountCents }
                    Card(Modifier.padding(3.dp).height(104.dp).clickable { selectedDate = date }) {
                        Column(Modifier.padding(8.dp)) {
                            Text(date.dayOfMonth.toString(), fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                            Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                                if (net != 0L) {
                                    Text(
                                        formatCalendarNet(net),
                                        color = if (net > 0) Color(0xFF65D28A) else Color(0xFFFF756B),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            val done = todoRows.count { it.completedAt != null }
                            val incomplete = todoRows.size - done
                            Box(Modifier.fillMaxWidth().height(22.dp)) {
                                when {
                                    done > 0 && incomplete > 0 -> {
                                        Text(
                                            done.toString(),
                                            modifier = Modifier.align(Alignment.CenterStart),
                                            color = Color(0xFF65D28A),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            incomplete.toString(),
                                            modifier = Modifier.align(Alignment.CenterEnd),
                                            color = Color(0xFFFF756B),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    done > 0 -> Text(
                                        done.toString(),
                                        modifier = Modifier.align(Alignment.Center),
                                        color = Color(0xFF65D28A),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    incomplete > 0 -> Text(
                                        incomplete.toString(),
                                        modifier = Modifier.align(Alignment.Center),
                                        color = Color(0xFFFF756B),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selectedDate?.let { date ->
        val todoRows = snapshot.todos.filter { it.deadlineEpochDay == date.toEpochDay() && it.deletedAt == null }
        val ledgerRows = snapshot.ledgerEntries.filter { it.epochDay == date.toEpochDay() && it.deletedAt == null }
        AlertDialog(
            onDismissRequest = { selectedDate = null },
            title = { Text(date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))) },
            text = {
                Column(
                    Modifier.widthIn(max = 620.dp).fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(desktopText("Todos"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (todoRows.isEmpty()) Text(desktopText("No todos"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    todoRows.forEach { todo ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(todo.completedAt != null, { scope.launch { store.toggleTodo(todo.id) } })
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        todo.title,
                                        color = if (todo.completedAt != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (todo.description.isNotBlank()) Text(todo.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                                IconButton(onClick = { selectedDate = null; editingTodo = todo }) {
                                    Icon(Icons.Default.Edit, desktopText("Edit todo"))
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text(desktopText("Ledger"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (ledgerRows.isEmpty()) Text(desktopText("No ledger entries"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ledgerRows.forEach { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.merchant.ifBlank { entry.note.ifBlank { entry.type.name } }, Modifier.weight(1f))
                                Text(
                                    (if (entry.type == LedgerType.INCOME) "+" else "−") + formatMoney(entry.amountCents),
                                    color = if (entry.type == LedgerType.INCOME) Color(0xFF65D28A) else Color(0xFFFF756B),
                                )
                                IconButton(onClick = { selectedDate = null; editingLedger = entry }) {
                                    Icon(Icons.Default.Edit, desktopText("Edit ledger entry"))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedDate = null }) { Text(desktopText("Close")) } },
        )
    }
    editingTodo?.let { todo ->
        TodoEditorDialog(todo, snapshot, { editingTodo = null }) { title, description, day, priority, categoryId, tags, completed ->
            scope.launch { store.upsertTodo(todo.id, title, description, day, priority, categoryId, tags, completed) }
            editingTodo = null
        }
    }
    editingLedger?.let { entry ->
        LedgerEditorDialog(entry, { editingLedger = null }) { type, cents, day, note, merchant, tags ->
            scope.launch { store.upsertLedger(entry.id, type, cents, day, note, merchant, tags) }
            editingLedger = null
        }
    }
}

@Composable
private fun NotesPage(snapshot: BackupSnapshot, store: DesktopDataStore, openVault: () -> Unit) {
    val scope = rememberSafeCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<Long?>(null) }
    var unfiledOnly by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<Long?>(null) }
    var deletingFolder by remember { mutableStateOf<Long?>(null) }
    val folderParents = snapshot.noteFolders.associate { it.id to it.parentId }
    val includedFolderIds = selectedFolder?.let { selected ->
        snapshot.noteFolders.mapNotNull { candidate ->
            var cursor: Long? = candidate.id
            val visited = mutableSetOf<Long>()
            while (cursor != null && visited.add(cursor)) {
                if (cursor == selected) return@mapNotNull candidate.id
                cursor = folderParents[cursor]
            }
            null
        }.toSet()
    }
    val notes = snapshot.notes.filter {
        ((unfiledOnly && it.folderId == null) || (!unfiledOnly && (includedFolderIds == null || it.folderId in includedFolderIds))) &&
            (query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true))
    }
        .sortedWith(compareByDescending<NoteEntity> { it.pinned }.thenByDescending { it.updatedAt })
    Row(Modifier.fillMaxSize()) {
        if (showFolders) Column(Modifier.width(250.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .25f)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(desktopText("Folders"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showFolders = false }) { Icon(Icons.Default.ChevronLeft, desktopText("Hide folders")) }
            }
            TextButton({ selectedFolder = null; unfiledOnly = false }) { Text(desktopText("All notes")) }
            TextButton({ selectedFolder = null; unfiledOnly = true }) { Text(desktopText("Unfiled")) }
            snapshot.noteFolders.forEach { folder ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ selectedFolder = folder.id; unfiledOnly = false }, modifier = Modifier.weight(1f)) { Text(noteFolderPath(folder.id, snapshot)) }
                    IconButton(onClick = { renamingFolder = folder.id }) { Icon(Icons.Default.Edit, desktopText("Rename folder")) }
                    IconButton(onClick = { deletingFolder = folder.id }) { Icon(Icons.Default.Delete, desktopText("Delete folder")) }
                }
            }
            TextButton({ newFolder = true }) { Icon(Icons.Default.Add, null); Text(desktopText("Folder")) }
        }
        Scaffold(floatingActionButton = { FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, null) } }, modifier = Modifier.weight(1f)) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(desktopText("Notes"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(desktopNotesCount(notes.size, LocalUiLanguage.current), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showFolders = !showFolders }) { Icon(Icons.Default.Folder, desktopText("Folders")) }
                    IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Default.Search, desktopText("Search")) }
                    IconButton(onClick = openVault) { Icon(Icons.Default.Lock, desktopText("Vault")) }
                }
                if (showSearch) OutlinedTextField(query, { query = it }, label = { Text(desktopText("Search")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Card(Modifier.fillMaxWidth().clickable { editing = note }) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (note.pinned) "★ ${note.title}" else note.title, fontWeight = FontWeight.SemiBold)
                                    Text(note.body, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    AttachmentList(snapshot, AttachmentOwnerType.NOTE, note.id, store)
                                }
                                IconButton({ chooseAndAttach(scope, store, AttachmentOwnerType.NOTE, note.id) }) { Icon(Icons.Default.AttachFile, desktopText("Attach")) }
                                IconButton({ scope.launch { store.deleteNote(note.id) } }) { Icon(Icons.Default.Delete, desktopText("Delete")) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding || editing != null) NoteEditorDialog(
        editing,
        snapshot.noteFolders.map { it.id to noteFolderPath(it.id, snapshot) },
        selectedFolder,
        { adding = false; editing = null },
    ) { folder, title, body, pinned ->
        scope.launch { store.upsertNote(editing?.id, folder, title, body, pinned) }
        adding = false; editing = null
    }
    if (newFolder) SimpleNameDialog(desktopText("New folder"), { newFolder = false }) { scope.launch { store.addNoteFolder(it, selectedFolder) }; newFolder = false }
    renamingFolder?.let { folderId ->
        SimpleNameDialog(desktopText("Rename folder"), { renamingFolder = null }, initial = snapshot.noteFolders.firstOrNull { it.id == folderId }?.name.orEmpty()) { name ->
            scope.launch { store.renameNoteFolder(folderId, name) }
            renamingFolder = null
        }
    }
    deletingFolder?.let { folderId ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text(desktopText("Delete folder")) },
            text = { Text(desktopText("Notes stay safe; the folder is removed and child folders move up one level.")) },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text(desktopText("Cancel")) } },
            confirmButton = { Button(onClick = { scope.launch { store.deleteNoteFolder(folderId) }; deletingFolder = null }) { Text(desktopText("Delete")) } },
        )
    }
}

@Composable
private fun NoteEditorDialog(note: NoteEntity?, folders: List<Pair<Long, String>>, defaultFolder: Long?, onDismiss: () -> Unit, onSave: (Long?, String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf(note?.title.orEmpty()) }
    var body by remember { mutableStateOf(note?.body.orEmpty()) }
    var pinned by remember { mutableStateOf(note?.pinned ?: false) }
    var folder by remember { mutableStateOf(note?.folderId ?: defaultFolder) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(desktopText(if (note == null) "New note" else "Edit note")) }, text = {
        Column(Modifier.widthIn(max = 620.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(desktopText("Title")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text(desktopText("Note")) }, modifier = Modifier.fillMaxWidth(), minLines = 12)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(pinned, { pinned = it }); Text(desktopText("Pinned")) }
            if (folders.isNotEmpty()) {
                Text(desktopText("Folder"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChipSimple(desktopText("None"), folder == null) { folder = null } }
                    items(folders, key = { it.first }) { (id, name) ->
                        FilterChipSimple(name, folder == id) { folder = id }
                    }
                }
            }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } }, confirmButton = { Button(enabled = title.isNotBlank() || body.isNotBlank(), onClick = { onSave(folder, title, body, pinned) }) { Text(desktopText("Save")) } })
}

@Composable
private fun SimpleNameDialog(title: String, onDismiss: () -> Unit, initial: String = "", onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(desktopText("Name")) }) }, dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } }, confirmButton = { Button(enabled = value.isNotBlank(), onClick = { onSave(value.trim()) }) { Text(desktopText("Save")) } })
}

@Composable
private fun VaultPage(snapshot: BackupSnapshot, store: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<VaultEntry?>(null) }
    var query by remember { mutableStateOf("") }
    var visibleIds by remember { mutableStateOf(emptySet<String>()) }
    val entries = snapshot.vaultEntries.filter { query.isBlank() || it.label.contains(query, true) || it.account.contains(query, true) }
    Scaffold(floatingActionButton = { FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, null) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PageHeader("Vault", "Encrypted inside the local .tlb file")
            OutlinedTextField(query, { query = it }, label = { Text(desktopText("Search")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.label.ifBlank { desktopText("Untitled") }, fontWeight = FontWeight.Bold)
                                Text(entry.account, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (entry.id in visibleIds) entry.password else "••••••••")
                            }
                            IconButton({ visibleIds = if (entry.id in visibleIds) visibleIds - entry.id else visibleIds + entry.id }) { Icon(if (entry.id in visibleIds) Icons.Default.VisibilityOff else Icons.Default.Visibility, desktopText("Show password")) }
                            IconButton({ editing = entry }) { Icon(Icons.Default.Edit, desktopText("Edit")) }
                            IconButton({ scope.launch { store.deleteVault(entry.id) } }) { Icon(Icons.Default.Delete, desktopText("Delete")) }
                        }
                    }
                }
            }
        }
    }
    if (adding || editing != null) VaultEditorDialog(editing, { adding = false; editing = null }) { label, account, password, website, notes ->
        scope.launch { store.upsertVault(editing?.id, label, account, password, website, notes) }
        adding = false; editing = null
    }
}

@Composable
private fun VaultEditorDialog(entry: VaultEntry?, onDismiss: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    var label by remember { mutableStateOf(entry?.label.orEmpty()) }; var account by remember { mutableStateOf(entry?.account.orEmpty()) }; var password by remember { mutableStateOf(entry?.password.orEmpty()) }; var website by remember { mutableStateOf(entry?.website.orEmpty()) }; var notes by remember { mutableStateOf(entry?.notes.orEmpty()) }; var visible by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(desktopText(if (entry == null) "New Vault entry" else "Edit Vault entry")) }, text = { Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(label, { label = it }, label = { Text(desktopText("Label")) }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(account, { account = it }, label = { Text(desktopText("Account")) }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(password, { password = it }, label = { Text(desktopText("Password")) }, modifier = Modifier.fillMaxWidth(), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } }); OutlinedTextField(website, { website = it }, label = { Text(desktopText("Website")) }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(notes, { notes = it }, label = { Text(desktopText("Notes")) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
    } }, dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } }, confirmButton = { Button(enabled = listOf(label, account, password, website, notes).any(String::isNotBlank), onClick = { onSave(label, account, password, website, notes) }) { Text(desktopText("Save")) } })
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
) {
    val scope = rememberSafeCoroutineScope()
    var connectDialog by remember { mutableStateOf(false) }
    var importCandidate by remember { mutableStateOf<File?>(null) }
    var importPassword by remember { mutableStateOf("") }
    var importPasswordVisible by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    val language = LocalUiLanguage.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader("Settings")
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Cloud, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(desktopText("Google Drive sync"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(desktopText(if (cloudState.connected) "Connected" else "Not connected"), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            Text(desktopText("Encrypted snapshots are stored in Life Assistant's private Google Drive app folder. Other Drive files are not accessible."))
            Text(desktopText("Each upload creates a new encrypted version. Previous versions are kept; conflicts pause sync until you resolve them."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (cloudState.connected) {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(desktopText("Automatic sync")); Text(desktopText("Off by default. When enabled, checks every 15 minutes while Life Assistant is running"), style = MaterialTheme.typography.bodySmall) }; Switch(cloudState.automaticSync, cloud::setAutomaticSync) }
                cloudState.lastSyncAt?.let { Text(desktopLastSync(formatTimestamp(it), LocalUiLanguage.current), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(enabled = !cloudState.syncing, onClick = { scope.launch { cloud.synchronize() } }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(desktopText(if (cloudState.syncing) "Syncing…" else "Sync now")) }; TextButton(enabled = !cloudState.syncing, onClick = { scope.launch { cloud.disconnect() } }) { Text(desktopText("Disconnect / switch account")) } }
            } else Button(onClick = { connectDialog = true }) { Text(desktopText("Connect Google Drive")) }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(desktopText("Local security"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(desktopText("Local data is password-encrypted. Windows can remember the password using DPAPI for this Windows account.")); OutlinedButton(onClick = forgetLocalPassword) { Text(desktopText("Forget remembered password")) } } }
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
                        FilterChipSimple(mode.name.lowercase().replaceFirstChar(Char::uppercase), dataStore.currentSnapshot()?.settings?.themeMode == mode) {
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
                        FilterChipSimple(value.name.lowercase().replaceFirstChar(Char::uppercase), dataStore.currentSnapshot()?.settings?.weekStart == value) { scope.launch { dataStore.setWeekStart(value) } }
                    }
                }
                Text(desktopText("Time format"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TimeFormatOption.entries, key = { it.name }) { value ->
                        FilterChipSimple(value.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), dataStore.currentSnapshot()?.settings?.timeFormat == value) { scope.launch { dataStore.setTimeFormat(value) } }
                    }
                }
                Text(desktopText("Date format"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DateFormatOption.entries, key = { it.name }) { value ->
                        FilterChipSimple(value.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), dataStore.currentSnapshot()?.settings?.dateFormat == value) { scope.launch { dataStore.setDateFormat(value) } }
                    }
                }
            }
        }
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
                    desktopText("Before using a cloud version, Life Assistant keeps an encrypted local recovery copy. Import a copy to recover earlier local data. Recovery copies are not deleted automatically."),
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
    if (connectDialog) GoogleConnectDialog(config.clientId, { connectDialog = false }) { clientId, clientSecret -> connectDialog = false; scope.launch { cloud.connect(clientId, clientSecret) } }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(desktopText("Connect Google Drive")) }, text = { Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(desktopText("A Google Cloud Desktop OAuth client from the same project as the Android app is required for this open-source build. Sign-in opens in your system browser.")); OutlinedTextField(clientId, { clientId = it.trim() }, label = { Text(desktopText("Desktop OAuth client ID")) }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(secret, { secret = it }, label = { Text(desktopText("Desktop OAuth client secret (optional)")) }, modifier = Modifier.fillMaxWidth(), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } }); Text(desktopText("If supplied, the client secret is protected by Windows DPAPI. OAuth desktop secrets are application configuration, not a replacement for PKCE."), style = MaterialTheme.typography.bodySmall) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } }, confirmButton = { Button(enabled = clientId.endsWith(".apps.googleusercontent.com"), onClick = { val transferred = secret.toCharArray(); secret = ""; onConnect(clientId, transferred) }) { Text(desktopText("Open Google sign-in")) } })
}

private fun chooseAndAttach(scope: kotlinx.coroutines.CoroutineScope, store: DesktopDataStore, ownerType: AttachmentOwnerType, ownerId: Long) {
    scope.launch {
        val chooser = JFileChooser()
        val selected = chooser.takeIf { it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION }?.selectedFile
        if (selected != null) store.attachFile(ownerType, ownerId, selected)
    }
}

@Composable
private fun AttachmentList(
    snapshot: BackupSnapshot,
    ownerType: AttachmentOwnerType,
    ownerId: Long,
    store: DesktopDataStore,
) {
    val scope = rememberSafeCoroutineScope()
    val attachments = snapshot.attachments.filter { it.ownerType == ownerType && it.ownerId == ownerId }
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

private fun categoryPath(categoryId: Long, snapshot: BackupSnapshot): String {
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

private fun noteFolderPath(folderId: Long, snapshot: BackupSnapshot): String {
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
    DesktopDestination.VAULT, DesktopDestination.SETTINGS -> TopLevelDestination.NOTES
}

private fun TopLevelDestination.toDesktopDestination(): DesktopDestination = when (this) {
    TopLevelDestination.TODO -> DesktopDestination.TODO
    TopLevelDestination.LEDGER -> DesktopDestination.LEDGER
    TopLevelDestination.CALENDAR -> DesktopDestination.CALENDAR
    TopLevelDestination.NOTES -> DesktopDestination.NOTES
}

@Composable
private fun rememberSafeCoroutineScope(): CoroutineScope {
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

private fun formatMoney(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.US).apply { currency = Currency.getInstance("USD") }.format(cents / 100.0)
private fun compactMoney(cents: Long): String = when {
    cents >= 100_000_000L -> "$" + "%.1fM".format(Locale.US, cents / 100_000_000.0)
    cents >= 100_000L -> "$" + "%.1fk".format(Locale.US, cents / 100_000.0)
    else -> formatMoney(cents)
}
private fun formatCalendarNet(cents: Long): String = BigDecimal.valueOf(cents, 2)
    .stripTrailingZeros()
    .toPlainString()
private fun formatTimestamp(timestamp: Long): String = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US).format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

private fun parseAmountCents(value: String): Long? {
    val decimal = value.toBigDecimalOrNull() ?: return null
    if (decimal.signum() <= 0 || decimal.scale() !in 0..2) return null
    return runCatching { decimal.movePointRight(2).longValueExact() }
        .getOrNull()
        ?.takeIf { it in 1..99_999_999_999L }
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsDesktopState(): androidx.compose.runtime.State<T> = collectAsState()
