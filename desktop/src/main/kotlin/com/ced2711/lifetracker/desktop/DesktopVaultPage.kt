package com.ced2711.lifetracker.desktop

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CLIPBOARD_CLEAR_MILLIS = 30_000L

private sealed interface VaultEditing {
    data object New : VaultEditing
    data class Existing(val id: String) : VaultEditing
}

/**
 * The Vault: it opens only after the data password is entered again (the phone asks for the
 * fingerprint), lists entries on the left and shows the chosen one on the right with copy
 * buttons. Copied values are cleared from the clipboard after 30 seconds.
 */
@Composable
internal fun VaultPage(snapshot: BackupSnapshot, store: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val snackbar = remember { SnackbarHostState() }
    var unlocked by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<VaultEditing?>(null) }
    var deleting by remember { mutableStateOf<VaultEntry?>(null) }

    if (!unlocked) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(desktopText("Vault"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(desktopText("Enter your data password to open the Vault."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DataPasswordDialog(
            title = "Open Vault",
            message = "Enter your data password to open the Vault.",
            verify = store::verifyPassword,
            onVerified = { unlocked = true },
            onDismiss = {},
        )
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

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
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
                Column((if (beside) Modifier.width(380.dp) else Modifier.fillMaxWidth()).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(desktopText("Vault"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(desktopText("Encrypted inside the local .tlb file"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { unlocked = false }) { Icon(Icons.Default.Lock, desktopText("Lock")) }
                        Button(onClick = { editing = VaultEditing.New }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(desktopText("New")) }
                    }
                    OutlinedTextField(query, { query = it }, placeholder = { Text(desktopText("Search")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (entries.isEmpty()) item { Text(desktopText("No Vault entries yet."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                        items(entries, key = { it.id }) { entry ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (entry.id == selectedId) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                modifier = Modifier.fillMaxWidth().clickable { selectedId = entry.id; editing = null },
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(entry.label.ifBlank { desktopText("Untitled") }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val detail = listOf(entry.account, entry.website).filter(String::isNotBlank).joinToString("  ·  ")
                                    if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                if (beside) {
                    VerticalDivider()
                    if (detailOpen) {
                        VaultDetail(snapshot, store, current, selected, ::copy, onEdit = { editing = it }, onDelete = { deleting = it }, onClose = { editing = null; selectedId = null }, onSaved = { selectedId = it; editing = null }, modifier = Modifier.weight(1f).fillMaxHeight())
                    } else {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
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

    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                desktopText(when { editing == VaultEditing.New -> "New Vault entry"; editing != null -> "Edit Vault entry"; else -> entry?.label?.ifBlank { "Untitled" } ?: "" }),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, desktopText("Close (Esc)")) }
        }
        if (editing == null && entry != null) {
            // Reading view: each value with a copy button.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                VaultField("Account", entry.account) { copy(entry.account, "Account") }
                VaultField(
                    "Password", if (showPassword) entry.password else "•".repeat(entry.password.length.coerceIn(0, 16)), monospace = true,
                    extra = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, desktopText("Show password")) } },
                ) { copy(entry.password, "Password") }
                VaultField("Website", entry.website) { copy(entry.website, "Website") }
                if (entry.notes.isNotBlank()) {
                    Text(desktopText("Notes"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(entry.notes)
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onDelete(entry) }) { Text(desktopText("Delete"), color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onEdit(VaultEditing.Existing(entry.id)) }) { Text(desktopText("Edit")) }
            }
            return@Column
        }
        val canSave = listOf(label, account, password, website, notes).any(String::isNotBlank)
        fun save() {
            if (!canSave) return
            scope.launch {
                store.upsertVault(entry?.id, label, account, password, website, notes)
                onSaved(entry?.id ?: store.currentSnapshot()?.vaultEntries?.maxByOrNull { it.createdAt }?.id)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp).editorKeys(onSave = ::save, onCancel = { if (entry != null) onSaved(entry.id) else onClose() }),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(label, { label = it }, label = { Text(desktopText("Label")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(account, { account = it }, label = { Text(desktopText("Account")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                password, { password = it }, label = { Text(desktopText("Password")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
            )
            OutlinedTextField(website, { website = it }, label = { Text(desktopText("Website")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text(desktopText("Notes")) }, minLines = 4, modifier = Modifier.fillMaxWidth())
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { if (entry != null) onSaved(entry.id) else onClose() }) { Text(desktopText("Cancel")) }
            Button(enabled = canSave, onClick = ::save) { Text(desktopText("Save (Ctrl+S)")) }
        }
    }
}

@Composable
private fun VaultField(label: String, value: String, monospace: Boolean = false, extra: @Composable () -> Unit = {}, onCopy: () -> Unit) {
    if (value.isBlank()) return
    Column {
        Text(desktopText(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontFamily = if (monospace) FontFamily.Monospace else null)
            extra()
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, desktopText("Copy")) }
        }
    }
}
