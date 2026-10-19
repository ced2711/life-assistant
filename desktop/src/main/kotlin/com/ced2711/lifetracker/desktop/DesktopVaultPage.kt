package com.ced2711.lifetracker.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CLIPBOARD_CLEAR_MILLIS = 30_000L

/** The Vault locks itself after the window was left for this long. */
private const val VAULT_AWAY_LOCK_MILLIS = 60_000L

private sealed interface VaultEditing {
    data object New : VaultEditing
    data class Existing(val id: String) : VaultEditing
}

/**
 * Accounts and passwords. It opens only after the data password is entered again (the phone
 * asks for the fingerprint), lists entries on the left and shows the chosen one on the right
 * with copy buttons. Copied values leave the clipboard after 30 seconds, and the Vault locks when
 * the page is left or the window was away for a minute.
 */
@Composable
internal fun VaultPage(snapshot: BackupSnapshot, store: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val security = LocalDesktopSecurity.current
    val snackbar = remember { SnackbarHostState() }
    var unlocked by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<VaultEditing?>(null) }
    var deleting by remember { mutableStateOf<VaultEntry?>(null) }

    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused, unlocked) {
        if (!focused && unlocked) {
            delay(VAULT_AWAY_LOCK_MILLIS)
            unlocked = false
            editing = null
            selectedId = null
        }
    }

    if (!unlocked) {
        VaultLocked(chosen = security.passwordChosen(), verify = store::verifyPassword, onUnlocked = { unlocked = true })
        return
    }

    RegisterPageShortcuts(onNew = { editing = VaultEditing.New })

    fun copy(value: String, what: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
        scope.launch { snackbar.showSnackbar(desktopCopiedMessage(desktopText(what, language), language)) }
        scope.launch {
            delay(CLIPBOARD_CLEAR_MILLIS)
            // Only clear what we put there; something copied later is left alone.
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val current = runCatching { clipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull()
            if (current == value) clipboard.setContents(StringSelection(""), null)
        }
    }

    val entries = snapshot.vaultEntries
        .filter { query.isBlank() || listOf(it.label, it.account, it.website, it.notes).any { field -> field.contains(query, true) } }
        .sortedBy { it.label.lowercase() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val beside = maxWidth >= 860.dp
            val current = editing
            val selected = snapshot.vaultEntries.firstOrNull { it.id == selectedId }
            val detailOpen = current != null || selected != null
            if (!beside && detailOpen) {
                VaultDetail(snapshot, store, current, selected, ::copy, onEdit = { editing = it }, onDelete = { deleting = it }, onClose = { editing = null; selectedId = null }, onSaved = { selectedId = it; editing = null }, modifier = Modifier.fillMaxSize())
                return@BoxWithConstraints
            }
            Row(Modifier.fillMaxSize()) {
                Column((if (beside) Modifier.width(400.dp) else Modifier.fillMaxWidth()).fillMaxHeight()) {
                    PageHeader("Vault", desktopText("Kept encrypted on this PC")) {
                        IconButton(onClick = { unlocked = false }) { Icon(Icons.Rounded.Lock, desktopText("Lock"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Button(onClick = { editing = VaultEditing.New }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(desktopText("New"))
                        }
                    }
                    LifeTextField(query, { query = it }, placeholder = desktopText("Search"), leadingIcon = Icons.Rounded.Search, modifier = Modifier.fillMaxWidth().padding(horizontal = PagePadding))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PagePadding - Space.md, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (entries.isEmpty()) item {
                            EmptyState(
                                title = desktopText(if (query.isBlank()) "No Vault entries yet" else "No entries match"),
                                icon = Icons.Rounded.Key,
                                body = desktopText(if (query.isBlank()) "Press Ctrl+N to store an account and its password." else "Try other words."),
                            )
                        }
                        items(entries, key = { it.id }) { entry ->
                            val interaction = remember { MutableInteractionSource() }
                            val hovered by interaction.collectIsHoveredAsState()
                            val background by animateColorAsState(
                                when {
                                    entry.id == selectedId -> LifeTheme.colors.accentSoft
                                    hovered -> MaterialTheme.colorScheme.surfaceContainer
                                    else -> Color.Transparent
                                },
                                tween(120),
                                label = "vault-row",
                            )
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(background).hoverable(interaction)
                                    .clickable { selectedId = entry.id; editing = null }.padding(horizontal = Space.md, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(entry.label.ifBlank { desktopText("Untitled") }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val detail = listOf(entry.account, entry.website).filter(String::isNotBlank).joinToString("  ·  ")
                                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (beside) {
                    ColumnDivider()
                    if (detailOpen) {
                        VaultDetail(snapshot, store, current, selected, ::copy, onEdit = { editing = it }, onDelete = { deleting = it }, onClose = { editing = null; selectedId = null }, onSaved = { selectedId = it; editing = null }, modifier = Modifier.weight(1f).fillMaxHeight())
                    } else {
                        Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
                            Text(desktopText("Choose an entry, or press Ctrl+N for a new one."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(desktopText("Delete this Vault entry?")) },
            text = { Text(entry.label.ifBlank { entry.account }) },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(desktopText("Cancel")) } },
            confirmButton = {
                Button(onClick = {
                    scope.launch { store.deleteVault(entry.id) }
                    if (selectedId == entry.id) selectedId = null
                    editing = null
                    deleting = null
                }) { Text(desktopText("Delete")) }
            },
        )
    }
}

/** The closed Vault: type the password here, or choose one first on a PC that has none yet. */
@Composable
private fun VaultLocked(chosen: Boolean, verify: (CharArray) -> Boolean, onUnlocked: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(chosen) { if (chosen) runCatching { focus.requestFocus() } }
    fun submit() {
        if (password.isEmpty()) return
        val candidate = password.toCharArray()
        password = ""
        if (verify(candidate)) onUnlocked() else failed = true
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Panel(Modifier.widthIn(max = 420.dp).padding(Space.xxl), padding = PaddingValues(32.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Text(desktopText("Vault"), style = MaterialTheme.typography.headlineSmall)
                if (chosen) {
                    Text(desktopText("Enter your data password to open the Vault."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PasswordField(password, { password = it; failed = false }, desktopText("Data password"), Modifier.fillMaxWidth().focusRequester(focus).onEnter(::submit), isError = failed)
                    if (failed) Text(desktopText("The password is incorrect."), color = LifeTheme.colors.danger, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = ::submit, enabled = password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(desktopText("Unlock")) }
                } else {
                    Text(desktopText("Accounts and passwords, kept encrypted. Choose a password to protect them; it is asked each time the Vault opens."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) { Text(desktopText("Choose a password")) }
                }
            }
        }
    }
    if (choosing) {
        ChoosePasswordDialog(
            title = "Choose a password",
            message = "It protects Vault, sealed confessions and the app lock, and encrypts cloud sync. Use the same one on your other devices.",
            onChosen = { choosing = false; onUnlocked() },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun VaultDetail(
    snapshot: BackupSnapshot,
    store: DesktopDataStore,
    editing: VaultEditing?,
    selected: VaultEntry?,
    copy: (String, String) -> Unit,
    onEdit: (VaultEditing) -> Unit,
    onDelete: (VaultEntry) -> Unit,
    onClose: () -> Unit,
    onSaved: (String?) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberSafeCoroutineScope()
    val entry = when (editing) {
        is VaultEditing.Existing -> snapshot.vaultEntries.firstOrNull { it.id == editing.id }
        VaultEditing.New -> null
        null -> selected
    }
    val key = (editing to entry?.id).toString()
    var label by remember(key) { mutableStateOf(entry?.label.orEmpty()) }
    var account by remember(key) { mutableStateOf(entry?.account.orEmpty()) }
    var password by remember(key) { mutableStateOf(entry?.password.orEmpty()) }
    var website by remember(key) { mutableStateOf(entry?.website.orEmpty()) }
    var notes by remember(key) { mutableStateOf(entry?.notes.orEmpty()) }
    var showPassword by remember(key) { mutableStateOf(false) }

    if (editing == null && entry != null) {
        // Reading view: each value with a copy button.
        EditorPane(
            title = entry.label.ifBlank { desktopText("Untitled") },
            onClose = onClose,
            modifier = modifier,
            footer = {
                TextButton(onClick = { onDelete(entry) }) { Text(desktopText("Delete"), color = LifeTheme.colors.danger) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onEdit(VaultEditing.Existing(entry.id)) }) { Text(desktopText("Edit")) }
            },
        ) {
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                VaultField("Account", entry.account) { copy(entry.account, "Account") }
                VaultField(
                    "Password",
                    if (showPassword) entry.password else "•".repeat(entry.password.length.coerceIn(0, 16)),
                    monospace = true,
                    extra = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, desktopText(if (showPassword) "Hide password" else "Show password"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                ) { copy(entry.password, "Password") }
                VaultField("Website", entry.website) { copy(entry.website, "Website") }
                if (entry.notes.isNotBlank()) {
                    FieldLabel("Notes")
                    Text(entry.notes, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        return
    }
    val canSave = listOf(label, account, password, website, notes).any(String::isNotBlank)
    fun cancel() = if (entry != null) onSaved(entry.id) else onClose()
    fun save() {
        if (!canSave) return
        scope.launch {
            store.upsertVault(entry?.id, label, account, password, website, notes)
            onSaved(entry?.id ?: store.currentSnapshot()?.vaultEntries?.maxByOrNull { it.createdAt }?.id)
        }
    }
    EditorPane(
        title = desktopText(if (editing == VaultEditing.New) "New Vault entry" else "Edit Vault entry"),
        onClose = ::cancel,
        modifier = modifier.editorKeys(onSave = ::save, onCancel = ::cancel),
        footer = {
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = ::cancel) { Text(desktopText("Cancel")) }
            Button(enabled = canSave, onClick = ::save) { Text(desktopText("Save (Ctrl+S)")) }
        },
    ) {
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            FieldLabel("Label")
            LifeTextField(label, { label = it }, placeholder = desktopText("e.g. Bank, Email"), modifier = Modifier.fillMaxWidth())
            FieldLabel("Account")
            LifeTextField(account, { account = it }, placeholder = desktopText("User name or email"), modifier = Modifier.fillMaxWidth())
            FieldLabel("Password")
            PasswordField(password, { password = it }, desktopText("Password"), Modifier.fillMaxWidth())
            FieldLabel("Website")
            LifeTextField(website, { website = it }, placeholder = "https://", modifier = Modifier.fillMaxWidth())
            FieldLabel("Notes")
            LifeTextField(notes, { notes = it }, placeholder = desktopText("Optional"), minLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun VaultField(label: String, value: String, monospace: Boolean = false, extra: @Composable () -> Unit = {}, onCopy: () -> Unit) {
    if (value.isBlank()) return
    Column {
        FieldLabel(label)
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = Space.lg, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, modifier = Modifier.weight(1f).padding(vertical = Space.md), style = MaterialTheme.typography.bodyLarge, fontFamily = if (monospace) FontFamily.Monospace else null, maxLines = 2, overflow = TextOverflow.Ellipsis)
            extra()
            IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, desktopText("Copy"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
