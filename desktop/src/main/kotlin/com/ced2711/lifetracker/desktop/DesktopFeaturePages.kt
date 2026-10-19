package com.ced2711.lifetracker.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.AppIdentity
import com.ced2711.lifetracker.domain.model.MAX_CONFESSION_LENGTH
import com.ced2711.lifetracker.domain.model.diaryPreview
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
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

/**
 * The data password as the rest of the UI sees it. A new PC opens without one (it keeps a random
 * key itself); the user picks a password the first time a feature needs it.
 */
class DesktopSecurity(
    val passwordChosen: () -> Boolean,
    val verify: (CharArray) -> Boolean,
    /** Makes the given password the data password; consumes the array. */
    val choose: suspend (CharArray) -> Boolean,
)

val LocalDesktopSecurity = staticCompositionLocalOf { DesktopSecurity({ true }, { false }, { false }) }

@Composable
internal fun desktopDiaryDayLabel(epochDay: Long, snapshot: BackupSnapshot): String {
    val locale = uiLocale(LocalUiLanguage.current)
    val date = LocalDate.ofEpochDay(epochDay)
    return UserFormatting.formatDate(date, snapshot.settings.dateFormat, locale) + "  " +
        UserFormatting.formatWeekday(date.dayOfWeek, locale, TextStyle.FULL)
}

/** One page per day: the days with a page on the left, the page being written on the right. */
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
    val pageFocus = remember { FocusRequester() }

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
    LaunchedEffect(selectedDay) { runCatching { pageFocus.requestFocus() } }
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

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            PageHeader("Diary", desktopText("Saved as you type"))
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = PagePadding - Space.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (entries.isEmpty()) {
                    item { Text(desktopText("No diary entries yet"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.md)) }
                }
                items(entries, key = { it.epochDay }) { entry ->
                    val interaction = remember { MutableInteractionSource() }
                    val hovered by interaction.collectIsHoveredAsState()
                    val isSelected = entry.epochDay == selectedDay
                    val background by animateColorAsState(
                        when {
                            isSelected -> LifeTheme.colors.accentSoft
                            hovered -> MaterialTheme.colorScheme.surfaceContainer
                            else -> Color.Transparent
                        },
                        tween(120),
                        label = "diary-row",
                    )
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(background).hoverable(interaction)
                            .clickable { select(entry.epochDay) }.padding(horizontal = Space.md, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(desktopDiaryDayLabel(entry.epochDay, snapshot), style = MaterialTheme.typography.titleSmall)
                        Text(diaryPreview(entry.body), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { Spacer(Modifier.height(48.dp)) }
            }
        }
        ColumnDivider()
        Column(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 22.dp, bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(desktopDiaryDayLabel(selectedDay, snapshot), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (selectedDay != LocalDate.now().toEpochDay()) {
                    TextButton({ select(LocalDate.now().toEpochDay()) }) { Text(desktopText("Today")) }
                }
                IconButton({ select(selectedDay - 1) }) { Icon(Icons.Rounded.ChevronLeft, desktopText("Previous day")) }
                IconButton({ select(selectedDay + 1) }) { Icon(Icons.Rounded.ChevronRight, desktopText("Next day")) }
                if (stored != null) {
                    IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, desktopText("Delete diary entry"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            // A page about as wide as a book reads better than one stretched across the screen.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PlainField(
                    value = text,
                    onValueChange = { draft = it },
                    placeholder = desktopText("How was your day?"),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f),
                    singleLine = false,
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = Space.md, bottom = 64.dp).focusRequester(pageFocus),
                    minHeight = 420,
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
                Button({
                    confirmDelete = false
                    draft = null
                    scope.launch { store.deleteDiary(selectedDay) }
                }) { Text(desktopText("Delete")) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(desktopText("Cancel")) } },
        )
    }
}

/** A password field with a show/hide button. */
@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    LifeTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.size(28.dp)) {
                Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, desktopText(if (visible) "Hide password" else "Show password"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = modifier,
    )
}

/**
 * Confirms it is the user before something private: asks for the data password, or, on a PC that
 * has none yet, lets the user choose it first. [verify] consumes the array it receives.
 */
@Composable
internal fun DataPasswordDialog(
    title: String,
    message: String,
    verify: (CharArray) -> Boolean,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
) {
    val security = LocalDesktopSecurity.current
    if (!security.passwordChosen()) {
        ChoosePasswordDialog(
            title = "Choose a password first",
            message = "This PC has no password yet. Choose one to protect Vault, sealed confessions and the app lock. It also encrypts cloud sync, so use the same one on your other devices.",
            onChosen = onVerified,
            onDismiss = onDismiss,
        )
        return
    }
    var password by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun submit() {
        if (password.isEmpty()) return
        val candidate = password.toCharArray()
        password = ""
        if (verify(candidate)) onVerified() else failed = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(desktopText(title)) },
        text = {
            Column(Modifier.widthIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(desktopText(message))
                PasswordField(password, { password = it; failed = false }, desktopText("Data password"), Modifier.fillMaxWidth().focusRequester(focus).onEnter(::submit), isError = failed)
                if (failed) Text(desktopText("The password is incorrect."), color = LifeTheme.colors.danger, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = ::submit, enabled = password.isNotEmpty()) { Text(desktopText("Confirm")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } },
    )
}

/** Picks the data password (twice, at least 8 characters) and applies it. */
@Composable
internal fun ChoosePasswordDialog(
    title: String,
    message: String,
    onChosen: () -> Unit,
    onDismiss: () -> Unit,
    /** When given, the password goes here instead of becoming the data password directly. */
    onSubmit: ((CharArray) -> Unit)? = null,
) {
    val security = LocalDesktopSecurity.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val valid = password.length >= 8 && password == confirmation
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun submit() {
        if (!valid || working) return
        val chosen = password.toCharArray()
        password = ""
        confirmation = ""
        if (onSubmit != null) {
            onSubmit(chosen)
            onChosen()
            return
        }
        working = true
        scope.launch {
            val done = security.choose(chosen)
            working = false
            if (done) onChosen() else failed = true
        }
    }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(desktopText(title)) },
        text = {
            Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(desktopText(message))
                PasswordField(password, { password = it; failed = false }, desktopText("New password (at least 8 characters)"), Modifier.fillMaxWidth().focusRequester(focus))
                PasswordField(confirmation, { confirmation = it }, desktopText("Repeat the password"), Modifier.fillMaxWidth().onEnter(::submit), isError = confirmation.isNotEmpty() && confirmation != password)
                Text(desktopText("Nobody can recover this password for you. Write it down somewhere safe."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (failed) Text(desktopText("The password could not be changed. Your data was kept as it is."), color = LifeTheme.colors.danger, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = ::submit, enabled = valid && !working) { Text(desktopText(if (working) "Working…" else "Set password")) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text(desktopText("Cancel")) } },
    )
}

/** A centred card for the screens shown instead of the app: locked, or asking for the password. */
@Composable
internal fun CenteredCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Panel(Modifier.widthIn(max = 420.dp).padding(Space.xxl), padding = PaddingValues(32.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.lg), content = content)
        }
    }
}

@Composable
internal fun DesktopAppLockScreen(verify: (CharArray) -> Boolean, onUnlocked: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun submit() {
        if (password.isEmpty()) return
        val candidate = password.toCharArray()
        password = ""
        if (verify(candidate)) onUnlocked() else failed = true
    }
    CenteredCard {
        Icon(Icons.Rounded.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(desktopText("${AppIdentity.NAME} is locked"), style = MaterialTheme.typography.headlineSmall)
        Text(desktopText("Enter your data password to continue."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        PasswordField(password, { password = it; failed = false }, desktopText("Data password"), Modifier.fillMaxWidth().focusRequester(focus).onEnter(::submit), isError = failed)
        if (failed) Text(desktopText("The password is incorrect."), color = LifeTheme.colors.danger, style = MaterialTheme.typography.bodySmall)
        Button(onClick = ::submit, enabled = password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(desktopText("Unlock")) }
    }
}

/** Write it down, then burn it for good or seal it on this PC. Never synced or exported. */
@Composable
internal fun ConfessionalPage(store: DesktopConfessionStore, verifyPassword: (CharArray) -> Boolean) {
    val scope = rememberSafeCoroutineScope()
    val sealed by store.entries.collectAsDesktopState()
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var askPassword by remember { mutableStateOf(false) }
    var confirmBurnAll by remember { mutableStateOf(false) }
    var confirmBurn by remember { mutableStateOf<String?>(null) }
    val burn = remember { Animatable(0f) }
    val burning = burn.isRunning

    LaunchedEffect(store) { store.load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            PageHeader(
                "Confessional",
                desktopText(
                    "Say what you need to say. Burn it to let it go for good, or seal it on this PC. " +
                        DesktopPlatform.text("Sealed words are protected by Windows and never exported or synced.", "Sealed words are protected on this computer and never exported or synced."),
                ),
            )
            Column(Modifier.padding(horizontal = PagePadding), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                Box(contentAlignment = Alignment.Center) {
                    val progress = burn.value
                    LifeTextField(
                        value = text,
                        onValueChange = { if (it.length <= MAX_CONFESSION_LENGTH) text = it },
                        enabled = !burning,
                        minLines = 10,
                        placeholder = desktopText("Write it down…"),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = lerp(MaterialTheme.colorScheme.onSurface, EmberColor, progress)),
                        background = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            alpha = 1f - progress
                            scaleY = 1f - progress * 0.35f
                            translationY = -progress * 80f
                        },
                    )
                    if (burning) {
                        Icon(
                            Icons.Rounded.LocalFireDepartment,
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
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
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
                        Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(18.dp))
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
                        Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp))
                        Text(desktopText("Seal"), Modifier.padding(start = 8.dp))
                    }
                    message?.let { Text(desktopText(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
                }

                val sealedEntries = sealed.orEmpty()
                if (sealedEntries.isNotEmpty()) {
                    SectionLabel(desktopText("Sealed confessions"), count = sealedEntries.size) {
                        if (revealed) {
                            TextButton({ revealed = false }) { Text(desktopText("Hide")) }
                            TextButton({ confirmBurnAll = true }) { Text(desktopText("Burn all"), color = LifeTheme.colors.danger) }
                        } else {
                            TextButton({ askPassword = true }) { Text(desktopText("Open")) }
                        }
                    }
                    if (revealed) {
                        val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                        sealedEntries.forEach { entry ->
                            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(start = Space.xl, end = Space.sm, top = Space.sm, bottom = Space.lg)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(format.format(Date(entry.createdAt)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    IconButton({ confirmBurn = entry.id }) { Icon(Icons.Rounded.LocalFireDepartment, desktopText("Burn"), tint = EmberColor) }
                                }
                                Text(entry.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = Space.md))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(48.dp))
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
    confirmBurn?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmBurn = null },
            title = { Text(desktopText("Burn this confession?")) },
            text = { Text(desktopText("It will be permanently deleted from this PC.")) },
            confirmButton = { Button({ confirmBurn = null; scope.launch { store.burn(id) } }) { Text(desktopText("Burn")) } },
            dismissButton = { TextButton({ confirmBurn = null }) { Text(desktopText("Cancel")) } },
        )
    }
    if (confirmBurnAll) {
        AlertDialog(
            onDismissRequest = { confirmBurnAll = false },
            title = { Text(desktopText("Burn every sealed confession?")) },
            text = { Text(desktopText("They will be permanently deleted from this PC.")) },
            confirmButton = {
                Button({
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
                LifeTextField(clientId, { clientId = it.trim() }, placeholder = desktopText("GitHub Client ID"), modifier = Modifier.fillMaxWidth())
                LifeTextField(repository, { repository = it }, placeholder = desktopText("Private repository (owner/name, optional)"), modifier = Modifier.fillMaxWidth())
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
            Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(desktopText("GitHub opened in your browser and this code is already copied. Paste it there, then choose Authorize. This window continues by itself."))
                SelectionContainer {
                    Text(
                        userCode,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = Space.xl, vertical = Space.md),
                    )
                }
                Text(verificationUri, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
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
