package com.ced2711.lifetracker.ui.notes

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.data.local.NoteFolderEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog
import com.ced2711.lifetracker.ui.attachment.rememberAttachmentOpener
import com.ced2711.lifetracker.ui.components.ConfirmDialog
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.lock.findHostActivity
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val AUTOSAVE_DELAY_MILLIS = 700L

private sealed interface FolderDialog {
    data class Create(val parent: NoteFolderEntity?) : FolderDialog
    data class Rename(val folder: NoteFolderEntity) : FolderDialog
}

/**
 * Notes: search, folders, the list and the open note. A note saves itself a moment after typing
 * stops, when it is closed and when the app goes to the background, so Back never loses text.
 */
@Composable
fun NotesScreen(
    viewModel: TaskLedgerViewModel,
    onOpenVault: () -> Unit,
    modifier: Modifier = Modifier,
    isWide: Boolean,
) {
    val folders by viewModel.noteFolders.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val holder = androidx.lifecycle.viewmodel.compose.viewModel<NoteEditorHolder>()
    val session = holder.session
    var scopeKey by rememberSaveable { mutableStateOf(NoteScope.All.key) }
    var query by rememberSaveable { mutableStateOf("") }
    // The saved note that is open, to open it again after Android closed the app in the background.
    var reopenNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var attachmentTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var folderDialog by remember { mutableStateOf<FolderDialog?>(null) }
    var deletingFolder by remember { mutableStateOf<NoteFolderEntity?>(null) }
    var confirmDeleteNote by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val coroutines = rememberCoroutineScope()
    val language = LocalUiLanguage.current
    val activity = LocalContext.current.findHostActivity()

    fun showMessage(message: String) {
        coroutines.launch { snackbar.showSnackbar(translateUiText(message, language)) }
    }

    val store: NoteStore = remember(viewModel) {
        { draft, onSaved, onFailure -> viewModel.saveNote(draft, onSaved, onFailure) }
    }
    // A folder that was deleted (here or on another device) falls back to all notes.
    val scope = NoteScope.fromKey(scopeKey).let { chosen ->
        if (chosen is NoteScope.Folder && folders.none { it.id == chosen.id }) NoteScope.All else chosen
    }

    fun closeEditor() {
        holder.session?.save(store)
        holder.session = null
        reopenNoteId = null
    }

    if (session != null) {
        val stored = notes.firstOrNull { it.id == session.savedId }
        // Autosave a moment after typing stops.
        LaunchedEffect(session, session.title, session.body, session.pinned, session.folderId) {
            if (session.dirty) {
                delay(AUTOSAVE_DELAY_MILLIS)
                session.save(store)
            }
        }
        LaunchedEffect(session, stored) {
            if (!session.onStored(stored) && holder.session === session) {
                holder.session = null
                reopenNoteId = null
            }
        }
        SideEffect { reopenNoteId = session.savedId }
    } else {
        LaunchedEffect(notes) {
            val id = reopenNoteId ?: return@LaunchedEffect
            notes.firstOrNull { it.id == id }?.let { if (holder.session == null) holder.session = NoteEditorState(it) }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { holder.session?.save(store) }
    DisposableEffect(Unit) {
        onDispose {
            holder.session?.save(store)
            // Rotating or folding rebuilds the screen and keeps the note open; leaving Notes closes it.
            if (activity?.isChangingConfigurations != true) holder.session = null
        }
    }
    BackHandler(enabled = session != null && !isWide) { closeEditor() }

    val attachmentFlow = remember(session?.savedId) {
        session?.savedId?.let { viewModel.attachments(AttachmentOwnerType.NOTE, it) } ?: flowOf(emptyList())
    }
    val attachments by attachmentFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val openAttachment = rememberAttachmentOpener(::showMessage)
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        val noteId = attachmentTargetId
        attachmentTargetId = null
        if (noteId != null && uris.isNotEmpty()) {
            viewModel.addAttachments(
                ownerType = AttachmentOwnerType.NOTE,
                ownerId = noteId,
                uris = uris,
                copyAttemptId = UUID.randomUUID().toString(),
                onCopyFailed = ::showMessage,
            )
        }
    }

    val actions = NotesActions(
        onScope = { scopeKey = it.key },
        onQuery = { query = it },
        onOpenNote = { note ->
            if (holder.session?.savedId != note.id) {
                holder.session?.save(store)
                holder.session = NoteEditorState(note)
            }
        },
        onNewNote = {
            holder.session?.save(store)
            holder.session = NoteEditorState(null, (scope as? NoteScope.Folder)?.id)
        },
        onOpenVault = onOpenVault,
        onNewFolder = { folderDialog = FolderDialog.Create(it) },
        onRenameFolder = { folderDialog = FolderDialog.Rename(it) },
        onDeleteFolder = { deletingFolder = it },
        onCloseEditor = ::closeEditor,
        onAddFiles = {
            // A new note is saved first, so the files have something to belong to.
            holder.session?.save(store, force = true) { noteId ->
                attachmentTargetId = noteId
                attachmentLauncher.launch(arrayOf("*/*"))
            }
        },
        onOpenAttachment = { openAttachment(it) },
        onRemoveAttachment = { attachment ->
            viewModel.removeAttachment(attachment.id) { token ->
                coroutines.launch {
                    val result = snackbar.showSnackbar(
                        message = removedFileText(attachment.originalName, language),
                        actionLabel = translateUiText("Undo", language),
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoRemoveAttachment(token)
                }
            }
        },
        onDeleteNote = { confirmDeleteNote = true },
    )

    NotesContent(
        notes = notes,
        folders = folders,
        settings = settings,
        scope = scope,
        query = query,
        editor = session,
        attachments = attachments,
        isWide = isWide,
        actions = actions,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    )

    folderDialog?.let { dialog ->
        FolderNameDialog(
            title = localizedText(if (dialog is FolderDialog.Create) "New folder" else "Rename folder"),
            initialName = (dialog as? FolderDialog.Rename)?.folder?.name.orEmpty(),
            parentName = (dialog as? FolderDialog.Create)?.parent?.name,
            onDismiss = { folderDialog = null },
            onConfirm = { name ->
                when (dialog) {
                    is FolderDialog.Create -> viewModel.addNoteFolder(
                        name = name,
                        parentId = dialog.parent?.id,
                        onSaved = { id -> folderDialog = null; scopeKey = NoteScope.Folder(id).key },
                        onFailure = ::showMessage,
                    )
                    is FolderDialog.Rename -> viewModel.renameNoteFolder(
                        folderId = dialog.folder.id,
                        name = name,
                        onSaved = { folderDialog = null },
                        onFailure = ::showMessage,
                    )
                }
            },
        )
    }
    deletingFolder?.let { folder ->
        ConfirmDialog(
            title = deleteFolderTitle(folder.name, language),
            text = localizedText("Notes will move to Unfiled. Child folders will move up one level."),
            confirmLabel = localizedText("Delete"),
            destructive = true,
            onDismiss = { deletingFolder = null },
            onConfirm = {
                deletingFolder = null
                if (scope == NoteScope.Folder(folder.id)) scopeKey = NoteScope.All.key
                viewModel.deleteNoteFolder(folder.id, onFailure = ::showMessage)
            },
        )
    }
    val noteToDelete = session?.savedId
    if (confirmDeleteNote && session != null && noteToDelete != null) {
        ConfirmDialog(
            title = localizedText("Delete this note?"),
            text = localizedText("The note will be removed. Its private files will be cleaned up safely."),
            confirmLabel = localizedText("Delete"),
            destructive = true,
            onDismiss = { confirmDeleteNote = false },
            onConfirm = {
                confirmDeleteNote = false
                session.discard()
                if (holder.session === session) holder.session = null
                reopenNoteId = null
                viewModel.deleteNote(noteToDelete, onFailure = ::showMessage)
            },
        )
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
    val language = LocalUiLanguage.current
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (parentName != null) {
                    Text(insideFolderText(parentName, language), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LifeTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = localizedText("Folder name"),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(localizedText("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("Cancel")) } },
    )
}

private fun insideFolderText(parent: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "位于“$parent”中"
    UiLanguage.ENGLISH -> "Inside $parent"
}
