package com.ced2711.lifetracker.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.NoteFolderEntity
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which notes the list shows. */
private sealed interface NoteScope {
    data object All : NoteScope
    data object Pinned : NoteScope
    data object Unfiled : NoteScope
    data class Folder(val id: Long) : NoteScope
}

/** The open editor: [session] changes whenever another note is opened, [id] is null until saved. */
private data class NoteEditing(val session: String, val id: Long?)

/**
 * Folders, the list of notes and the open note side by side. Notes save themselves as you type;
 * arrow keys step through the list, Ctrl+N starts a note, Ctrl+F searches.
 */
@Composable
internal fun NotesPage(
    snapshot: BackupSnapshot,
    store: DesktopDataStore,
    requestedNoteId: Long? = null,
    onRequestHandled: () -> Unit = {},
    openVault: () -> Unit,
) {
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
    // Opened from elsewhere (Today's pinned notes): show that note.
    LaunchedEffect(requestedNoteId) {
        requestedNoteId?.let { id ->
            if (snapshot.notes.any { it.id == id }) editing = NoteEditing(UUID.randomUUID().toString(), id)
            onRequestHandled()
        }
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
                    modifier = Modifier.width(232.dp),
                )
                ColumnDivider()
            }
            Column((if (current != null) Modifier.width(360.dp) else Modifier.weight(1f)).fillMaxHeight()) {
                val compact = current != null
                Row(
                    Modifier.fillMaxWidth().padding(start = if (compact) Space.xl else PagePadding, end = if (compact) Space.lg else PagePadding, top = 28.dp, bottom = Space.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(noteScopeTitle(noteScope, snapshot), style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(desktopNotesCount(notes.size, language), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!showFolders) {
                        ChoiceMenu(
                            label = desktopText("Show"),
                            options = listOf<NoteScope>(NoteScope.All, NoteScope.Pinned, NoteScope.Unfiled) + snapshot.noteFolders.sortedBy { noteFolderPath(it.id, snapshot).lowercase() }.map { NoteScope.Folder(it.id) },
                            selected = noteScope,
                            optionLabel = { option ->
                                when (option) {
                                    NoteScope.All -> desktopText("All notes", language)
                                    NoteScope.Pinned -> desktopText("Pinned", language)
                                    NoteScope.Unfiled -> desktopText("Unfiled", language)
                                    is NoteScope.Folder -> noteFolderPath(option.id, snapshot)
                                }
                            },
                            onSelect = { noteScope = it },
                        )
                    }
                    Button(onClick = ::newNote) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(desktopText("New note"))
                    }
                }
                LifeTextField(
                    query, { query = it },
                    placeholder = desktopText("Search (Ctrl+F)"),
                    leadingIcon = Icons.Rounded.Search,
                    trailing = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, desktopText("Clear"), Modifier.size(16.dp)) } }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) Space.lg else PagePadding).focusRequester(searchFocus),
                )
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (compact) Space.sm else PagePadding - Space.md, vertical = Space.sm)
                        .listKeys(
                            onUp = { notes.moveFrom(current?.id, -1)?.let { editing = NoteEditing(UUID.randomUUID().toString(), it.id) } },
                            onDown = { notes.moveFrom(current?.id, 1)?.let { editing = NoteEditing(UUID.randomUUID().toString(), it.id) } },
                            onOpen = {},
                            onDelete = { notes.firstOrNull { it.id == current?.id }?.let { deletingNote = it } },
                        )
                        .focusable(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (notes.isEmpty()) item {
                        EmptyState(
                            title = desktopText(if (query.isBlank()) "No notes here yet" else "No notes match"),
                            icon = Icons.AutoMirrored.Rounded.Notes,
                            body = desktopText(if (query.isBlank()) "Press Ctrl+N to write one. Notes save themselves as you type." else "Try other words, or look in All notes."),
                        )
                    }
                    items(notes, key = { it.id }) { note ->
                        NoteListRow(note, snapshot, selected = note.id == current?.id, showFolder = noteScope !is NoteScope.Folder) {
                            if (note.id != current?.id) editing = NoteEditing(UUID.randomUUID().toString(), note.id)
                        }
                    }
                }
            }
            if (current != null) {
                ColumnDivider()
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
    NoteScope.All -> desktopText("Notes")
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
    modifier: Modifier,
) {
    FilterColumn(modifier) {
        FilterEntry(desktopText("All notes"), snapshot.notes.size, selected == NoteScope.All) { onSelect(NoteScope.All) }
        FilterEntry(desktopText("Pinned"), snapshot.notes.count { it.pinned }, selected == NoteScope.Pinned) { onSelect(NoteScope.Pinned) }
        FilterEntry(desktopText("Unfiled"), snapshot.notes.count { it.folderId == null }, selected == NoteScope.Unfiled) { onSelect(NoteScope.Unfiled) }
        FilterHeader("Folders") {
            IconButton(onClick = { onNewFolder((selected as? NoteScope.Folder)?.id) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Add, desktopText("New folder"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            FilterEntry(
                folder.name, count, selected == NoteScope.Folder(folder.id), indent = depth,
                onRename = { onRename(folder) }, onDelete = { onDelete(folder) },
            ) { onSelect(NoteScope.Folder(folder.id)) }
        }
        if (tree.isEmpty()) {
            Text(desktopText("No folders yet."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable
private fun NoteListRow(note: NoteEntity, snapshot: BackupSnapshot, selected: Boolean, showFolder: Boolean, onClick: () -> Unit) {
    val language = LocalUiLanguage.current
    val updated = Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val dateText = if (updated == LocalDate.now()) desktopText("Today") else UserFormatting.formatDate(updated, snapshot.settings.dateFormat, uiLocale(language))
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> LifeTheme.colors.accentSoft
            hovered -> MaterialTheme.colorScheme.surfaceContainer
            else -> Color.Transparent
        },
        tween(120),
        label = "note-row",
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(background).hoverable(interaction).clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (note.pinned) {
                Icon(Icons.Rounded.PushPin, desktopText("Pinned"), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
            }
            Text(note.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(dateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Space.sm))
        }
        val preview = note.body.lineSequence().map(String::trim).filter(String::isNotEmpty).joinToString("  ")
        if (preview.isNotEmpty()) Text(preview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showFolder) note.folderId?.let { Text(noteFolderPath(it, snapshot), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
    }
}

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
    val language = LocalUiLanguage.current
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

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).editorKeys(onSave = { scope.launch { saveNow() } }, onCancel = { scope.launch { saveNow(); onClose() } })) {
        Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChoiceMenu(
                label = desktopText("Folder"),
                options = listOf<Long?>(null) + snapshot.noteFolders.sortedBy { noteFolderPath(it.id, snapshot).lowercase() }.map { it.id },
                selected = folder,
                optionLabel = { id -> id?.let { noteFolderPath(it, snapshot) } ?: desktopText("Unfiled", language) },
                onSelect = { folder = it },
            )
            Spacer(Modifier.weight(1f))
            Text(
                desktopText(if (dirty) "Saving…" else if (savedId == null) "New note" else "Saved"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Space.sm),
            )
            IconButton(onClick = { pinned = !pinned }) {
                Icon(if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, desktopText(if (pinned) "Unpin" else "Pin"), tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = {
                    scope.launch {
                        // A new note is saved first so the file has something to belong to.
                        saveNow()
                        savedId?.let { chooseAndAttach(scope, store, AttachmentOwnerType.NOTE, it) }
                    }
                },
                enabled = hasContent,
            ) { Icon(Icons.Rounded.AttachFile, desktopText("Attach"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            stored?.let { note -> IconButton(onClick = { onDelete(note) }) { Icon(Icons.Rounded.DeleteOutline, desktopText("Delete"), tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
            IconButton(onClick = { scope.launch { saveNow(); onClose() } }) { Icon(Icons.Rounded.Close, desktopText("Close (Esc)"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = Space.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                PlainField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = desktopText("Title"),
                    style = MaterialTheme.typography.headlineMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocus).onEnter { runCatching { bodyFocus.requestFocus() } },
                )
                Spacer(Modifier.height(Space.md))
                PlainField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = desktopText("Start writing…"),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15f),
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().focusRequester(bodyFocus),
                    minHeight = 320,
                )
                savedId?.let { id ->
                    Spacer(Modifier.height(Space.lg))
                    AttachmentList(snapshot, AttachmentOwnerType.NOTE, id, store)
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

/** A borderless text field for writing: just the text on the page. */
@Composable
internal fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
    minHeight: Int = 0,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = minHeight.dp)) {
                if (value.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                inner()
            }
        },
    )
}
