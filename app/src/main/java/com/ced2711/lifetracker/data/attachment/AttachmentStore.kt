package com.ced2711.lifetracker.data.attachment

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import com.ced2711.lifetracker.data.persistedDeadlineTimestamp
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.TaskLedgerDao
import com.ced2711.lifetracker.data.local.attachmentBytesTowardLimit
import com.ced2711.lifetracker.data.local.isWithinAttachmentLimit
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AttachmentDeletionToken(
    val attachmentId: Long,
    val ownerType: AttachmentOwnerType,
    val ownerId: Long,
    /** Wall-clock deadline used only to time the transient Undo affordance. */
    val expiresAtMillis: Long,
    /** Persisted compare-and-set token, floored at the attachment creation time. */
    val pendingDeleteAtMillis: Long,
    val undoExpiresAtElapsedRealtime: Long,
)

class AttachmentStore(
    context: Context,
    private val dao: TaskLedgerDao,
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private val appContext = context.applicationContext
    private val attachmentDirectory = File(appContext.filesDir, ATTACHMENT_DIRECTORY)
    private val copyMutex = Mutex()

    fun attachments(ownerType: AttachmentOwnerType, ownerId: Long): Flow<List<AttachmentEntity>> {
        require(ownerId > 0) { "Attachments require a persisted owner." }
        return dao.observeAttachments(ownerType.name, ownerId)
    }

    suspend fun getAttachments(
        ownerType: AttachmentOwnerType,
        ownerId: Long,
    ): List<AttachmentEntity> = withContext(Dispatchers.IO) {
        require(ownerId > 0) { "Attachments require a persisted owner." }
        dao.getAttachments(ownerType.name, ownerId)
    }

    suspend fun copyAttachments(
        ownerType: AttachmentOwnerType,
        ownerId: Long,
        uris: List<Uri>,
        copyAttemptId: String,
    ): List<AttachmentEntity> = copyMutex.withLock {
        require(ownerId > 0) { "Attachments require a persisted owner." }
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) return@withLock emptyList()
        val normalizedAttemptId = normalizeCopyAttemptId(copyAttemptId)

        withContext(Dispatchers.IO) {
            // A daily maintenance run is not guaranteed before the next user action. Reclaim only
            // rows whose Undo deadline has elapsed, then count every row that remains.
            purgeExpiredPendingAttachments(System.currentTimeMillis())
            requireActiveOwner(ownerType, ownerId)
            findCompletedCopyAttempt(ownerType, ownerId, normalizedAttemptId, uniqueUris.size)?.let {
                return@withContext it
            }
            val existingCount = dao.getAttachmentCount(ownerType.name, ownerId)
            require(isWithinAttachmentLimit(existingCount, uniqueUris.size, MAX_ATTACHMENTS_PER_OWNER)) {
                "An item can have at most $MAX_ATTACHMENTS_PER_OWNER attachments."
            }
            val existingBytes = attachmentBytesTowardLimit(
                dao.getAllAttachmentSizeBytes(),
                MAX_LIVE_ATTACHMENT_BYTES,
            )
            require(existingBytes < MAX_LIVE_ATTACHMENT_BYTES) { TOTAL_ATTACHMENT_LIMIT_MESSAGE }
            var remainingTotalBytes = MAX_LIVE_ATTACHMENT_BYTES - existingBytes
            require(attachmentDirectory.isDirectory || attachmentDirectory.mkdirs()) {
                "Unable to create the private attachment directory."
            }

            val copiedAttachments = mutableListOf<CopiedAttachment>()
            val insertedAttachments = mutableListOf<AttachmentEntity>()
            try {
                uniqueUris.forEachIndexed { index, uri ->
                    currentCoroutineContext().ensureActive()
                    copiedAttachments += copyToPrivateStorage(
                        ownerType = ownerType,
                        ownerId = ownerId,
                        uri = uri,
                        copyAttemptId = normalizedAttemptId,
                        copyIndex = index,
                        remainingTotalBytes = remainingTotalBytes,
                    )
                    remainingTotalBytes -= copiedAttachments.last().entity.sizeBytes
                }

                // Revalidate after potentially slow provider reads so a deleted owner cannot
                // normally acquire attachments while the copy is in progress.
                currentCoroutineContext().ensureActive()
                requireActiveOwner(ownerType, ownerId)
                val insertedIds = dao.insertAttachmentsWithinLimit(
                    ownerType = ownerType.name,
                    ownerId = ownerId,
                    attachments = copiedAttachments.map(CopiedAttachment::entity),
                    maxAttachments = MAX_ATTACHMENTS_PER_OWNER,
                    maxTotalBytes = MAX_LIVE_ATTACHMENT_BYTES,
                )
                insertedAttachments += copiedAttachments.zip(insertedIds) { copied, id ->
                    copied.entity.copy(id = id)
                }
                insertedAttachments
            } catch (failure: Throwable) {
                withContext(NonCancellable) {
                    insertedAttachments.asReversed().forEach { attachment ->
                        runCatching { dao.deleteAttachment(attachment) }
                            .onFailure(failure::addSuppressed)
                    }
                    copiedAttachments.forEach { copied ->
                        if (copied.file.exists() && !copied.file.delete()) {
                            failure.addSuppressed(
                                IOException("Unable to remove incomplete attachment ${copied.file.name}."),
                            )
                        }
                    }
                }
                throw failure
            }
        }
    }

    suspend fun markAttachmentForDeletion(
        attachmentId: Long,
        undoWindowMillis: Long = DEFAULT_UNDO_WINDOW_MILLIS,
        nowMillis: Long = System.currentTimeMillis(),
    ): AttachmentDeletionToken? = copyMutex.withLock {
        require(attachmentId > 0) { "Attachment id must be positive." }
        require(undoWindowMillis >= 0) { "Undo window cannot be negative." }

        withContext(Dispatchers.IO) {
            val attachment = dao.getAttachment(attachmentId) ?: return@withContext null
            if (attachment.pendingDeleteAt != null) return@withContext null
            requireActiveOwner(attachment.ownerType, attachment.ownerId)
            val expiresAt = persistedDeadlineTimestamp(nowMillis, undoWindowMillis)
            val pendingDeleteAt = persistedDeadlineTimestamp(
                nowMillis,
                undoWindowMillis,
                attachment.createdAt,
            )
            val undoExpiresAtElapsedRealtime = persistedDeadlineTimestamp(
                elapsedRealtimeMillis(),
                undoWindowMillis,
            )
            if (dao.markAttachmentForDeletion(attachmentId, pendingDeleteAt) != 1) {
                return@withContext null
            }

            AttachmentDeletionToken(
                attachmentId = attachment.id,
                ownerType = attachment.ownerType,
                ownerId = attachment.ownerId,
                expiresAtMillis = expiresAt,
                pendingDeleteAtMillis = pendingDeleteAt,
                undoExpiresAtElapsedRealtime = undoExpiresAtElapsedRealtime,
            )
        }
    }

    suspend fun undoAttachmentDeletion(
        token: AttachmentDeletionToken,
        nowElapsedRealtimeMillis: Long = elapsedRealtimeMillis(),
    ): Boolean = copyMutex.withLock {
        withContext(Dispatchers.IO) {
            if (nowElapsedRealtimeMillis >= token.undoExpiresAtElapsedRealtime) {
                return@withContext false
            }
            val attachment = dao.getAttachment(token.attachmentId) ?: return@withContext false
            if (
                attachment.ownerType != token.ownerType ||
                attachment.ownerId != token.ownerId ||
                attachment.pendingDeleteAt != token.pendingDeleteAtMillis
            ) {
                return@withContext false
            }

            requireActiveOwner(token.ownerType, token.ownerId)
            val totalCount = dao.getAttachmentCount(token.ownerType.name, token.ownerId)
            require(totalCount <= MAX_ATTACHMENTS_PER_OWNER) {
                "This item already has $MAX_ATTACHMENTS_PER_OWNER attachments."
            }
            dao.undoAttachmentDeletion(token.attachmentId, token.pendingDeleteAtMillis) == 1
        }
    }

    suspend fun markOwnerAttachmentsForDeletion(
        ownerType: AttachmentOwnerType,
        ownerId: Long,
        pendingDeleteAt: Long,
    ) {
        require(ownerId > 0) { "Attachments require a persisted owner." }
        val creationFloor = dao.getMaxAttachmentCreatedAt(ownerType.name, listOf(ownerId))
        dao.markOwnerAttachmentsForDeletion(
            ownerType.name,
            ownerId,
            maxOf(pendingDeleteAt, creationFloor ?: Long.MIN_VALUE),
        )
    }

    suspend fun undoOwnerAttachmentDeletion(
        ownerType: AttachmentOwnerType,
        ownerId: Long,
    ) {
        require(ownerId > 0) { "Attachments require a persisted owner." }
        dao.undoOwnerAttachmentDeletion(ownerType.name, ownerId)
    }

    /**
     * Purges expired deletions, rows whose polymorphic owner no longer exists, and files left
     * behind by an interrupted copy. Existing callers therefore get orphan cleanup without a
     * second maintenance entry point.
     */
    suspend fun purgeReadyAttachments(now: Long): Int = copyMutex.withLock {
        withContext(Dispatchers.IO) {
            val candidates = (
                dao.getAttachmentsReadyForDeletion(now) + dao.getOrphanedAttachments()
                ).distinctBy(AttachmentEntity::id)
            var purgedRows = 0
            candidates.forEach { attachment ->
                if (purgeAttachment(attachment)) purgedRows += 1
            }
            purgeUntrackedFiles()
            purgedRows
        }
    }

    private suspend fun requireActiveOwner(ownerType: AttachmentOwnerType, ownerId: Long) {
        val exists = when (ownerType) {
            AttachmentOwnerType.TODO -> dao.activeTodoExists(ownerId)
            AttachmentOwnerType.LEDGER -> dao.activeLedgerEntryExists(ownerId)
        }
        require(exists) { "The attachment owner no longer exists." }
    }

    private suspend fun findCompletedCopyAttempt(
        ownerType: AttachmentOwnerType,
        ownerId: Long,
        copyAttemptId: String,
        expectedCount: Int,
    ): List<AttachmentEntity>? {
        val prefix = "$copyAttemptId-"
        val existing = dao.getAllOwnerAttachments(ownerType.name, ownerId)
            .filter { attachment -> File(attachment.privatePath).name.startsWith(prefix) }
        if (existing.isEmpty()) return null

        require(existing.size == expectedCount) {
            "The previous attachment copy did not finish consistently."
        }
        require(
            existing.all { attachment ->
                attachment.pendingDeleteAt == null &&
                    File(attachment.privatePath).let { file ->
                        file.isStrictlyInside(attachmentDirectory) && file.isFile
                    }
            },
        ) { "The previous attachment copy is no longer available." }
        return existing
    }

    private suspend fun purgeExpiredPendingAttachments(now: Long) {
        dao.getAttachmentsReadyForDeletion(now).forEach { attachment ->
            purgeAttachment(attachment)
        }
    }

    private suspend fun purgeAttachment(attachment: AttachmentEntity): Boolean {
        val file = File(attachment.privatePath)
        val isManaged = file.isStrictlyInside(attachmentDirectory)
        if (isManaged && file.exists() && (!file.isFile || !file.delete())) return false

        // Unsafe legacy/corrupt paths are removed from the database but are never touched on disk.
        dao.deleteAttachment(attachment)
        return true
    }

    private suspend fun purgeUntrackedFiles() {
        if (!attachmentDirectory.isDirectory) return
        val trackedFiles = dao.getAllAttachmentPrivatePaths()
            .map(::File)
            .filter { it.isStrictlyInside(attachmentDirectory) }
            .mapNotNull { runCatching { it.canonicalPath }.getOrNull() }
            .toHashSet()

        attachmentDirectory.listFiles()?.forEach { candidate ->
            val canonicalPath = runCatching { candidate.canonicalPath }.getOrNull() ?: return@forEach
            if (
                candidate.isStrictlyInside(attachmentDirectory) &&
                canonicalPath !in trackedFiles &&
                candidate.isFile
            ) {
                candidate.delete()
            }
        }
    }

    private suspend fun copyToPrivateStorage(
        ownerType: AttachmentOwnerType,
        ownerId: Long,
        uri: Uri,
        copyAttemptId: String,
        copyIndex: Int,
        remainingTotalBytes: Long,
    ): CopiedAttachment {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
            "Only content:// attachment URIs are supported."
        }

        // Metadata providers are external processes too; keep this phase owned by the same
        // cancellable copy Job as the subsequent stream transfer.
        val metadata = runInterruptible { readMetadata(uri) }
        currentCoroutineContext().ensureActive()
        if (metadata.declaredSize != null && metadata.declaredSize > MAX_FILE_SIZE_BYTES) {
            throw IOException("${metadata.originalName} exceeds the 25 MB attachment limit.")
        }
        if (metadata.declaredSize != null && metadata.declaredSize > remainingTotalBytes) {
            throw IOException(TOTAL_ATTACHMENT_LIMIT_MESSAGE)
        }

        val destination = File(
            attachmentDirectory,
            "$copyAttemptId-$copyIndex${safeExtension(metadata.originalName)}",
        )
        try {
            val copiedSize = runInterruptible {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destination).use { output ->
                        try {
                            copyAttachmentBytes(
                                input = input,
                                output = output,
                                maxFileBytes = MAX_FILE_SIZE_BYTES,
                                remainingTotalBytes = remainingTotalBytes,
                                bufferSize = COPY_BUFFER_SIZE,
                            )
                        } catch (_: AttachmentFileLimitExceededException) {
                            throw IOException("${metadata.originalName} exceeds the 25 MB attachment limit.")
                        } catch (_: AttachmentTotalLimitExceededException) {
                            throw IOException(TOTAL_ATTACHMENT_LIMIT_MESSAGE)
                        }
                    }
                } ?: throw IOException("Unable to open ${metadata.originalName}.")
            }
            currentCoroutineContext().ensureActive()

            return CopiedAttachment(
                entity = AttachmentEntity(
                    ownerType = ownerType,
                    ownerId = ownerId,
                    privatePath = destination.absolutePath,
                    originalName = metadata.originalName,
                    mimeType = metadata.mimeType,
                    sizeBytes = copiedSize,
                ),
                file = destination,
            )
        } catch (failure: Throwable) {
            if (destination.exists() && !destination.delete()) {
                failure.addSuppressed(
                    IOException("Unable to remove incomplete attachment ${destination.name}."),
                )
            }
            throw failure
        }
    }

    private fun readMetadata(uri: Uri): AttachmentMetadata {
        var originalName: String? = null
        var declaredSize: Long? = null
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) {
                    originalName = cursor.getString(nameColumn)?.takeIf(String::isNotBlank)
                }
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                    declaredSize = cursor.getLong(sizeColumn).takeIf { it >= 0 }
                }
            }
        }

        return AttachmentMetadata(
            originalName = originalName ?: DEFAULT_ATTACHMENT_NAME,
            mimeType = appContext.contentResolver.getType(uri) ?: DEFAULT_MIME_TYPE,
            declaredSize = declaredSize,
        )
    }

    private fun safeExtension(originalName: String): String {
        val separator = originalName.lastIndexOf('.')
        if (separator <= 0 || separator == originalName.lastIndex) return ""
        val extension = originalName.substring(separator + 1)
        return if (extension.length <= MAX_EXTENSION_LENGTH && extension.all(Char::isLetterOrDigit)) {
            ".$extension"
        } else {
            ""
        }
    }

    private data class AttachmentMetadata(
        val originalName: String,
        val mimeType: String,
        val declaredSize: Long?,
    )

    private data class CopiedAttachment(
        val entity: AttachmentEntity,
        val file: File,
    )

    companion object {
        const val MAX_ATTACHMENTS_PER_OWNER = 10
        const val MAX_FILE_SIZE_BYTES = 25L * 1024L * 1024L
        const val MAX_LIVE_ATTACHMENT_BYTES = 128L * 1024L * 1024L
        const val DEFAULT_UNDO_WINDOW_MILLIS = 6_000L
        const val ATTACHMENT_DIRECTORY = "attachments"
        const val TOTAL_ATTACHMENT_LIMIT_MESSAGE =
            "Attachments can use up to 128 MB in total. Remove one or more files and try again."

        private const val DEFAULT_ATTACHMENT_NAME = "attachment"
        private const val DEFAULT_MIME_TYPE = "application/octet-stream"
        private const val COPY_BUFFER_SIZE = 8 * 1024
        private const val MAX_EXTENSION_LENGTH = 16
    }
}

internal fun normalizeCopyAttemptId(value: String): String {
    val normalized = runCatching { UUID.fromString(value).toString() }
        .getOrElse { throw IllegalArgumentException("Attachment copy attempt id must be a UUID.", it) }
    require(normalized.equals(value, ignoreCase = true)) {
        "Attachment copy attempt id must use canonical UUID form."
    }
    return normalized
}
