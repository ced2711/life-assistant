package com.ced2711.lifetracker.data.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.repository.TaskLedgerRepository
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.NoteDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesRepositoryInstrumentedTest {
    private lateinit var database: TaskLedgerDatabase
    private lateinit var repository: TaskLedgerRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TaskLedgerDatabase::class.java).build()
        repository = TaskLedgerRepository(database, wallClockMillis = { 1_000L })
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun nestedFoldersNotesAndAttachmentsKeepSafeDeleteSemantics() = runBlocking {
        val parentId = repository.addNoteFolder("Reference")
        val childId = repository.addNoteFolder("Accounts", parentId)
        val noteId = repository.saveNote(
            NoteDraft(folderId = childId, body = "First line\nLong-term details", pinned = true),
        )
        database.dao().insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.NOTE,
                ownerId = noteId,
                privatePath = "/private/test",
                originalName = "important.pdf",
                mimeType = "application/pdf",
                sizeBytes = 10,
                createdAt = 1_000,
            ),
        )

        assertEquals("First line", repository.getNote(noteId)?.title)
        assertTrue(repository.attachmentOwnerExists(AttachmentOwnerType.NOTE, noteId))

        repository.deleteNoteFolder(parentId)
        assertEquals(null, database.dao().getNoteFolder(childId)?.parentId)
        assertEquals(childId, repository.getNote(noteId)?.folderId)

        repository.deleteNoteFolder(childId)
        assertEquals(null, repository.getNote(noteId)?.folderId)

        repository.deleteNote(noteId)
        assertFalse(repository.attachmentOwnerExists(AttachmentOwnerType.NOTE, noteId))
        assertEquals(null, repository.getNote(noteId))
        assertNotNull(database.backupDao().backupAttachments().single().pendingDeleteAt)
    }
}
