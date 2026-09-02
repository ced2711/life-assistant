package com.ced2711.lifetracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentQuotaDaoInstrumentedTest {
    private lateinit var database: TaskLedgerDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TaskLedgerDatabase::class.java).build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun pendingDeleteRowsConsumeGlobalCapacityAndRejectedBatchIsAtomic() = runBlocking {
        val dao = database.dao()
        val todoId = dao.insertTodo(TodoEntity(title = "Todo", description = "Todo"))
        val existing = attachment(todoId, "existing", 100, pendingDeleteAt = 1_000)
        dao.insertAttachment(existing)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                dao.insertAttachmentsWithinLimit(
                    ownerType = AttachmentOwnerType.TODO.name,
                    ownerId = todoId,
                    attachments = listOf(attachment(todoId, "one", 20), attachment(todoId, "two", 9)),
                    maxAttachments = 10,
                    maxTotalBytes = 128,
                )
            }
        }

        assertEquals(
            "Attachments can use up to 128 MB in total. Remove one or more files and try again.",
            failure.message,
        )
        assertEquals(listOf("existing"), dao.getAllOwnerAttachments(AttachmentOwnerType.TODO.name, todoId).map { it.originalName })
    }

    @Test
    fun pendingDeleteRowsAlsoConsumePerOwnerCount() = runBlocking {
        val dao = database.dao()
        val todoId = dao.insertTodo(TodoEntity(title = "Todo", description = "Todo"))
        dao.insertAttachment(attachment(todoId, "pending", 1, pendingDeleteAt = 1_000))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                dao.insertAttachmentsWithinLimit(
                    ownerType = AttachmentOwnerType.TODO.name,
                    ownerId = todoId,
                    attachments = listOf(attachment(todoId, "new", 1)),
                    maxAttachments = 1,
                    maxTotalBytes = 128,
                )
            }
        }

        assertEquals(listOf("pending"), dao.getAllOwnerAttachments(AttachmentOwnerType.TODO.name, todoId).map { it.originalName })
    }

    @Test
    fun batchThatExactlyFillsGlobalCapacityIsInsertedCompletely() = runBlocking {
        val dao = database.dao()
        val todoId = dao.insertTodo(TodoEntity(title = "Todo", description = "Todo"))
        dao.insertAttachment(attachment(todoId, "existing", 100))

        val insertedIds = dao.insertAttachmentsWithinLimit(
            ownerType = AttachmentOwnerType.TODO.name,
            ownerId = todoId,
            attachments = listOf(attachment(todoId, "one", 20), attachment(todoId, "two", 8)),
            maxAttachments = 10,
            maxTotalBytes = 128,
        )

        assertEquals(2, insertedIds.size)
        assertEquals(128L, dao.getAllAttachmentSizeBytes().sum())
    }

    @Test
    fun legacyOverLimitRowsStayWhileEveryNewAdditionIsRejected() = runBlocking {
        val dao = database.dao()
        val todoId = dao.insertTodo(TodoEntity(title = "Todo", description = "Todo"))
        val existingId = dao.insertAttachment(attachment(todoId, "legacy", 129))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                dao.insertAttachmentsWithinLimit(
                    ownerType = AttachmentOwnerType.TODO.name,
                    ownerId = todoId,
                    attachments = listOf(attachment(todoId, "new", 0)),
                    maxAttachments = 10,
                    maxTotalBytes = 128,
                )
            }
        }

        assertEquals(existingId, dao.getAllOwnerAttachments(AttachmentOwnerType.TODO.name, todoId).single().id)
    }

    private fun attachment(
        ownerId: Long,
        name: String,
        sizeBytes: Long,
        pendingDeleteAt: Long? = null,
    ) = AttachmentEntity(
        ownerType = AttachmentOwnerType.TODO,
        ownerId = ownerId,
        privatePath = "/private/$name",
        originalName = name,
        mimeType = "application/octet-stream",
        sizeBytes = sizeBytes,
        pendingDeleteAt = pendingDeleteAt,
    )
}
