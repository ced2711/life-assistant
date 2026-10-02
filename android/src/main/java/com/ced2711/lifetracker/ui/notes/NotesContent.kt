package com.ced2711.lifetracker.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.NoteFolderEntity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.components.SearchField
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.IconTile
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.Pill
import com.ced2711.lifetracker.ui.design.ReadableWidth
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Everything the Notes page can ask for; the screen wires these to the view model. */
internal class NotesActions(
    val onScope: (NoteScope) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onOpenNote: (NoteEntity) -> Unit = {},
    val onNewNote: () -> Unit = {},
    val onOpenVault: () -> Unit = {},
    val onNewFolder: (parent: NoteFolderEntity?) -> Unit = {},
    val onRenameFolder: (NoteFolderEntity) -> Unit = {},
    val onDeleteFolder: (NoteFolderEntity) -> Unit = {},
    val onCloseEditor: () -> Unit = {},
    val onAddFiles: () -> Unit = {},
    val onOpenAttachment: (AttachmentEntity) -> Unit = {},
    val onRemoveAttachment: (AttachmentEntity) -> Unit = {},
    val onDeleteNote: () -> Unit = {},
)

/**
 * The Notes page: the list with search and folders, and the open note. On a phone the open note
 * replaces the list; when [isWide] they sit side by side.
 */
@Composable
internal fun NotesContent(
    notes: List<NoteEntity>,
    folders: List<NoteFolderEntity>,
    settings: AppSettings,
    scope: NoteScope,
    query: String,
    editor: NoteEditorState?,
    attachments: List<AttachmentEntity>,
    isWide: Boolean,
    actions: NotesActions,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
) {
    val shown = remember(notes, folders, scope, query) { visibleNotes(notes, folders, scope, query) }
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = snackbarHost,
        floatingActionButton = { if (!isWide && editor == null) NewNoteButton(actions.onNewNote) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isWide -> Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        NotesList(shown, folders, settings, scope, query, editor?.savedId, actions, Modifier.fillMaxSize())
                        NewNoteButton(actions.onNewNote, Modifier.align(Alignment.BottomEnd).padding(Space.lg))
                    }
                    Box(Modifier.fillMaxHeight().width(1.dp).background(LifeTheme.colors.divider))
                    if (editor != null) {
                        NoteEditorPane(editor, folders, attachments, true, actions, Modifier.weight(1f).fillMaxHeight())
                    } else {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            EmptyState(title = localizedText("Select a note or create a new one."), icon = Icons.AutoMirrored.Rounded.Notes)
                        }
                    }
                }
                editor != null -> NoteEditorPane(editor, folders, attachments, false, actions, Modifier.fillMaxSize())
                else -> ReadableWidth(Modifier.fillMaxHeight()) {
                    NotesList(shown, folders, settings, scope, query, null, actions, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun NewNoteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(onClick = onClick, modifier = modifier, containerColor = MaterialTheme.colorScheme.primary) {
        Icon(Icons.Rounded.Add, localizedText("New note"))
    }
}

@Composable
private fun NotesList(
    shown: List<NoteEntity>,
    folders: List<NoteFolderEntity>,
    settings: AppSettings,
    scope: NoteScope,
    query: String,
    selectedNoteId: Long?,
    actions: NotesActions,
    modifier: Modifier = Modifier,
) {
    val language = LocalUiLanguage.current
    val selectedFolder = (scope as? NoteScope.Folder)?.let { current -> folders.firstOrNull { it.id == current.id } }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.xs, top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, actions.onQuery, Modifier.weight(1f), placeholder = localizedText("Search notes"))
            IconButton(onClick = actions.onOpenVault) {
                Icon(Icons.Outlined.Lock, localizedText("Open password vault"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = Space.lg, end = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Pill(localizedText("All notes"), scope == NoteScope.All, { actions.onScope(NoteScope.All) }, exclusive = true)
                Pill(localizedText("Pinned"), scope == NoteScope.Pinned, { actions.onScope(NoteScope.Pinned) }, exclusive = true)
                Pill(localizedText("Unfiled"), scope == NoteScope.Unfiled, { actions.onScope(NoteScope.Unfiled) }, exclusive = true)
                remember(folders) { flattenNoteFolders(folders) }.forEach { row ->
                    Pill(
                        text = noteFolderPath(row.folder.id, folders),
                        selected = selectedFolder?.id == row.folder.id,
                        onClick = { actions.onScope(NoteScope.Folder(row.folder.id)) },
                        exclusive = true,
                        leading = { Icon(Icons.Outlined.Folder, null, Modifier.size(16.dp)) },
                    )
                }
            }
            FolderMenu(selectedFolder, actions)
        }
        Text(
            notesCountText(shown.size, language),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Space.lg, end = Space.lg, top = Space.xs, bottom = Space.xs),
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Space.xs, end = Space.xs, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.xxs),
        ) {
            if (shown.isEmpty()) {
                item {
                    val searching = query.isNotBlank()
                    EmptyState(
                        title = localizedText(if (searching) "No notes match" else "No notes here yet"),
                        icon = Icons.AutoMirrored.Rounded.Notes,
                        body = localizedText(if (searching) "Try other words, or look in All notes." else "Tap + to write one. Notes save themselves as you type."),
                    )
                }
            }
            items(shown, key = NoteEntity::id) { note ->
                NoteRow(
                    note = note,
                    folderPath = note.folderId?.takeIf { it != selectedFolder?.id }?.let { noteFolderPath(it, folders) },
                    settings = settings,
                    selected = note.id == selectedNoteId,
                    onClick = { actions.onOpenNote(note) },
                )
            }
        }
    }
}

/** The folder actions: always in sight next to the folders, one tap away. */
@Composable
private fun FolderMenu(selected: NoteFolderEntity?, actions: NotesActions) {
    val language = LocalUiLanguage.current
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(end = Space.xs)) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, localizedText("Manage folders"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (selected == null) localizedText("New folder") else newFolderInText(selected.name, language)) },
                onClick = { open = false; actions.onNewFolder(selected) },
            )
            if (selected != null) {
                DropdownMenuItem(text = { Text(localizedText("Rename folder")) }, onClick = { open = false; actions.onRenameFolder(selected) })
                DropdownMenuItem(
                    text = { Text(localizedText("Delete folder"), color = LifeTheme.colors.danger) },
                    onClick = { open = false; actions.onDeleteFolder(selected) },
                )
            }
        }
    }
}

@Composable
private fun NoteRow(note: NoteEntity, folderPath: String?, settings: AppSettings, selected: Boolean, onClick: () -> Unit) {
    val language = LocalUiLanguage.current
    val changed = Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val date = if (changed == LocalDate.now()) localizedText("Today") else UserFormatting.formatDate(changed, settings.dateFormat, uiLocale(language))
    val preview = remember(note.body) { note.body.lineSequence().map(String::trim).filter(String::isNotEmpty).take(4).joinToString("  ").take(240) }
    ListRow(
        title = note.title,
        titleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        supporting = preview.ifEmpty { null },
        maxTitleLines = 1,
        selected = selected,
        onClick = onClick,
        trailing = {
            if (note.pinned) Icon(Icons.Rounded.PushPin, localizedText("Pinned"), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        },
        extra = folderPath?.let { path ->
            { Text(path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
    )
}

/** The open note: a page to write on, with the few choices a note has kept quiet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteEditorPane(
    editor: NoteEditorState,
    folders: List<NoteFolderEntity>,
    attachments: List<AttachmentEntity>,
    isWide: Boolean,
    actions: NotesActions,
    modifier: Modifier = Modifier,
) {
    val language = LocalUiLanguage.current
    val bodyFocus = remember { FocusRequester() }
    // A new note is for writing straight away.
    LaunchedEffect(editor) { if (editor.savedId == null) runCatching { bodyFocus.requestFocus() } }
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onCloseEditor) {
                if (isWide) Icon(Icons.Rounded.Close, localizedText("Close")) else Icon(Icons.AutoMirrored.Rounded.ArrowBack, localizedText("Back to notes"))
            }
            Spacer(Modifier.weight(1f))
            val status = editor.status
            Text(
                localizedText(
                    when (status) {
                        NoteSaveStatus.New -> "New note"
                        NoteSaveStatus.Saving -> "Saving…"
                        NoteSaveStatus.Saved -> "Saved"
                        NoteSaveStatus.Failed -> "Not saved"
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (status == NoteSaveStatus.Failed) LifeTheme.colors.danger else quiet,
                modifier = Modifier.padding(horizontal = Space.sm),
            )
            IconButton(onClick = actions.onAddFiles) { Icon(Icons.Rounded.AttachFile, localizedText("Add files"), tint = quiet) }
            if (editor.savedId != null) {
                IconButton(onClick = actions.onDeleteNote) { Icon(Icons.Rounded.DeleteOutline, localizedText("Delete note"), tint = quiet) }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // The body reaches the bottom of the page, so tapping anywhere below the title writes.
            val filesHeight = if (attachments.isEmpty()) 0.dp else 48.dp + 60.dp * minOf(attachments.size, 2)
            val bodyMinHeight = (maxHeight - 170.dp - filesHeight).coerceAtLeast(160.dp)
            ReadableWidth(Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().padding(start = Space.xl, end = Space.xl, bottom = Space.xxxl)) {
                    WritingField(
                        value = editor.title,
                        onValueChange = { typed ->
                            // Enter in the title moves on to the text.
                            if ('\n' in typed) runCatching { bodyFocus.requestFocus() }
                            editor.title = typed.replace("\n", "").take(MAX_NOTE_TITLE_LENGTH)
                        },
                        placeholder = localizedText("Title"),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = Space.xs, bottom = Space.xs),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        FolderChoice(editor, folders, Modifier.weight(1f, fill = false))
                        Row(
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .toggleable(value = editor.pinned, role = Role.Switch, onValueChange = { editor.pinned = it })
                                .heightIn(min = 44.dp)
                                .padding(horizontal = Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val tint = if (editor.pinned) MaterialTheme.colorScheme.primary else quiet
                            Icon(if (editor.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, null, Modifier.size(18.dp), tint = tint)
                            Text(localizedText(if (editor.pinned) "Pinned" else "Pin"), style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
                        }
                    }
                    WritingField(
                        value = editor.body,
                        onValueChange = { if (it.length <= MAX_NOTE_BODY_LENGTH) editor.body = it },
                        placeholder = localizedText("Start writing…"),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15f),
                        minHeight = bodyMinHeight,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm).focusRequester(bodyFocus),
                    )
                    if (attachments.isNotEmpty()) {
                        SectionLabel(localizedText("Files"), count = attachments.size, modifier = Modifier.padding(top = Space.lg))
                        attachments.forEach { attachment ->
                            ListRow(
                                // Lines the file names up with the text above despite the row's own inset.
                                modifier = Modifier.layout { measurable, constraints ->
                                    val inset = Space.md.roundToPx()
                                    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + 2 * inset))
                                    layout(constraints.maxWidth, placeable.height) { placeable.place(-inset, 0) }
                                },
                                title = attachment.originalName,
                                supporting = fileSizeText(attachment.sizeBytes),
                                maxTitleLines = 1,
                                leading = { IconTile(Icons.Rounded.AttachFile) },
                                onClick = { actions.onOpenAttachment(attachment) },
                                onClickLabel = localizedText("Open"),
                                trailing = {
                                    IconButton(onClick = { actions.onRemoveAttachment(attachment) }) {
                                        Icon(Icons.Rounded.Close, removeFileText(attachment.originalName, language), Modifier.size(20.dp), tint = quiet)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The note's folder as quiet text; tapping it lists the folders. */
@Composable
private fun FolderChoice(editor: NoteEditorState, folders: List<NoteFolderEntity>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val current = editor.folderId?.takeIf { id -> folders.any { it.id == id } }
    Box(modifier) {
        Row(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = localizedText("Folder"), role = Role.DropdownList) { open = true }
                .heightIn(min = 44.dp)
                .padding(end = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp), tint = quiet)
            Text(
                current?.let { noteFolderPath(it, folders) } ?: localizedText("Unfiled"),
                style = MaterialTheme.typography.labelLarge,
                color = quiet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp), tint = quiet)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(localizedText("Unfiled")) }, onClick = { editor.folderId = null; open = false })
            flattenNoteFolders(folders).forEach { row ->
                DropdownMenuItem(
                    text = { Text(row.folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { editor.folderId = row.folder.id; open = false },
                    modifier = Modifier.padding(start = Space.lg * row.depth),
                )
            }
        }
    }
}

internal fun notesCountText(count: Int, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "$count 条笔记"
    UiLanguage.ENGLISH -> if (count == 1) "1 note" else "$count notes"
}

internal fun newFolderInText(parent: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "在“$parent”中新建文件夹"
    UiLanguage.ENGLISH -> "New folder in $parent"
}

internal fun removeFileText(name: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "移除 $name"
    UiLanguage.ENGLISH -> "Remove $name"
}

internal fun removedFileText(name: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "已移除 $name"
    UiLanguage.ENGLISH -> "Removed $name"
}

internal fun deleteFolderTitle(name: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "删除“$name”？"
    UiLanguage.ENGLISH -> "Delete $name?"
}

private fun fileSizeText(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1_024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1_024.0 * 1_024.0))
}
