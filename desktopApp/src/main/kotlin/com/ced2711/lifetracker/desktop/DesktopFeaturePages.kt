package com.ced2711.lifetracker.desktop

import java.net.URI
import java.awt.datatransfer.StringSelection
import java.awt.Toolkit
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.AppIdentity
import com.ced2711.lifetracker.domain.model.MAX_CONFESSION_LENGTH
import com.ced2711.lifetracker.domain.model.diaryPreview
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EmberColor = Color(0xFFFF7043)
private const val DIARY_AUTOSAVE_DELAY_MILLIS = 700L

@Composable
internal fun desktopDiaryDayLabel(epochDay: Long, snapshot: BackupSnapshot): String {
    val locale = uiLocale(LocalUiLanguage.current)
    val date = LocalDate.ofEpochDay(epochDay)
    return UserFormatting.formatDate(date, snapshot.settings.dateFormat, locale) + "  " +
        UserFormatting.formatWeekday(date.dayOfWeek, locale, TextStyle.FULL)
}

@Composable
internal fun DiaryPage(
    snapshot: BackupSnapshot,
    store: DesktopDataStore,
    requestedDay: Long?,
    onRequestedDayHandled: () -> Unit,
) {
    val scope = rememberSafeCoroutineScope()
    var selectedDay by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    // Null means "showing the stored page"; a value is the user's unsaved or just-saved text.
    var draft by remember(selectedDay) { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val entries = snapshot.diaryEntries.sortedByDescending { it.epochDay }
    val stored = entries.firstOrNull { it.epochDay == selectedDay }
    val text = draft ?: stored?.body.orEmpty()

    fun flush(day: Long, value: String?) {
        val saved = store.currentSnapshot()?.diaryEntries?.firstOrNull { it.epochDay == day }?.body.orEmpty()
        if (value != null && value != saved) scope.launch { store.upsertDiary(day, value) }
    }

    fun select(day: Long) {
        if (day == selectedDay) return
        flush(selectedDay, draft)
        selectedDay = day
    }

    LaunchedEffect(requestedDay) {
        if (requestedDay != null) {
            select(requestedDay)
            onRequestedDayHandled()
        }
    }
    LaunchedEffect(selectedDay, draft) {
        val pending = draft ?: return@LaunchedEffect
        delay(DIARY_AUTOSAVE_DELAY_MILLIS)
        flush(selectedDay, pending)
    }
    val latestDay by rememberUpdatedState(selectedDay)
    val latestDraft by rememberUpdatedState(draft)
    DisposableEffect(Unit) {
        // The page scope is cancelled on leave, so the final save must outlive it.
        onDispose {
            val day = latestDay
            val value = latestDraft
            val saved = store.currentSnapshot()?.diaryEntries?.firstOrNull { it.epochDay == day }?.body.orEmpty()
            if (value != null && value != saved) {
                CoroutineScope(Dispatchers.Default).launch {
                    runCatching { store.upsertDiary(day, value) }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Diary", desktopText("Saved automatically. Clearing the text removes the entry."))
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entries.isEmpty()) {
                    item { Text(desktopText("No diary entries yet"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(entries, key = { it.epochDay }) { entry ->
                    Card(
                        Modifier.fillMaxWidth().clickable { select(entry.epochDay) },
                        colors = if (entry.epochDay == selectedDay) {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            CardDefaults.cardColors()
                        },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(desktopDiaryDayLabel(entry.epochDay, snapshot), fontWeight = FontWeight.SemiBold)
                            Text(diaryPreview(entry.body), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ select(selectedDay - 1) }) { Icon(Icons.Default.ChevronLeft, desktopText("Previous day")) }
                    Text(
                        desktopDiaryDayLabel(selectedDay, snapshot),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton({ select(selectedDay + 1) }) { Icon(Icons.Default.ChevronRight, desktopText("Next day")) }
                    if (selectedDay != LocalDate.now().toEpochDay()) {
                        TextButton({ select(LocalDate.now().toEpochDay()) }) { Text(desktopText("Today")) }
                    }
                    if (stored != null) {
                        IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, desktopText("Delete diary entry")) }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    placeholder = { Text(desktopText("How was your day?")) },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(desktopText("Delete this diary entry?")) },
            text = { Text(desktopDiaryDayLabel(selectedDay, snapshot)) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    draft = null
                    scope.launch { store.deleteDiary(selectedDay) }
                }) { Text(desktopText("Delete")) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(desktopText("Cancel")) } },
        )
    }
}

/**
 * Asks for the local data password. [verify] consumes the array it receives, so the typed
 * characters never linger after the check.
 */
@Composable
internal fun DataPasswordDialog(
    title: String,
    message: String,
    verify: (CharArray) -> Boolean,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    fun submit() {
        val candidate = password.toCharArray()
        password = ""
        if (verify(candidate)) onVerified() else failed = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(desktopText(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(desktopText(message))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; failed = false },
                    label = { Text(desktopText("Data password")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = failed,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (failed) Text(desktopText("The password is incorrect."), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(onClick = ::submit, enabled = password.isNotEmpty()) { Text(desktopText("Confirm")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } },
    )
}

@Composable
internal fun DesktopAppLockScreen(verify: (CharArray) -> Boolean, onUnlocked: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    fun submit() {
        val candidate = password.toCharArray()
        password = ""
        if (verify(candidate)) onUnlocked() else failed = true
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 420.dp).padding(24.dp)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(desktopText("${AppIdentity.NAME} is locked"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(desktopText("Enter your data password to continue."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; failed = false },
                    label = { Text(desktopText("Data password")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = failed,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (failed) Text(desktopText("The password is incorrect."), color = MaterialTheme.colorScheme.error)
                Button(onClick = ::submit, enabled = password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(desktopText("Unlock"))
                }
            }
        }
    }
}

@Composable
internal fun ConfessionalPage(store: DesktopConfessionStore, verifyPassword: (CharArray) -> Boolean) {
    val scope = rememberSafeCoroutineScope()
    val sealed by store.entries.collectAsDesktopState()
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var askPassword by remember { mutableStateOf(false) }
    var confirmBurnAll by remember { mutableStateOf(false) }
    val burn = remember { Animatable(0f) }
    val burning = burn.isRunning

    LaunchedEffect(store) { store.load() }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Confessional")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    desktopText(
                        "Say what you need to say. Burn it to let it go for good, or seal it on this PC. " +
                            "Sealed words are protected by Windows and never exported or synced.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(contentAlignment = Alignment.Center) {
                    val progress = burn.value
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= MAX_CONFESSION_LENGTH) text = it },
                        enabled = !burning,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).graphicsLayer {
                            alpha = 1f - progress
                            scaleY = 1f - progress * 0.35f
                            translationY = -progress * 80f
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = lerp(MaterialTheme.colorScheme.onSurface, EmberColor, progress),
                        ),
                        placeholder = { Text(desktopText("Write it down…")) },
                    )
                    if (burning) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            null,
                            tint = EmberColor,
                            modifier = Modifier.size(80.dp).graphicsLayer {
                                val flare = if (progress < 0.6f) progress / 0.6f else (1f - progress) / 0.4f
                                alpha = flare.coerceIn(0f, 1f)
                                scaleX = 0.6f + progress
                                scaleY = 0.6f + progress
                            },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                burn.snapTo(0f)
                                burn.animateTo(1f, tween(1_400, easing = FastOutSlowInEasing))
                                text = ""
                                burn.snapTo(0f)
                                message = "Burned. It's gone."
                            }
                        },
                        enabled = text.isNotBlank() && !burning,
                        colors = ButtonDefaults.buttonColors(containerColor = EmberColor, contentColor = Color.Black),
                    ) {
                        Icon(Icons.Default.LocalFireDepartment, null)
                        Text(desktopText("Burn"), Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                store.seal(text)
                                text = ""
                                message = "Sealed on this PC."
                            }
                        },
                        enabled = text.isNotBlank() && !burning,
                    ) {
                        Icon(Icons.Default.Lock, null)
                        Text(desktopText("Seal"), Modifier.padding(start = 8.dp))
                    }
                }
                message?.let { Text(desktopText(it), color = MaterialTheme.colorScheme.primary) }

                val sealedEntries = sealed.orEmpty()
                if (sealedEntries.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            desktopText("Sealed confessions") + " (${sealedEntries.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (revealed) {
                            TextButton({ revealed = false }) { Text(desktopText("Hide")) }
                            TextButton({ confirmBurnAll = true }) { Text(desktopText("Burn all")) }
                        } else {
                            TextButton({ askPassword = true }) { Text(desktopText("Open")) }
                        }
                    }
                    if (revealed) {
                        val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                        sealedEntries.forEach { entry ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            format.format(Date(entry.createdAt)),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton({ scope.launch { store.burn(entry.id) } }) {
                                            Icon(Icons.Default.LocalFireDepartment, desktopText("Burn"), tint = EmberColor)
                                        }
                                    }
                                    Text(entry.text)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (askPassword) {
        DataPasswordDialog(
            title = "Open sealed confessions",
            message = "Enter your data password to read sealed confessions.",
            verify = verifyPassword,
            onVerified = { askPassword = false; revealed = true },
            onDismiss = { askPassword = false },
        )
    }
    if (confirmBurnAll) {
        AlertDialog(
            onDismissRequest = { confirmBurnAll = false },
            title = { Text(desktopText("Burn every sealed confession?")) },
            text = { Text(desktopText("They will be permanently deleted from this PC.")) },
            confirmButton = {
                TextButton({
                    confirmBurnAll = false
                    revealed = false
                    scope.launch { store.burnAll() }
                }) { Text(desktopText("Burn all")) }
            },
            dismissButton = { TextButton({ confirmBurnAll = false }) { Text(desktopText("Cancel")) } },
        )
    }
}

@Composable
internal fun GitHubConnectDialog(
    defaultClientId: String,
    defaultRepository: String,
    onDismiss: () -> Unit,
    onConnect: (clientId: String, repository: String) -> Unit,
) {
    var clientId by remember { mutableStateOf(defaultClientId) }
    var repository by remember { mutableStateOf(defaultRepository) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(desktopText("Connect GitHub")) },
        text = {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    desktopText(
                        "This build has no built-in GitHub sign-in. Enter the Client ID of a GitHub OAuth App with " +
                            "Device Flow on. Leave the repository empty to create a private life-assistant-data repository.",
                    ),
                )
                OutlinedTextField(clientId, { clientId = it.trim() }, label = { Text(desktopText("GitHub Client ID")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(repository, { repository = it }, label = { Text(desktopText("Private repository (owner/name, optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    desktopText("Your data password encrypts every backup before upload. Use the same password on every device."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } },
        confirmButton = {
            Button(enabled = clientId.isNotBlank(), onClick = { onConnect(clientId, repository.trim()) }) {
                Text(desktopText("Get sign-in code"))
            }
        },
    )
}

@Composable
internal fun GitHubCodeDialog(userCode: String, verificationUri: String, onCancel: () -> Unit) {
    // Copy the code and open the page right away, so the user only pastes and approves.
    LaunchedEffect(userCode) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(userCode), null) }
        runCatching { java.awt.Desktop.getDesktop().browse(URI(verificationUri)) }
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(desktopText("Approve on GitHub")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(desktopText("GitHub opened in your browser and this code is already copied. Paste it there, then choose Authorize. This window continues by itself."))
                SelectionContainer {
                    Text(userCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Text(verificationUri, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(userCode), null)
                    }) { Text(desktopText("Copy code")) }
                    Button(onClick = {
                        runCatching { java.awt.Desktop.getDesktop().browse(URI(verificationUri)) }
                    }) { Text(desktopText("Open GitHub")) }
                }
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(desktopText("Cancel")) } },
    )
}
