package com.ced2711.lifetracker.ui.notes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.localization.uiLocale
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.NoteFolderEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.NoteDraft
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.attachment.rememberAttachmentOpener
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val ALL_FOLDERS = "all"
private const val ROOT_FOLDER = "root"

@Composable
fun NotesScreen(
    viewModel: TaskLedgerViewModel,
    onOpenVault: () -> Unit,
    modifier: Modifier = Modifier,
    isWide: Boolean,
) {
    val folders by viewModel.noteFolders.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var folderSelection by rememberSaveable { mutableStateOf(ALL_FOLDERS) }
    var selectedNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creatingNote by rememberSaveable { mutableStateOf(false) }
    var newDraftNonce by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var attachmentTargetId by remember { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiLanguage = LocalUiLanguage.current

    val selectedFolderId = folderSelection.toLongOrNull()
    val visibleNotes = remember(notes, folderSelection, query) {
        notes.filter { note ->
            val folderMatches = when (folderSelection) {
                ALL_FOLDERS -> true
                ROOT_FOLDER -> note.folderId == null
                else -> note.folderId == selectedFolderId
            }
            val queryMatches = query.isBlank() ||
                note.title.contains(query, ignoreCase = true) ||
                note.body.contains(query, ignoreCase = true)
            folderMatches && queryMatches
        }
    }
    val selectedNote = notes.firstOrNull { it.id == selectedNoteId }

    LaunchedEffect(selectedNoteId, notes) {
        if (selectedNoteId != null && selectedNote == null && !creatingNote) selectedNoteId = null
    }

    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        val noteId = attachmentTargetId
        attachmentTargetId = null
        if (noteId != null && uris.isNotEmpty()) {
            viewModel.addAttachments(
                ownerType = AttachmentOwnerType.NOTE,
                ownerId = noteId,
                uris = uris,
                copyAttemptId = UUID.randomUUID().toString(),
                onCopyFailed = { message ->
                    scope.launch { snackbar.showSnackbar(translateUiText(message, uiLanguage)) }
                },
            )
        }
    }

    fun createNote() {
        selectedNoteId = null
        creatingNote = true
        newDraftNonce += 1
    }

    fun requestAttachments(draft: NoteDraft) {
        if (draft.id != null) {
            attachmentTargetId = draft.id
            attachmentLauncher.launch(arrayOf("*/*"))
        } else {
            viewModel.saveNote(
                draft = draft,
                onSaved = { noteId ->
                    creatingNote = false
                    selectedNoteId = noteId
                    attachmentTargetId = noteId
                    attachmentLauncher.launch(arrayOf("*/*"))
                },
                onFailure = { message ->
                    scope.launch { snackbar.showSnackbar(translateUiText(message, uiLanguage)) }
                },
            )
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (isWide) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                NotesBrowser(
                    folders = folders,
                    notes = visibleNotes,
                    folderSelection = folderSelection,
                    selectedNoteId = selectedNoteId,
                    query = query,
                    onQueryChanged = { query = it },
                    onFolderSelected = { folderSelection = it },
                    onNoteSelected = { selectedNoteId = it; creatingNote = false },
                    onCreateNote = ::createNote,
                    onOpenVault = onOpenVault,
                    viewModel = viewModel,
                    snackbar = snackbar,
                    modifier = Modifier.widthIn(min = 300.dp, max = 380.dp).fillMaxHeight(),
                )
                VerticalDivider(Modifier.fillMaxHeight())
                NoteEditorPane(
                    note = selectedNote,
                    newDraftNonce = newDraftNonce,
                    creatingNote = creatingNote,
                    folders = folders,
                    initialFolderId = selectedFolderId,
                    viewModel = viewModel,
                    snackbar = snackbar,
                    onSaved = { noteId -> creatingNote = false; selectedNoteId = noteId },
                    onDeleted = { creatingNote = false; selectedNoteId = null },
                    onRequestAttachments = ::requestAttachments,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else if (creatingNote || selectedNote != null) {
            NoteEditorPane(
                note = selectedNote,
                newDraftNonce = newDraftNonce,
                creatingNote = creatingNote,
                folders = folders,
                initialFolderId = selectedFolderId,
                viewModel = viewModel,
                snackbar = snackbar,
                onSaved = { noteId -> creatingNote = false; selectedNoteId = noteId },
                onDeleted = { creatingNote = false; selectedNoteId = null },
                onBack = { creatingNote = false; selectedNoteId = null },
                onRequestAttachments = ::requestAttachments,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            NotesBrowser(
                folders = folders,
                notes = visibleNotes,
                folderSelection = folderSelection,
                selectedNoteId = selectedNoteId,
                query = query,
                onQueryChanged = { query = it },
                onFolderSelected = { folderSelection = it },
                onNoteSelected = { selectedNoteId = it },
                onCreateNote = ::createNote,
                onOpenVault = onOpenVault,
                viewModel = viewModel,
                snackbar = snackbar,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun NotesBrowser(
    folders: List<NoteFolderEntity>,
    notes: List<NoteEntity>,
    folderSelection: String,
    selectedNoteId: Long?,
    query: String,
    onQueryChanged: (String) -> Unit,
    onFolderSelected: (String) -> Unit,
    onNoteSelected: (Long) -> Unit,
    onCreateNote: () -> Unit,
    onOpenVault: () -> Unit,
    viewModel: TaskLedgerViewModel,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val uiLanguage = LocalUiLanguage.current
    var folderDialogMode by remember { mutableStateOf<FolderDialogMode?>(null) }
    var confirmDeleteFolder by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var foldersExpanded by rememberSaveable { mutableStateOf(false) }
    val selectedFolder = folders.firstOrNull { it.id.toString() == folderSelection }
    val selectedFolderLabel = when (folderSelection) {
        ALL_FOLDERS -> localizedText("All notes")
        ROOT_FOLDER -> localizedText("Unfiled")
        else -> selectedFolder?.name ?: localizedText("All notes")
    }

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(localizedText("Notes"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    localizedText("Long-term writing and private files"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenVault) {
                Icon(Icons.Outlined.Lock, contentDescription = localizedText("Open password vault"))
            }
            IconButton(onClick = { folderDialogMode = FolderDialogMode.Create }) {
                Icon(Icons.Outlined.CreateNewFolder, contentDescription = localizedText("New folder"))
            }
        }
        NotesBrowserSectionHeader(
            label = if (query.isBlank()) "Search" else "Search · $query",
            icon = Icons.Outlined.Search,
            expanded = searchExpanded,
            onClick = { searchExpanded = !searchExpanded },
        )
        AnimatedVisibility(visible = searchExpanded) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = localizedText("Clear search"))
                        }
                    }
                } else null,
                placeholder = { Text(localizedText("Search notes")) },
            )
        }
        NotesBrowserSectionHeader(
            label = "Folders · $selectedFolderLabel",
            icon = if (foldersExpanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
            expanded = foldersExpanded,
            onClick = { foldersExpanded = !foldersExpanded },
        )
        AnimatedVisibility(visible = foldersExpanded) {
            Column {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    FolderRow(localizedText("All notes"), Icons.Outlined.FolderOpen, folderSelection == ALL_FOLDERS) {
                        onFolderSelected(ALL_FOLDERS)
                    }
                    FolderRow(localizedText("Unfiled"), Icons.Outlined.Folder, folderSelection == ROOT_FOLDER) {
                        onFolderSelected(ROOT_FOLDER)
                    }
                    flattenNoteFolders(folders).forEach { row ->
                        FolderRow(
                            label = row.folder.name,
                            icon = Icons.Outlined.Folder,
                            selected = folderSelection == row.folder.id.toString(),
                            depth = row.depth,
                        ) { onFolderSelected(row.folder.id.toString()) }
                    }
                }
                if (selectedFolder != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { folderDialogMode = FolderDialogMode.Rename }) {
                            Icon(Icons.Outlined.Edit, null)
                            Spacer(Modifier.width(4.dp))
                            Text(localizedText("Rename"))
                        }
                        TextButton(onClick = { confirmDeleteFolder = true }) {
                            Icon(Icons.Outlined.Delete, null)
                            Spacer(Modifier.width(4.dp))
                            Text(localizedText("Delete folder"))
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(localizedText("${notes.size} notes"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = onCreateNote) {
                Icon(Icons.Outlined.Add, null)
                Text(localizedText("New"))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (notes.isEmpty()) {
                Text(
                    localizedText(if (query.isBlank()) "No notes here yet." else "No matching notes."),
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            notes.forEach { note ->
                NoteListItem(note, selected = note.id == selectedNoteId) { onNoteSelected(note.id) }
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    folderDialogMode?.let { mode ->
        FolderNameDialog(
            title = if (mode == FolderDialogMode.Create) "New folder" else "Rename folder",
            initialName = if (mode == FolderDialogMode.Rename) selectedFolder?.name.orEmpty() else "",
            parentName = if (mode == FolderDialogMode.Create) selectedFolder?.name else null,
            onDismiss = { folderDialogMode = null },
            onConfirm = { name ->
                if (mode == FolderDialogMode.Create) {
                    viewModel.addNoteFolder(
                        name = name,
                        parentId = selectedFolder?.id,
                        onSaved = { id -> folderDialogMode = null; onFolderSelected(id.toString()) },
                        onFailure = { message ->
                            scope.launch { snackbar.showSnackbar(translateUiText(message, uiLanguage)) }
                        },
                    )
                } else if (selectedFolder != null) {
                    viewModel.renameNoteFolder(
                        folderId = selectedFolder.id,
                        name = name,
                        onSaved = { folderDialogMode = null },
                        onFailure = { message ->
                            scope.launch { snackbar.showSnackbar(translateUiText(message, uiLanguage)) }
                        },
                    )
                }
            },
        )
    }
    if (confirmDeleteFolder && selectedFolder != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteFolder = false },
            title = { Text(localizedText("Delete ${selectedFolder.name}?")) },
            text = { Text(localizedText("Notes will move to Unfiled. Child folders will move up one level.")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNoteFolder(
                        selectedFolder.id,
                        onFailure = { message ->
                            scope.launch { snackbar.showSnackbar(translateUiText(message, uiLanguage)) }
                        },
                    )
                    confirmDeleteFolder = false
                    onFolderSelected(ALL_FOLDERS)
                }) { Text(localizedText("Delete")) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteFolder = false }) { Text(localizedText("Cancel")) } },
        )
    }
}

@Composable
private fun NotesBrowserSectionHeader(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null)
            Spacer(Modifier.width(8.dp))
            Text(
                localizedText(label),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = localizedText(if (expanded) "Collapse" else "Expand"),
            )
        }
    }
}

@Composable
private fun FolderRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    depth: Int = 0,
    onClick: () -> Unit,
) {
    val colors = if (selected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    Card(
        onClick = onClick,
        colors = colors,
        modifier = Modifier.fillMaxWidth().padding(start = (depth * 18).dp, bottom = 2.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(8.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NoteListItem(note: NoteEntity, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.pinned) {
                    Icon(Icons.Outlined.PushPin, null, Modifier.padding(end = 6.dp))
                }
                Text(note.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (note.body.isNotBlank()) {
                Text(
                    note.body.replace('\n', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                    uiLocale(LocalUiLanguage.current),
                ).format(Date(note.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun NoteEditorPane(
    note: NoteEntity?,
    newDraftNonce: Int,
    creatingNote: Boolean,
    folders: List<NoteFolderEntity>,
    initialFolderId: Long?,
    viewModel: TaskLedgerViewModel,
    snackbar: SnackbarHostState,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit,
    onRequestAttachments: (NoteDraft) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    if (note == null && !creatingNote) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Edit, null)
                Text(localizedText("Select a note or create a new one."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val stateKey = note?.id?.toString() ?: "new-$newDraftNonce"
    var title by rememberSaveable(stateKey) { mutableStateOf(note?.title.orEmpty()) }
    var body by rememberSaveable(stateKey) { mutableStateOf(note?.body.orEmpty()) }
    var folderId by rememberSaveable(stateKey) { mutableStateOf(note?.folderId ?: initialFolderId) }
    var pinned by rememberSaveable(stateKey) { mutableStateOf(note?.pinned ?: false) }
    var folderMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uiLanguage = LocalUiLanguage.current
    val attachmentFlow = remember(note?.id) {
        note?.id?.let { viewModel.attachments(AttachmentOwnerType.NOTE, it) } ?: flowOf(emptyList())
    }
    val attachments by attachmentFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val openAttachment = rememberAttachmentOpener { message ->
        scope.launch { snackbar.showSnackbar(translateUiText(message, uiLanguage)) }
    }
    val folderLabel = folders.firstOrNull { it.id == folderId }?.name ?: localizedText("Unfiled")

    fun draft() = NoteDraft(note?.id, folderId, title, body, pinned)

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = localizedText("Back to notes"))
                }
            }
            Text(
                localizedText(if (note == null) "New note" else "Edit note"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (note != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = localizedText("Delete note"))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(localizedText("Title (optional)")) },
            placeholder = { Text(localizedText("Derived from the first line if empty")) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        Box {
            FilledTonalButton(onClick = { folderMenu = true }) {
                Icon(Icons.Outlined.Folder, null)
                Spacer(Modifier.width(6.dp))
                Text(folderLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.UnfoldMore, null)
            }
            DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                DropdownMenuItem(
                    text = { Text(localizedText("Unfiled")) },
                    onClick = { folderId = null; folderMenu = false },
                )
                flattenNoteFolders(folders).forEach { row ->
                    DropdownMenuItem(
                        text = { Text(localizedText("  ").repeat(row.depth) + row.folder.name) },
                        onClick = { folderId = row.folder.id; folderMenu = false },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = pinned, onCheckedChange = { pinned = it })
            Text(localizedText("Pin this note"))
        }
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text(localizedText("Note")) },
            placeholder = { Text(localizedText("Write anything…")) },
            modifier = Modifier.fillMaxWidth().height(300.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                viewModel.saveNote(
                    draft(),
                    onSaved = { id ->
                        onSaved(id)
                        scope.launch { snackbar.showSnackbar(translateUiText("Note saved", uiLanguage)) }
                    },
                    onFailure = { message ->
                        scope.launch { snackbar.showSnackbar(translateUiText(message, uiLanguage)) }
                    },
                )
            }) { Text(localizedText("Save")) }
            FilledTonalButton(onClick = { onRequestAttachments(draft()) }) {
                Icon(Icons.Outlined.AttachFile, null)
                Text(localizedText("Add files"))
            }
        }
        Text(
            localizedText("Files and images are copied into private app storage. Passwords should use the secure Vault."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        attachments.forEach { attachment ->
            AttachmentRow(
                attachment = attachment,
                onOpen = { openAttachment(attachment) },
                onRemove = {
                    viewModel.removeAttachment(attachment.id) { token ->
                        scope.launch {
                            val result = snackbar.showSnackbar(
                                message = translateUiText("Removed ${attachment.originalName}", uiLanguage),
                                actionLabel = translateUiText("Undo", uiLanguage),
                                withDismissAction = true,
                                duration = SnackbarDuration.Long,
                            )
                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                viewModel.undoRemoveAttachment(token)
                            }
                        }
                    }
                },
            )
        }
    }
    if (confirmDelete && note != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(localizedText("Delete this note?")) },
            text = { Text(localizedText("The note will be removed. Its private files will be cleaned up safely.")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNote(
                        note.id,
                        onDeleted = onDeleted,
                        onFailure = { message ->
                            scope.launch { snackbar.showSnackbar(translateUiText(message, uiLanguage)) }
                        },
                    )
                    confirmDelete = false
                }) { Text(localizedText("Delete")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(localizedText("Cancel")) } },
        )
    }
}

@Composable
private fun AttachmentRow(
    attachment: AttachmentEntity,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = onOpen,
            label = { Text(attachment.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = localizedText("Remove ${attachment.originalName}"),
            )
        }
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initialName: String,
    parentName: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText(title)) },
        text = {
            Column {
                if (parentName != null) {
                    Text(localizedText("Inside $parentName"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(localizedText("Folder name")) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(localizedText("Save"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("Cancel")) } },
    )
}

internal data class NoteFolderRow(val folder: NoteFolderEntity, val depth: Int)

internal fun flattenNoteFolders(folders: List<NoteFolderEntity>): List<NoteFolderRow> {
    val children = folders.groupBy(NoteFolderEntity::parentId)
    val result = ArrayList<NoteFolderRow>(folders.size)
    val visited = HashSet<Long>()
    fun append(parentId: Long?, depth: Int) {
        children[parentId].orEmpty()
            .sortedWith(compareBy(NoteFolderEntity::sortOrder, { it.name.lowercase() }, NoteFolderEntity::id))
            .forEach { folder ->
                if (visited.add(folder.id)) {
                    result += NoteFolderRow(folder, depth)
                    append(folder.id, depth + 1)
                }
            }
    }
    append(null, 0)
    folders.filterNot { it.id in visited }.forEach { result += NoteFolderRow(it, 0) }
    return result
}

private enum class FolderDialogMode { Create, Rename }
