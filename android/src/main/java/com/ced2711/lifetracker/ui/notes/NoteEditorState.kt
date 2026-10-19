package com.ced2711.lifetracker.ui.notes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.NoteFolderEntity
import com.ced2711.lifetracker.domain.model.NoteDraft

internal const val MAX_NOTE_TITLE_LENGTH = 500
internal const val MAX_NOTE_BODY_LENGTH = 1_000_000

/** Which notes the list shows. */
internal sealed interface NoteScope {
    data object All : NoteScope
    data object Pinned : NoteScope
    data object Unfiled : NoteScope
    data class Folder(val id: Long) : NoteScope

    /** A short text form, so the choice survives the app being closed in the background. */
    val key: String
        get() = when (this) {
            All -> "all"
            Pinned -> "pinned"
            Unfiled -> "unfiled"
            is Folder -> id.toString()
        }

    companion object {
        fun fromKey(key: String): NoteScope = when (key) {
            "pinned" -> Pinned
            "unfiled" -> Unfiled
            else -> key.toLongOrNull()?.let(::Folder) ?: All
        }
    }
}

/** What the quiet line at the top of the editor says. */
internal enum class NoteSaveStatus { New, Saving, Saved, Failed }

/** Saves a note and reports its id, or why it could not be saved. */
internal typealias NoteStore = (draft: NoteDraft, onSaved: (Long) -> Unit, onFailure: (String) -> Unit) -> Unit

/**
 * The note being written. It keeps its own text while open, so saving never resets what is being
 * typed, and it saves one change at a time: a new note is created exactly once even when the
 * autosave, Back and "app goes to the background" all ask to save at the same moment.
 */
@Stable
internal class NoteEditorState(note: NoteEntity?, defaultFolderId: Long? = null) {
    private data class Content(val title: String, val body: String, val pinned: Boolean, val folderId: Long?)

    var title by mutableStateOf(note?.title.orEmpty())
    var body by mutableStateOf(note?.body.orEmpty())
    var pinned by mutableStateOf(note?.pinned ?: false)
    var folderId by mutableStateOf(note?.folderId ?: defaultFolderId)

    /** Null until the note was saved for the first time. */
    var savedId by mutableStateOf(note?.id)
        private set

    private var saved by mutableStateOf(note?.let { Content(it.title, it.body, it.pinned, it.folderId) })
    private var saving by mutableStateOf(false)
    private var failed by mutableStateOf(false)
    private var edited = false
    private var seenStored = note != null
    private var discarded = false
    private val afterSave = ArrayList<(Long) -> Unit>()

    private fun current() = Content(title, body, pinned, folderId)

    /** An empty new note is not worth keeping. */
    val hasContent: Boolean get() = title.isNotBlank() || body.isNotBlank()

    val dirty: Boolean get() = hasContent && current() != saved

    val status: NoteSaveStatus
        get() = when {
            saving -> NoteSaveStatus.Saving
            dirty && failed -> NoteSaveStatus.Failed
            dirty -> NoteSaveStatus.Saving
            savedId == null -> NoteSaveStatus.New
            else -> NoteSaveStatus.Saved
        }

    /**
     * Saves what changed. [then] runs with the note's id once everything typed so far is stored
     * (used to attach files). [force] also saves a new note that is still empty.
     */
    fun save(store: NoteStore, force: Boolean = false, then: ((Long) -> Unit)? = null) {
        if (discarded) return
        if (saving) {
            then?.let(afterSave::add)
            return
        }
        val id = savedId
        if (!dirty && !(force && id == null)) {
            if (id != null) then?.invoke(id)
            return
        }
        then?.let(afterSave::add)
        val snapshot = current()
        saving = true
        failed = false
        edited = true
        store(
            NoteDraft(id, snapshot.folderId, snapshot.title, snapshot.body, snapshot.pinned),
            { newId ->
                savedId = newId
                saved = snapshot
                saving = false
                if (dirty && !discarded) {
                    // More was typed while this save was on its way.
                    save(store)
                } else {
                    val waiting = afterSave.toList()
                    afterSave.clear()
                    if (!discarded) waiting.forEach { it(newId) }
                }
            },
            {
                saving = false
                failed = true
                afterSave.clear()
            },
        )
    }

    /** The note was deleted: nothing of it may be saved again. */
    fun discard() {
        discarded = true
        afterSave.clear()
    }

    /**
     * Tells the editor what is stored now. A note that was only read (never changed here) follows
     * changes made elsewhere, such as a sync. Returns false when the open note no longer exists
     * and nothing typed would be lost by closing it.
     */
    fun onStored(note: NoteEntity?): Boolean {
        if (note == null) return !(seenStored && savedId != null && !dirty && !saving)
        seenStored = true
        val base = saved ?: return true
        if (edited || saving || current() != base) return true
        val stored = Content(note.title, note.body, note.pinned, note.folderId)
        if (stored != base) {
            title = stored.title
            body = stored.body
            pinned = stored.pinned
            folderId = stored.folderId
            saved = stored
        }
        return true
    }
}

/** Keeps the open note across rotation and folding, where the screen itself is rebuilt. */
internal class NoteEditorHolder : ViewModel() {
    var session by mutableStateOf<NoteEditorState?>(null)
}

internal data class NoteFolderRow(val folder: NoteFolderEntity, val depth: Int)

/** Folders in tree order, parents before their children, with how deep each one sits. */
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

/** "Parent / Child" for a folder. */
internal fun noteFolderPath(folderId: Long, folders: List<NoteFolderEntity>): String {
    val byId = folders.associateBy(NoteFolderEntity::id)
    val names = ArrayList<String>()
    val seen = HashSet<Long>()
    var folder = byId[folderId]
    while (folder != null && seen.add(folder.id)) {
        names += folder.name
        folder = folder.parentId?.let(byId::get)
    }
    return names.asReversed().joinToString(" / ")
}

/** A folder and every folder inside it. */
internal fun descendantFolderIds(folders: List<NoteFolderEntity>, root: Long): Set<Long> {
    val children = folders.groupBy(NoteFolderEntity::parentId)
    val result = mutableSetOf(root)
    val pending = ArrayDeque(listOf(root))
    while (pending.isNotEmpty()) {
        children[pending.removeFirst()].orEmpty().forEach { if (result.add(it.id)) pending.addLast(it.id) }
    }
    return result
}

/** The notes of a scope that match the search, pinned first, then last changed. */
internal fun visibleNotes(notes: List<NoteEntity>, folders: List<NoteFolderEntity>, scope: NoteScope, query: String): List<NoteEntity> {
    val folderIds = (scope as? NoteScope.Folder)?.let { descendantFolderIds(folders, it.id) }
    val words = query.trim()
    return notes
        .filter { note ->
            when (scope) {
                NoteScope.All -> true
                NoteScope.Pinned -> note.pinned
                NoteScope.Unfiled -> note.folderId == null
                is NoteScope.Folder -> note.folderId in folderIds.orEmpty()
            }
        }
        .filter { words.isEmpty() || it.title.contains(words, ignoreCase = true) || it.body.contains(words, ignoreCase = true) }
        .sortedWith(compareByDescending<NoteEntity> { it.pinned }.thenByDescending { it.updatedAt })
}
