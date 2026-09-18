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
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.domain.date.SmartDateParser
import java.io.File
import java.io.IOException
import java.awt.Desktop
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
    var appError by remember { mutableStateOf<String?>(null) }
    val reportError: (Throwable) -> Unit = remember {
        { error -> appError = desktopErrorMessage(error) }
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
    CompositionLocalProvider(LocalDesktopErrorReporter provides reportError) {
        MaterialTheme(colorScheme = lifeTrackerColors(accent)) {
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
                )
                }
            }
            appError?.let { message ->
                AlertDialog(
                    onDismissRequest = { appError = null },
                    title = { Text("Operation failed") },
                    text = { Text(message) },
                    confirmButton = { Button(onClick = { appError = null }) { Text("OK") } },
                )
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
                Text("Life Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (existingData) "Unlock your encrypted local data." else
                        "Create an encrypted local data file. Use this same password for Google Drive sync.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Data password") },
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
                    Text("Remember securely with Windows")
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                Button(
                    enabled = password.length >= 8,
                    onClick = {
                        val transferred = password.toCharArray()
                        password = ""
                        onUnlock(transferred, rememberOnPc)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (existingData) "Unlock" else "Create local data")
                }
                Text(
                    "Your password is never uploaded. If remembered, it is protected by Windows DPAPI for this Windows account.",
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
) {
    var destination by remember { mutableStateOf(DesktopDestination.TODO) }
    val scope = rememberSafeCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val cloudState by cloud.state.collectAsDesktopState()

    LaunchedEffect(cloudState.message) {
        cloudState.message?.let {
            snackbar.showSnackbar(it)
            cloud.acknowledgeMessage()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 850.dp
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        DesktopDestination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = item == destination,
                                onClick = { destination = item },
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label) },
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
                        DesktopDestination.entries.forEach { item ->
                            NavigationRailItem(
                                selected = item == destination,
                                onClick = { destination = item },
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                    HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (destination) {
                        DesktopDestination.TODO -> TodoPage(snapshot, dataStore)
                        DesktopDestination.LEDGER -> LedgerPage(snapshot, dataStore)
                        DesktopDestination.CALENDAR -> CalendarPage(snapshot, dataStore)
                        DesktopDestination.NOTES -> NotesPage(snapshot, dataStore)
                        DesktopDestination.VAULT -> VaultPage(snapshot, dataStore)
                        DesktopDestination.SETTINGS -> SettingsPage(
                            cloudState = cloudState,
                            cloud = cloud,
                            config = configStore.read(),
                            dataStore = dataStore,
                            forgetLocalPassword = { credentials.delete(WindowsCredentialStore.LOCAL_PASSWORD) },
                        )
                    }
                }
            }
        }

        cloudState.conflict?.let {
            AlertDialog(
                onDismissRequest = cloud::dismissConflict,
                title = { Text("Sync conflict") },
                text = { Text("This PC and Google Drive both changed. Life Tracker will keep the history and create a new merged cloud revision from your choice.") },
                dismissButton = { TextButton(onClick = cloud::dismissConflict) { Text("Cancel") } },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { scope.launch { cloud.synchronize(ConflictResolution.USE_CLOUD) } }) {
                            Text("Use newest cloud")
                        }
                        Button(onClick = { scope.launch { cloud.synchronize(ConflictResolution.KEEP_LOCAL) } }) {
                            Text("Keep this PC")
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
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodoPage(snapshot: BackupSnapshot, store: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    var editing by remember { mutableStateOf<TodoEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Long?>(null) }
    var newCategory by remember { mutableStateOf(false) }
    val categoryParents = snapshot.categories.associate { it.id to it.parentId }
    val includedCategoryIds = selectedCategory?.let { selected ->
        snapshot.categories.mapNotNull { candidate ->
            var cursor: Long? = candidate.id
            val visited = mutableSetOf<Long>()
            while (cursor != null && visited.add(cursor)) {
                if (cursor == selected) return@mapNotNull candidate.id
                cursor = categoryParents[cursor]
            }
            null
        }.toSet()
    }
    val todos = snapshot.todos
        .filter { it.deletedAt == null && (showCompleted || it.completedAt == null) }
        .filter { includedCategoryIds == null || it.categoryId in includedCategoryIds }
        .filter { query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true) }
        .sortedWith(compareBy<TodoEntity> { it.completedAt != null }.thenBy { it.deadlineEpochDay ?: Long.MAX_VALUE })
    Scaffold(
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, null) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PageHeader("Todo", "${todos.count { it.completedAt == null }} active tasks")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(showCompleted, { showCompleted = it })
                    Text("Completed")
                }
            }
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChipSimple("All", selectedCategory == null) { selectedCategory = null } }
                items(snapshot.categories, key = { it.id }) { category ->
                    FilterChipSimple(
                        categoryPath(category.id, snapshot),
                        selectedCategory == category.id,
                    ) { selectedCategory = category.id }
                }
                item { TextButton(onClick = { newCategory = true }) { Icon(Icons.Default.Add, null); Text("Category") } }
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
                                    Text("Due ${LocalDate.ofEpochDay(it)}", style = MaterialTheme.typography.bodySmall)
                                }
                                AttachmentList(snapshot, AttachmentOwnerType.TODO, todo.id, store)
                            }
                            IconButton({ chooseAndAttach(scope, store, AttachmentOwnerType.TODO, todo.id) }) {
                                Icon(Icons.Default.AttachFile, "Attach")
                            }
                            IconButton({ editing = todo }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton({ scope.launch { store.deleteTodo(todo.id) } }) { Icon(Icons.Default.Delete, "Delete") }
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
        SimpleNameDialog("New category", { newCategory = false }) { name ->
            scope.launch { store.addCategory(name, selectedCategory) }
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
        title = { Text(if (todo == null) "New todo" else "Edit todo") },
        text = {
            Column(Modifier.width(520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(title, { title = it }, label = { Text("Title (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(deadline, { deadline = it }, label = { Text("Deadline M/D/YYYY, M/D, or day (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it }, label = { Text("Tags, comma separated (optional)") }, modifier = Modifier.fillMaxWidth())
                Text("Category")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChipSimple("None", categoryId == null) { categoryId = null } }
                    items(snapshot.categories, key = { it.id }) { category ->
                        FilterChipSimple(
                            categoryPath(category.id, snapshot),
                            categoryId == category.id,
                        ) { categoryId = category.id }
                    }
                }
                Text("Priority")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(TodoPriority.NONE, TodoPriority.LOW, TodoPriority.MEDIUM, TodoPriority.HIGH).forEach { item ->
                        FilterChipSimple(item.name.lowercase().replaceFirstChar(Char::uppercase), item == priority) { priority = item }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(completed, { completed = it })
                    Text("Done")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            val day = deadline.takeIf(String::isNotBlank)?.let { SmartDateParser.parse(it, LocalDate.now())?.toEpochDay() }
            Button(enabled = description.isNotBlank() && (deadline.isBlank() || day != null), onClick = {
                onSave(title, description, day, priority, categoryId, tags, completed)
            }) { Text("Save") }
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
            PageHeader("Ledger", "Net ${formatMoney(income - expense)}")
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Income", formatMoney(income), Color(0xFF65D28A), Modifier.weight(1f))
                SummaryCard("Expense", formatMoney(expense), Color(0xFFFF756B), Modifier.weight(1f))
            }
            LedgerTrend(entries, Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 24.dp, vertical = 12.dp))
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.merchant.ifBlank { entry.note.ifBlank { entry.type.name.lowercase().replaceFirstChar(Char::uppercase) } }, fontWeight = FontWeight.SemiBold)
                                Text(LocalDate.ofEpochDay(entry.epochDay).toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AttachmentList(snapshot, AttachmentOwnerType.LEDGER, entry.id, store)
                            }
                            Text(
                                (if (entry.type == LedgerType.INCOME) "+" else "−") + formatMoney(entry.amountCents),
                                color = if (entry.type == LedgerType.INCOME) Color(0xFF65D28A) else Color(0xFFFF756B),
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton({ chooseAndAttach(scope, store, AttachmentOwnerType.LEDGER, entry.id) }) { Icon(Icons.Default.AttachFile, "Attach") }
                            IconButton({ editing = entry }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton({ scope.launch { store.deleteLedger(entry.id) } }) { Icon(Icons.Default.Delete, "Delete") }
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
private fun LedgerTrend(entries: List<LedgerEntryEntity>, modifier: Modifier) {
    val values = entries.groupBy { it.epochDay }.toSortedMap().entries.toList().takeLast(7).map { (day, rows) ->
        day to rows.sumOf { if (it.type == LedgerType.INCOME) it.amountCents else -it.amountCents }
    }
    val maximum = values.maxOfOrNull { kotlin.math.abs(it.second) }?.coerceAtLeast(1L) ?: 1L
    val scaleMaximum = ((maximum * 11L) + 9L) / 10L
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Daily net  •  green = net income  •  red = net expense", style = MaterialTheme.typography.bodySmall)
            Text("Range ±${formatMoney(scaleMaximum)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (values.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No ledger data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("M/d", Locale.US)),
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
        title = { Text(if (entry == null) "New ledger entry" else "Edit ledger entry") },
        text = {
            Column(Modifier.width(480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row { LedgerType.entries.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(type == item, { type = item }); Text(item.name.lowercase().replaceFirstChar(Char::uppercase)) } } }
                OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Date M/D/YYYY, M/D, or day") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant / payer") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it }, label = { Text("Tags, comma separated (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            val cents = parseAmountCents(amount)
            val day = SmartDateParser.parse(date, LocalDate.now())?.toEpochDay()
            Button(enabled = cents != null && day != null, onClick = { onSave(type, requireNotNull(cents), requireNotNull(day), note, merchant, tags) }) { Text("Save") }
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
    val offset = first.dayOfWeek.value % 7
    val days = List(offset) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "Previous") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton({ month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "Next") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                    Modifier.width(620.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Todos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (todoRows.isEmpty()) Text("No todos", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    Icon(Icons.Default.Edit, "Edit todo")
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Ledger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (ledgerRows.isEmpty()) Text("No ledger entries", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ledgerRows.forEach { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.merchant.ifBlank { entry.note.ifBlank { entry.type.name } }, Modifier.weight(1f))
                                Text(
                                    (if (entry.type == LedgerType.INCOME) "+" else "−") + formatMoney(entry.amountCents),
                                    color = if (entry.type == LedgerType.INCOME) Color(0xFF65D28A) else Color(0xFFFF756B),
                                )
                                IconButton(onClick = { selectedDate = null; editingLedger = entry }) {
                                    Icon(Icons.Default.Edit, "Edit ledger entry")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedDate = null }) { Text("Close") } },
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
private fun NotesPage(snapshot: BackupSnapshot, store: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<Long?>(null) }
    var newFolder by remember { mutableStateOf(false) }
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
        (includedFolderIds == null || it.folderId in includedFolderIds) &&
            (query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true))
    }
        .sortedWith(compareByDescending<NoteEntity> { it.pinned }.thenByDescending { it.updatedAt })
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(220.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .25f)).padding(12.dp)) {
            Text("Folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton({ selectedFolder = null }) { Text("All notes") }
            snapshot.noteFolders.forEach { folder ->
                TextButton({ selectedFolder = folder.id }) { Text(noteFolderPath(folder.id, snapshot)) }
            }
            TextButton({ newFolder = true }) { Icon(Icons.Default.Add, null); Text("Folder") }
        }
        Scaffold(floatingActionButton = { FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, null) } }, modifier = Modifier.weight(1f)) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                PageHeader("Notes", "${notes.size} notes")
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Card(Modifier.fillMaxWidth().clickable { editing = note }) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (note.pinned) "★ ${note.title}" else note.title, fontWeight = FontWeight.SemiBold)
                                    Text(note.body, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    AttachmentList(snapshot, AttachmentOwnerType.NOTE, note.id, store)
                                }
                                IconButton({ chooseAndAttach(scope, store, AttachmentOwnerType.NOTE, note.id) }) { Icon(Icons.Default.AttachFile, "Attach") }
                                IconButton({ scope.launch { store.deleteNote(note.id) } }) { Icon(Icons.Default.Delete, "Delete") }
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
    if (newFolder) SimpleNameDialog("New folder", { newFolder = false }) { scope.launch { store.addNoteFolder(it, selectedFolder) }; newFolder = false }
}

@Composable
private fun NoteEditorDialog(note: NoteEntity?, folders: List<Pair<Long, String>>, defaultFolder: Long?, onDismiss: () -> Unit, onSave: (Long?, String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf(note?.title.orEmpty()) }
    var body by remember { mutableStateOf(note?.body.orEmpty()) }
    var pinned by remember { mutableStateOf(note?.pinned ?: false) }
    var folder by remember { mutableStateOf(note?.folderId ?: defaultFolder) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (note == null) "New note" else "Edit note") }, text = {
        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 12)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(pinned, { pinned = it }); Text("Pinned") }
            if (folders.isNotEmpty()) {
                Text("Folder")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChipSimple("None", folder == null) { folder = null } }
                    items(folders, key = { it.first }) { (id, name) ->
                        FilterChipSimple(name, folder == id) { folder = id }
                    }
                }
            }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = { Button(enabled = title.isNotBlank() || body.isNotBlank(), onClick = { onSave(folder, title, body, pinned) }) { Text("Save") } })
}

@Composable
private fun SimpleNameDialog(title: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text("Name") }) }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = { Button(enabled = value.isNotBlank(), onClick = { onSave(value.trim()) }) { Text("Save") } })
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
            OutlinedTextField(query, { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.label.ifBlank { "Untitled" }, fontWeight = FontWeight.Bold)
                                Text(entry.account, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (entry.id in visibleIds) entry.password else "••••••••")
                            }
                            IconButton({ visibleIds = if (entry.id in visibleIds) visibleIds - entry.id else visibleIds + entry.id }) { Icon(if (entry.id in visibleIds) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show password") }
                            IconButton({ editing = entry }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton({ scope.launch { store.deleteVault(entry.id) } }) { Icon(Icons.Default.Delete, "Delete") }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (entry == null) "New Vault entry" else "Edit Vault entry") }, text = { Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(label, { label = it }, label = { Text("Label") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(account, { account = it }, label = { Text("Account") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } }); OutlinedTextField(website, { website = it }, label = { Text("Website") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
    } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = { Button(enabled = listOf(label, account, password, website, notes).any(String::isNotBlank), onClick = { onSave(label, account, password, website, notes) }) { Text("Save") } })
}

@Composable
private fun SettingsPage(
    cloudState: DesktopCloudUiState,
    cloud: DesktopCloudSyncController,
    config: DesktopCloudConfig,
    dataStore: DesktopDataStore,
    forgetLocalPassword: () -> Unit,
) {
    val scope = rememberSafeCoroutineScope()
    var connectDialog by remember { mutableStateOf(false) }
    var importCandidate by remember { mutableStateOf<File?>(null) }
    var importPassword by remember { mutableStateOf("") }
    var importPasswordVisible by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader("Settings")
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Cloud, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Google Drive sync", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(if (cloudState.connected) "Connected" else "Not connected", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            Text("Encrypted snapshots are stored in Life Tracker's private Google Drive app folder. Other Drive files are not accessible.")
            Text("Keeps the 30 most recent revisions. Competing branches are retained until you resolve the conflict.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (cloudState.connected) {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Automatic sync"); Text("Checks every 15 minutes while Life Tracker is running", style = MaterialTheme.typography.bodySmall) }; Switch(cloudState.automaticSync, cloud::setAutomaticSync) }
                cloudState.lastSyncAt?.let { Text("Last sync: ${formatTimestamp(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(enabled = !cloudState.syncing, onClick = { scope.launch { cloud.synchronize() } }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(if (cloudState.syncing) "Syncing…" else "Sync now") }; TextButton(enabled = !cloudState.syncing, onClick = { scope.launch { cloud.disconnect() } }) { Text("Disconnect / switch account") } }
            } else Button(onClick = { connectDialog = true }) { Text("Connect Google Drive") }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Local security", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Local data is password-encrypted. Windows can remember the password using DPAPI for this Windows account."); OutlinedButton(onClick = forgetLocalPassword) { Text("Forget remembered password") } } }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Accent color")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AccentColor.entries, key = { it.name }) { accent ->
                        FilterChipSimple(
                            accent.name.lowercase().replaceFirstChar(Char::uppercase),
                            dataStore.currentSnapshot()?.settings?.accentColor == accent,
                        ) { scope.launch { dataStore.setAccentColor(accent) } }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Encrypted backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("The .tlb file includes todos, ledger entries, notes, Vault entries, and attached files. Manual import can migrate an older Android backup password to this PC's current data password.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val chooser = JFileChooser().apply { selectedFile = File("LifeTracker-backup.tlb") }
                        val destination = chooser.takeIf {
                            it.showSaveDialog(null) == JFileChooser.APPROVE_OPTION
                        }?.selectedFile
                        if (destination != null) scope.launch {
                            val upload = dataStore.createUploadSnapshot()
                            try {
                                withContext(Dispatchers.IO) { upload.file.copyTo(destination, overwrite = true) }
                                backupMessage = "Encrypted backup exported."
                            } finally {
                                upload.file.delete()
                            }
                        }
                    }) { Text("Export backup") }
                    OutlinedButton(onClick = {
                        val chooser = JFileChooser()
                        importCandidate = chooser.takeIf {
                            it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
                        }?.selectedFile
                        importPassword = ""
                    }) { Text("Import backup") }
                }
                backupMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }
        Text("Life Tracker Desktop 1.6.0 • Data format compatible with Android", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (connectDialog) GoogleConnectDialog(config.clientId, { connectDialog = false }) { clientId, clientSecret -> connectDialog = false; scope.launch { cloud.connect(clientId, clientSecret) } }
    importCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { importCandidate = null; importPassword = "" },
            title = { Text("Import encrypted backup?") },
            text = {
                Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter the password used when this backup was created. After validation, its contents will be encrypted with this PC's current data password.")
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text("Source backup password") },
                        singleLine = true,
                        visualTransformation = if (importPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { importPasswordVisible = !importPasswordVisible }) {
                                Icon(if (importPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("This replaces the local records. Export the current data first if you may need it later.", style = MaterialTheme.typography.bodySmall)
                }
            },
            dismissButton = { TextButton(onClick = { importCandidate = null; importPassword = "" }) { Text("Cancel") } },
            confirmButton = {
                Button(enabled = importPassword.length >= 8, onClick = {
                    val sourcePassword = importPassword.toCharArray()
                    importPassword = ""
                    importCandidate = null
                    scope.launch {
                        try {
                            val expected = dataStore.localFingerprint()
                            backupMessage = when (dataStore.importFromEncrypted(candidate, sourcePassword, expected)) {
                                is DesktopReplaceResult.Applied -> "Backup imported and encrypted with the current data password."
                                DesktopReplaceResult.LocalChanged -> "Local data changed; import was cancelled."
                                DesktopReplaceResult.Invalid -> "The source password is incorrect or the backup is damaged."
                            }
                        } finally {
                            sourcePassword.fill('\u0000')
                        }
                    }
                }) { Text("Restore") }
            },
        )
    }
}

@Composable
private fun GoogleConnectDialog(defaultClientId: String, onDismiss: () -> Unit, onConnect: (String, CharArray) -> Unit) {
    var clientId by remember { mutableStateOf(defaultClientId) }; var secret by remember { mutableStateOf("") }; var visible by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Connect Google Drive") }, text = { Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("A Google Cloud Desktop OAuth client from the same project as the Android app is required for this open-source build. Sign-in opens in your system browser."); OutlinedTextField(clientId, { clientId = it.trim() }, label = { Text("Desktop OAuth client ID") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(secret, { secret = it }, label = { Text("Desktop OAuth client secret (optional)") }, modifier = Modifier.fillMaxWidth(), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } }); Text("If supplied, the client secret is protected by Windows DPAPI. OAuth desktop secrets are application configuration, not a replacement for PKCE.", style = MaterialTheme.typography.bodySmall) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = { Button(enabled = clientId.endsWith(".apps.googleusercontent.com"), onClick = { val transferred = secret.toCharArray(); secret = ""; onConnect(clientId, transferred) }) { Text("Open Google sign-in") } })
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
            ) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open attachment", Modifier.size(18.dp)) }
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
            ) { Icon(Icons.Default.Download, "Save a copy", Modifier.size(18.dp)) }
            IconButton(
                onClick = { scope.launch { store.removeAttachment(attachment.id) } },
            ) { Icon(Icons.Default.Delete, "Remove attachment", Modifier.size(18.dp)) }
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

@Composable
private fun rememberSafeCoroutineScope(): CoroutineScope {
    val reportError = LocalDesktopErrorReporter.current
    val handler = remember(reportError) {
        CoroutineExceptionHandler { _, error -> reportError(error) }
    }
    return rememberCoroutineScope { handler }
}

private fun desktopErrorMessage(error: Throwable): String = when (error) {
    is IOException -> "The file operation could not be completed. Check available storage and file access, then try again."
    is IllegalArgumentException -> error.message?.takeIf { it.isNotBlank() }?.take(220)
        ?: "One of the entered values is invalid."
    else -> "The operation could not be completed. Your last saved data was kept."
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
