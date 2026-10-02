package com.ced2711.lifetracker.ui.render

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.ConfessionEntry
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.ui.confessional.ConfessionalContent
import com.ced2711.lifetracker.ui.diary.DiaryContent
import com.ced2711.lifetracker.ui.diary.DiarySaveStatus
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.notes.NoteEditorState
import com.ced2711.lifetracker.ui.notes.NoteScope
import com.ced2711.lifetracker.ui.notes.NotesActions
import com.ced2711.lifetracker.ui.notes.NotesContent

private val today = RenderSamples.today.toEpochDay()

private val sampleNotes = RenderSamples.notes + listOf(
    NoteEntity(id = 4, folderId = 1, title = "Ideas for the garden shed", body = "Shelves along the back wall, hooks for the tools, and a small workbench under the window.", updatedAt = System.currentTimeMillis() - 6 * 86_400_000L),
    NoteEntity(id = 5, title = "Books to read", body = "", updatedAt = System.currentTimeMillis() - 9 * 86_400_000L),
)

private val sampleAttachments = listOf(
    AttachmentEntity(id = 1, ownerType = AttachmentOwnerType.NOTE, ownerId = 1, privatePath = "a", originalName = "release-plan.pdf", mimeType = "application/pdf", sizeBytes = 482_000),
    AttachmentEntity(id = 2, ownerType = AttachmentOwnerType.NOTE, ownerId = 1, privatePath = "b", originalName = "installer screenshot.png", mimeType = "image/png", sizeBytes = 1_840_000),
)

private val sampleConfessions = listOf(
    ConfessionEntry("a", System.currentTimeMillis() - 2 * 86_400_000L, "I never told her how much that evening meant to me."),
    ConfessionEntry("b", System.currentTimeMillis() - 20 * 86_400_000L, "I was wrong about the move, and too proud to say so."),
)

@Composable
private fun Notes(isWide: Boolean, notes: List<NoteEntity> = sampleNotes, scope: NoteScope = NoteScope.All, open: NoteEntity? = null) {
    NotesContent(
        notes = notes,
        folders = if (notes.isEmpty()) emptyList() else RenderSamples.noteFolders,
        settings = RenderSamples.settings,
        scope = scope,
        query = "",
        editor = open?.let { NoteEditorState(it) },
        attachments = if (open != null) sampleAttachments else emptyList(),
        isWide = isWide,
        actions = NotesActions(),
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun Diary(isWide: Boolean, selectedDay: Long, text: String) {
    DiaryContent(
        entries = RenderSamples.diary,
        settings = RenderSamples.settings,
        selectedDay = selectedDay,
        today = today,
        text = text,
        status = if (text.isEmpty()) DiarySaveStatus.Empty else DiarySaveStatus.Saved,
        hasPage = text.isNotEmpty(),
        isWide = isWide,
        onTextChange = {},
        onSelectDay = {},
        onDelete = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun Confessional(isWide: Boolean, text: String, revealed: Boolean, message: String? = null) {
    ConfessionalContent(
        text = text,
        onTextChange = {},
        burnProgress = 0f,
        burning = false,
        message = message,
        sealed = sampleConfessions,
        revealed = revealed,
        isWide = isWide,
        onBurn = {},
        onSeal = {},
        onOpen = {},
        onHide = {},
        onBurnAll = {},
        onBurnOne = {},
        modifier = Modifier.fillMaxSize(),
    )
}

/** Scenes of the Notes, Diary and Confessional screens for ScreenRenderTest; see RenderScene. */
internal val notesScenes: List<RenderScene> = listOf(
    RenderScene("notes-list", TopLevelDestination.NOTES) { isWide -> Notes(isWide) },
    RenderScene("notes-list-folder", TopLevelDestination.NOTES) { isWide -> Notes(isWide, scope = NoteScope.Folder(1)) },
    RenderScene("notes-list-empty", TopLevelDestination.NOTES) { isWide -> Notes(isWide, notes = emptyList()) },
    RenderScene("notes-editor", TopLevelDestination.NOTES) { isWide -> Notes(isWide, open = sampleNotes.first()) },
    RenderScene("notes-editor-new", TopLevelDestination.NOTES) { isWide ->
        NotesContent(
            notes = sampleNotes,
            folders = RenderSamples.noteFolders,
            settings = RenderSamples.settings,
            scope = NoteScope.All,
            query = "",
            editor = NoteEditorState(null),
            attachments = emptyList(),
            isWide = isWide,
            actions = NotesActions(),
            modifier = Modifier.fillMaxSize(),
        )
    },
    RenderScene("diary-page", TopLevelDestination.DIARY) { isWide ->
        Diary(isWide, today - 1, RenderSamples.diary.first().body + "\n\nThe light on the water was the best part. I should do this more often, and leave the phone at home next time.")
    },
    RenderScene("diary-empty-day", TopLevelDestination.DIARY) { isWide -> Diary(isWide, today, "") },
    RenderScene("confessional-typed", TopLevelDestination.CONFESSIONAL) { isWide ->
        Confessional(isWide, "I have been putting off the call for weeks because I am afraid of what they will say.", revealed = false)
    },
    RenderScene("confessional-sealed", TopLevelDestination.CONFESSIONAL) { isWide ->
        Confessional(isWide, "", revealed = true, message = localizedText("Sealed on this device."))
    },
)
