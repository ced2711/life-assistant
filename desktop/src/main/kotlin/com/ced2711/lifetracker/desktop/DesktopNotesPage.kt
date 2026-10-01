package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.NoteFolderEntity
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which notes the middle column lists. */
private sealed interface NoteScope {
    data object All : NoteScope
    data object Pinned : NoteScope
    data object Unfiled : NoteScope
    data class Folder(val id: Long) : NoteScope
}

/** The note being edited. [session] restarts the editor; [id] is filled in once a new note is saved. */
private data class NoteEditing(val session: String, val id: Long?)

/**
 * Notes for a large screen: folders on the left, the notes of the chosen folder in the middle and
 * the open note on the right. Edits save themselves a moment after typing stops (Ctrl+S saves at
 * once), so there is no save dialog.
 */
@Composable
internal fun NotesPage(snapshot: BackupSnapshot, store: DesktopDataStore, openVault: () -> Unit) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    var noteScope by remember { mutableStateOf<NoteScope>(NoteScope.All) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<NoteEditing?>(null) }
    var newFolderParent by remember { mutableStateOf<Long?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<NoteFolderEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<NoteFolderEntity?>(null) }
    var deletingNote by remember { mutableStateOf<NoteEntity?>(null) }
    val searchFocus = remember { FocusRequester() }

    fun newNote() {
        editing = NoteEditing(UUID.randomUUID().toString(), null)
    }
    RegisterPageShortcuts(onNew = ::newNote, onFind = { runCatching { searchFocus.requestFocus() } })

    val folderIds = when (val current = noteScope) {
        is NoteScope.Folder -> descendantFolderIds(snapshot.noteFolders, current.id)
        else -> null
    }
    val notes = snapshot.notes
        .filter { note ->
            when (noteScope) {
                NoteScope.All -> true
                NoteScope.Pinned -> note.pinned
                NoteScope.Unfiled -> note.folderId == null
                is NoteScope.Folder -> note.folderId in folderIds.orEmpty()
            }
        }
        .filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) }
        .sortedWith(compareByDescending<NoteEntity> { it.pinned }.thenByDescending { it.updatedAt })
    val defaultFolder = (noteScope as? NoteScope.Folder)?.id

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val current = editing
        val showFolders = maxWidth >= (if (current != null) 1_100 else 760).dp
        val editorBeside = maxWidth >= 860.dp
        if (current != null && !editorBeside) {
            NoteEditor(snapshot, store, current, defaultFolder, onSaved = { id -> editing = current.copy(id = id) }, onClose = { editing = null }, onDelete = { deletingNote = it }, modifier = Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        Row(Modifier.fillMaxSize()) {
            if (showFolders) {
                NoteFolderColumn(
                    snapshot = snapshot,
                    selected = noteScope,
                    onSelect = { noteScope = it },
                    onNewFolder = { parent -> newFolderParent = parent; creatingFolder = true },
                    onRename = { renamingFolder = it },
                    onDelete = { deletingFolder = it },
                    onOpenVault = openVault,
                    modifier = Modifier.width(232.dp).fillMaxHeight(),
                )
                VerticalDivider()
            }
            Column((if (current != null) Modifier.width(360.dp) else Modifier.weight(1f)).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(noteScopeTitle(noteScope, snapshot), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(desktopNotesCount(notes.size, language), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = ::newNote) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(desktopText("New note")) }
                }
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text(desktopText("Search (Ctrl+F)")) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(searchFocus),
                )
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        .listKeys(
                            onUp = { notes.moveFrom(current?.id, -1)?.let { editing = NoteEditing(UUID.randomUUID().toString(), it.id) } },
                            onDown = { notes.moveFrom(current?.id, 1)?.let { editing = NoteEditing(UUID.randomUUID().toString(), it.id) } },
                            onOpen = {},
                            onDelete = { notes.firstOrNull { it.id == current?.id }?.let { deletingNote = it } },
                        )
                        .focusable(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (notes.isEmpty()) item {
                        Text(desktopText(if (query.isBlank()) "No notes here yet. Press Ctrl+N to write one." else "No notes match the search."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    }
                    items(notes, key = { it.id }) { note ->
                        NoteListRow(note, snapshot, selected = note.id == current?.id, showFolder = noteScope !is NoteScope.Folder) {
                            if (note.id != current?.id) editing = NoteEditing(UUID.randomUUID().toString(), note.id)
                        }
                    }
                }
            }
            if (current != null) {
                VerticalDivider()
                NoteEditor(
                    snapshot, store, current, defaultFolder,
                    onSaved = { id -> if (editing?.session == current.session) editing = current.copy(id = id) },
                    onClose = { editing = null },
                    onDelete = { deletingNote = it },
                    modifier = Modifier.weight(1.4f).fillMaxHeight(),
                )
            }
        }
    }

    if (creatingFolder) SimpleNameDialog(desktopText("New folder"), { creatingFolder = false }) { name ->
        scope.launch { store.addNoteFolder(name, newFolderParent) }
        creatingFolder = false
    }
    renamingFolder?.let { folder ->
        SimpleNameDialog(desktopText("Rename folder"), { renamingFolder = null }, initial = folder.name) { name ->
            scope.launch { store.renameNoteFolder(folder.id, name) }
            renamingFolder = null
        }
    }
    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text(desktopText("Delete folder")) },
            text = { Text(desktopText("Notes stay safe; the folder is removed and child folders move up one level.")) },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text(desktopText("Cancel")) } },
            confirmButton = {
                Button(onClick = {
                    scope.launch { store.deleteNoteFolder(folder.id) }
                    if (noteScope == NoteScope.Folder(folder.id)) noteScope = NoteScope.All
                    deletingFolder = null
                }) { Text(desktopText("Delete")) }
            },
        )
    }
    deletingNote?.let { note ->
        AlertDialog(
            onDismissRequest = { deletingNote = null },
            title = { Text(desktopText("Delete this note?")) },
            text = { Text(note.title) },
            dismissButton = { TextButton(onClick = { deletingNote = null }) { Text(desktopText("Cancel")) } },
            confirmButton = {
                Button(onClick = {
                    scope.launch { store.deleteNote(note.id) }
                    if (editing?.id == note.id) editing = null
                    deletingNote = null
                }) { Text(desktopText("Delete")) }
            },
        )
    }
}

private fun List<NoteEntity>.moveFrom(currentId: Long?, delta: Int): NoteEntity? {
    if (isEmpty()) return null
    val index = indexOfFirst { it.id == currentId }
    return this[(if (index < 0) 0 else index + delta).coerceIn(0, lastIndex)]
}

private fun descendantFolderIds(folders: List<NoteFolderEntity>, root: Long): Set<Long> {
    val children = folders.groupBy { it.parentId }
    val result = mutableSetOf(root)
    val pending = ArrayDeque(listOf(root))
    while (pending.isNotEmpty()) {
        children[pending.removeFirst()].orEmpty().forEach { if (result.add(it.id)) pending.addLast(it.id) }
    }
    return result
}

@Composable
private fun noteScopeTitle(scope: NoteScope, snapshot: BackupSnapshot): String = when (scope) {
    NoteScope.All -> desktopText("All notes")
    NoteScope.Pinned -> desktopText("Pinned")
    NoteScope.Unfiled -> desktopText("Unfiled")
    is NoteScope.Folder -> snapshot.noteFolders.firstOrNull { it.id == scope.id }?.name ?: desktopText("Notes")
}

@Composable
private fun NoteFolderColumn(
    snapshot: BackupSnapshot,
    selected: NoteScope,
    onSelect: (NoteScope) -> Unit,
    onNewFolder: (Long?) -> Unit,
    onRename: (NoteFolderEntity) -> Unit,
    onDelete: (NoteFolderEntity) -> Unit,
    onOpenVault: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FolderEntry(desktopText("All notes"), snapshot.notes.size, selected == NoteScope.All) { onSelect(NoteScope.All) }
        FolderEntry(desktopText("Pinned"), snapshot.notes.count { it.pinned }, selected == NoteScope.Pinned) { onSelect(NoteScope.Pinned) }
        FolderEntry(desktopText("Unfiled"), snapshot.notes.count { it.folderId == null }, selected == NoteScope.Unfiled) { onSelect(NoteScope.Unfiled) }
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(desktopText("Folders"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = { onNewFolder((selected as? NoteScope.Folder)?.id) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, desktopText("New folder"), Modifier.size(18.dp))
            }
        }
        val children = snapshot.noteFolders.groupBy { it.parentId }
        val tree = mutableListOf<Pair<NoteFolderEntity, Int>>()
        fun walk(parent: Long?, depth: Int) {
            children[parent].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() })).forEach {
                tree += it to depth
                walk(it.id, depth + 1)
            }
        }
        walk(null, 0)
        tree.forEach { (folder, depth) ->
            val count = snapshot.notes.count { it.folderId in descendantFolderIds(snapshot.noteFolders, folder.id) }
            FolderEntry(
                folder.name, count, selected == NoteScope.Folder(folder.id), indent = depth,
                onRename = { onRename(folder) }, onDelete = { onDelete(folder) },
            ) { onSelect(NoteScope.Folder(folder.id)) }
        }
        if (tree.isEmpty()) Text(desktopText("No folders yet."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun FolderEntry(
    label: String,
    count: Int,
    selected: Boolean,
    indent: Int = 0,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().hoverable(interaction).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = 10.dp + (indent * 14).dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, modifier = Modifier.weight(1f).padding(vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            if (hovered && onRename != null) {
                IconButton(onClick = onRename, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.Edit, desktopText("Rename folder"), Modifier.size(16.dp)) }
                IconButton(onClick = { onDelete?.invoke() }, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.Delete, desktopText("Delete folder"), Modifier.size(16.dp)) }
            } else if (count > 0) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
            }
        }
    }
}

@Composable
private fun NoteListRow(note: NoteEntity, snapshot: BackupSnapshot, selected: Boolean, showFolder: Boolean, onClick: () -> Unit) {
    val language = LocalUiLanguage.current
    val updated = Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val dateText = if (updated == LocalDate.now()) desktopText("Today") else UserFormatting.formatDate(updated, snapshot.settings.dateFormat, uiLocale(language))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.pinned) Icon(Icons.Default.PushPin, desktopText("Pinned"), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                if (note.pinned) Spacer(Modifier.width(4.dp))
                Text(note.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(dateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val preview = note.body.lineSequence().map(String::trim).filter(String::isNotEmpty).joinToString("  ")
            if (preview.isNotEmpty()) Text(preview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showFolder) note.folderId?.let { Text(noteFolderPath(it, snapshot), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteEditor(
    snapshot: BackupSnapshot,
    store: DesktopDataStore,
    editing: NoteEditing,
    defaultFolder: Long?,
    onSaved: (Long) -> Unit,
    onClose: () -> Unit,
    onDelete: (NoteEntity) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberSafeCoroutineScope()
    val stored = editing.id?.let { id -> snapshot.notes.firstOrNull { it.id == id } }
    // The editor keeps its own text while open; saving never resets what is being typed.
    var title by remember(editing.session) { mutableStateOf(stored?.title.orEmpty()) }
    var body by remember(editing.session) { mutableStateOf(stored?.body.orEmpty()) }
    var pinned by remember(editing.session) { mutableStateOf(stored?.pinned ?: false) }
    var folder by remember(editing.session) { mutableStateOf(stored?.folderId ?: defaultFolder) }
    var savedId by remember(editing.session) { mutableStateOf(editing.id) }
    var savedState by remember(editing.session) { mutableStateOf(listOf<Any?>(stored?.title, stored?.body, stored?.pinned, stored?.folderId)) }
    val bodyFocus = remember { FocusRequester() }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(editing.session) { runCatching { if (editing.id == null) titleFocus.requestFocus() else bodyFocus.requestFocus() } }

    val current = listOf<Any?>(title, body, pinned, folder)
    val hasContent = title.isNotBlank() || body.isNotBlank()
    val dirty = current != savedState && hasContent

    suspend fun saveNow() {
        if (!(current != savedState && hasContent)) return
        val snapshotOfInput = current
        val id = store.saveNote(savedId, folder, title, body, pinned) ?: return
        savedId = id
        savedState = snapshotOfInput
        onSaved(id)
    }
    // Autosave a moment after typing stops.
    LaunchedEffect(editing.session, title, body, pinned, folder) {
        if (!dirty) return@LaunchedEffect
        delay(700)
        saveNow()
    }

    Column(modifier.background(MaterialTheme.colorScheme.surface).editorKeys(onSave = { scope.launch { saveNow() } }, onCancel = { scope.launch { saveNow(); onClose() } })) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                desktopText(if (dirty) "Saving…" else if (savedId == null) "New note" else "Saved"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { pinned = !pinned }) {
                Icon(if (pinned) Icons.Default.PushPin else Icons.Outlined.PushPin, desktopText(if (pinned) "Unpin" else "Pin"), tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            savedId?.let { id -> IconButton({ chooseAndAttach(scope, store, AttachmentOwnerType.NOTE, id) }) { Icon(Icons.Default.AttachFile, desktopText("Attach")) } }
            stored?.let { note -> IconButton(onClick = { onDelete(note) }) { Icon(Icons.Default.Delete, desktopText("Delete")) } }
            IconButton(onClick = { scope.launch { saveNow(); onClose() } }) { Icon(Icons.Default.Close, desktopText("Close (Esc)")) }
        }
        val plain = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        )
        TextField(
            title, { title = it },
            placeholder = { Text(desktopText("Title"), style = MaterialTheme.typography.headlineSmall) },
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            singleLine = true, colors = plain,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).focusRequester(titleFocus).onEnter { runCatching { bodyFocus.requestFocus() } },
        )
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChipSimple(desktopText("Unfiled"), folder == null) { folder = null }
            snapshot.noteFolders.sortedBy { noteFolderPath(it.id, snapshot).lowercase() }.forEach { candidate ->
                FilterChipSimple(noteFolderPath(candidate.id, snapshot), folder == candidate.id) { folder = candidate.id }
            }
        }
        HorizontalDivider(Modifier.padding(top = 10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            TextField(
                body, { body = it },
                placeholder = { Text(desktopText("Start writing…")) },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = plain,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).focusRequester(bodyFocus),
            )
            savedId?.let { id ->
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) { AttachmentList(snapshot, AttachmentOwnerType.NOTE, id, store) }
            }
        }
    }
}
