package com.ced2711.lifetracker.data.attachment

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentStoreTimestampIntegrationTest {
    private lateinit var database: TaskLedgerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TaskLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun pendingDeleteUsesCreationFloorWhileUndoUsesElapsedRealtime() = runBlocking {
        val dao = database.dao()
        val todoId = dao.insertTodo(
            TodoEntity(
                title = "Todo",
                description = "Todo",
                createdAt = 2_000_000L,
                updatedAt = 2_000_000L,
            ),
        )
        val attachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.TODO,
                ownerId = todoId,
                privatePath = "/private/clock-rollback.txt",
                originalName = "clock-rollback.txt",
                mimeType = "text/plain",
                sizeBytes = 10L,
                createdAt = 5_000_000L,
            ),
        )
        var elapsedRealtime = 100_000L
        val store = AttachmentStore(
            context = ApplicationProvider.getApplicationContext(),
            dao = dao,
            elapsedRealtimeMillis = { elapsedRealtime },
        )

        val token = requireNotNull(
            store.markAttachmentForDeletion(
                attachmentId = attachmentId,
                undoWindowMillis = 6_000L,
                nowMillis = 1_000_000L,
            ),
        )

        assertEquals(1_006_000L, token.expiresAtMillis)
        assertEquals(5_000_000L, token.pendingDeleteAtMillis)
        assertEquals(106_000L, token.undoExpiresAtElapsedRealtime)
        assertEquals(5_000_000L, dao.getAttachment(attachmentId)?.pendingDeleteAt)

        elapsedRealtime = token.undoExpiresAtElapsedRealtime
        assertFalse(store.undoAttachmentDeletion(token))
        assertEquals(5_000_000L, dao.getAttachment(attachmentId)?.pendingDeleteAt)
    }
}
