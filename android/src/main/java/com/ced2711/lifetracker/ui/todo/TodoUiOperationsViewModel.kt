package com.ced2711.lifetracker.ui.todo

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ced2711.lifetracker.data.attachment.AttachmentDeletionToken
import com.ced2711.lifetracker.domain.model.RecurringDeleteResult
import com.ced2711.lifetracker.ui.remainingUndoMillis
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


internal data class PendingTodoDelete(
    val title: String,
    val includedFuture: Boolean,
    val receipt: RecurringDeleteResult,
)

internal data class PendingTodoAttachmentDelete(
    val originalName: String,
    val token: AttachmentDeletionToken,
)

/** Retains in-flight UI operation receipts across activity recreation without persisting stale work. */
internal class TodoUiOperationsViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var savingEditorKey by mutableStateOf<String?>(null)
        private set
    var savedEditorKey by mutableStateOf<String?>(null)
        private set
    var pendingDeletes by mutableStateOf<List<PendingTodoDelete>>(emptyList())
        private set
    var pendingAttachmentDeletes by mutableStateOf<List<PendingTodoAttachmentDelete>>(emptyList())
        private set

    private var ownerSessionKey: String? = savedStateHandle[OWNER_SESSION_KEY]
    private var ownerRequestDigest: String? = savedStateHandle[OWNER_REQUEST_DIGEST_KEY]
    private var clientOperationToken: String? = savedStateHandle[CLIENT_OPERATION_TOKEN_KEY]
    private var savedOwnerId by mutableStateOf(savedStateHandle.get<Long>(OWNER_ID_KEY))
    private var saveTimeoutJob: Job? = null
    private var activeSaveJob: Job? = null
    private var nextSaveAttempt = 0L
    private var activeSaveAttempt: Long? = null
    private var activeSaveEditorKey: String? = null
    private var attachmentRequestEditorKey: String? = savedStateHandle[COPY_EDITOR_KEY]
    private var attachmentRequestDigest: String? = savedStateHandle[COPY_REQUEST_DIGEST_KEY]
    private var attachmentCopyAttemptId: String? = savedStateHandle[COPY_ATTEMPT_ID_KEY]
    private val deleteExpiryJobs = mutableMapOf<String, Job>()
    private val attachmentExpiryJobs = mutableMapOf<Long, Job>()
    private var elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime
    private var expiryScheduler: (Long, () -> Unit) -> Job = { delayMillis, onExpired ->
        viewModelScope.launch {
            delay(delayMillis)
            onExpired()
        }
    }

    internal constructor(
        savedStateHandle: SavedStateHandle,
        elapsedRealtimeMillis: () -> Long,
        expiryScheduler: (Long, () -> Unit) -> Job,
    ) : this(savedStateHandle) {
        this.elapsedRealtimeMillis = elapsedRealtimeMillis
        this.expiryScheduler = expiryScheduler
    }

    init {
        val completeOperationRecovery = ownerSessionKey != null &&
            ownerRequestDigest != null && clientOperationToken != null
        if (!completeOperationRecovery) clearOwnerRecovery()
        val completeCopyRecovery = attachmentRequestEditorKey != null &&
            attachmentRequestDigest != null && attachmentCopyAttemptId != null
        if (!completeCopyRecovery) clearAttachmentCopyRequest()
    }

    fun beginSave(editorKey: String): Long? {
        if (savingEditorKey != null) return null
        saveTimeoutJob?.cancel()
        val attempt = ++nextSaveAttempt
        activeSaveAttempt = attempt
        activeSaveEditorKey = editorKey
        savingEditorKey = editorKey
        savedEditorKey = null
        saveTimeoutJob = viewModelScope.launch {
            delay(SAVE_TIMEOUT_MILLIS)
            if (!isActive(editorKey, attempt)) return@launch

            // Invalidate callbacks first, then wait for the real I/O job to finish cancelling.
            // Retry stays disabled until the old copy releases AttachmentStore's mutex.
            activeSaveAttempt = null
            activeSaveJob?.cancelAndJoin()
            if (activeSaveEditorKey == editorKey && activeSaveAttempt == null) {
                activeSaveJob = null
                activeSaveEditorKey = null
                savingEditorKey = null
                saveTimeoutJob = null
            }
        }
        return attempt
    }

    fun trackSaveJob(editorKey: String, attempt: Long, job: Job) {
        if (!isActive(editorKey, attempt)) {
            job.cancel()
            return
        }
        activeSaveJob = job
    }

    fun attachmentCopyAttemptId(editorKey: String, uriStrings: List<String>): String {
        val requestDigest = digest(uriStrings.distinct().joinToString(separator = "\u0000"))
        if (
            attachmentRequestEditorKey == editorKey &&
            attachmentRequestDigest == requestDigest &&
            attachmentCopyAttemptId != null
        ) {
            return requireNotNull(attachmentCopyAttemptId)
        }
        clearAttachmentCopyRequest()
        return deterministicUuid(
            "todo-copy\u0000$editorKey\u0000${ownerRequestDigest.orEmpty()}\u0000$requestDigest",
        ).also { attemptId ->
            attachmentRequestEditorKey = editorKey
            attachmentRequestDigest = requestDigest
            attachmentCopyAttemptId = attemptId
            savedStateHandle[COPY_EDITOR_KEY] = editorKey
            savedStateHandle[COPY_REQUEST_DIGEST_KEY] = requestDigest
            savedStateHandle[COPY_ATTEMPT_ID_KEY] = attemptId
        }
    }

    fun hasSavedOwner(editorKey: String): Boolean =
        ownerSessionKey == editorKey && savedOwnerId != null

    fun clientOperationToken(editorKey: String, ownerRequest: String): String {
        val requestDigest = digest(ownerRequest)
        if (ownerSessionKey == editorKey && clientOperationToken != null) {
            updateOwnerRequestDigest(editorKey, requestDigest)
            return requireNotNull(clientOperationToken)
        }
        clearOwnerRecovery()
        clearAttachmentCopyRequest()
        return deterministicUuid("todo-owner\u0000$editorKey").also { token ->
            ownerSessionKey = editorKey
            ownerRequestDigest = requestDigest
            clientOperationToken = token
            savedStateHandle[OWNER_SESSION_KEY] = editorKey
            savedStateHandle[OWNER_REQUEST_DIGEST_KEY] = requestDigest
            savedStateHandle[CLIENT_OPERATION_TOKEN_KEY] = token
        }
    }

    fun savedOwnerId(editorKey: String, ownerRequest: String): Long? {
        if (ownerSessionKey != editorKey) return null
        updateOwnerRequestDigest(editorKey, digest(ownerRequest))
        return savedOwnerId
    }

    fun markOwnerSaved(editorKey: String, attempt: Long, ownerId: Long, ownerRequest: String) {
        if (!isActive(editorKey, attempt)) return
        clientOperationToken(editorKey, ownerRequest)
        savedOwnerId = ownerId
        savedStateHandle[OWNER_ID_KEY] = ownerId
    }

    fun rejectSavedOwner(editorKey: String, ownerId: Long) {
        if (ownerSessionKey != editorKey || savedOwnerId != ownerId) return
        clearOwnerRecovery()
        clearAttachmentCopyRequest(editorKey)
    }

    fun markSaveSucceeded(editorKey: String, attempt: Long) {
        if (!isActive(editorKey, attempt)) return
        saveTimeoutJob?.cancel()
        saveTimeoutJob = null
        activeSaveJob = null
        activeSaveAttempt = null
        activeSaveEditorKey = null
        savingEditorKey = null
        clearOwnerRecovery()
        clearAttachmentCopyRequest(editorKey)
        savedEditorKey = editorKey
    }

    fun markSaveFailed(editorKey: String, attempt: Long) {
        if (!isActive(editorKey, attempt)) return
        saveTimeoutJob?.cancel()
        saveTimeoutJob = null
        activeSaveJob = null
        activeSaveAttempt = null
        activeSaveEditorKey = null
        savingEditorKey = null
    }

    fun consumeSaved(editorKey: String) {
        if (savedEditorKey == editorKey) savedEditorKey = null
    }

    fun abandonEditor(editorKey: String?) {
        if (editorKey == null) return
        if (savingEditorKey == editorKey) {
            saveTimeoutJob?.cancel()
            saveTimeoutJob = null
            activeSaveJob?.cancel()
            activeSaveJob = null
            savingEditorKey = null
        }
        if (activeSaveEditorKey == editorKey) {
            activeSaveAttempt = null
            activeSaveEditorKey = null
        }
        if (savedEditorKey == editorKey) savedEditorKey = null
        if (ownerSessionKey == editorKey) {
            clearOwnerRecovery()
        }
        clearAttachmentCopyRequest(editorKey)
    }

    fun publishDelete(
        item: PendingTodoDelete,
        nowElapsedRealtimeMillis: Long = elapsedRealtimeMillis(),
    ) {
        reconcileUndoWindows(nowElapsedRealtimeMillis)
        val key = deleteKey(item)
        val remaining = remainingUndoMillis(
            item.receipt.undoExpiresAtElapsedRealtime,
            nowElapsedRealtimeMillis,
        )
        if (remaining == 0L) return
        pendingDeletes = pendingDeletes.filterNot { deleteKey(it) == key } + item
        scheduleDeleteExpiry(item, remaining)
    }

    fun consumeDelete(item: PendingTodoDelete) {
        reconcileUndoWindows()
        pendingDeletes = pendingDeletes.filterNot { it == item }
        deleteExpiryJobs.remove(deleteKey(item))?.cancel()
    }

    fun publishAttachmentDelete(
        item: PendingTodoAttachmentDelete,
        nowElapsedRealtimeMillis: Long = elapsedRealtimeMillis(),
    ) {
        reconcileUndoWindows(nowElapsedRealtimeMillis)
        val token = item.token
        val remaining = remainingUndoMillis(
            token.undoExpiresAtElapsedRealtime,
            nowElapsedRealtimeMillis,
        )
        if (remaining == 0L) return
        pendingAttachmentDeletes =
            pendingAttachmentDeletes.filterNot { it.token.attachmentId == token.attachmentId } + item
        scheduleAttachmentExpiry(item, remaining)
    }

    fun consumeAttachmentDelete(item: PendingTodoAttachmentDelete) {
        reconcileUndoWindows()
        pendingAttachmentDeletes = pendingAttachmentDeletes.filterNot { it == item }
        attachmentExpiryJobs.remove(item.token.attachmentId)?.cancel()
    }

    fun reconcileUndoWindows(nowElapsedRealtimeMillis: Long = elapsedRealtimeMillis()) {
        deleteExpiryJobs.values.forEach(Job::cancel)
        deleteExpiryJobs.clear()
        pendingDeletes = pendingDeletes.filter { item ->
            remainingUndoMillis(
                item.receipt.undoExpiresAtElapsedRealtime,
                nowElapsedRealtimeMillis,
            ) > 0L
        }
        pendingDeletes.forEach { item ->
            scheduleDeleteExpiry(
                item,
                remainingUndoMillis(
                    item.receipt.undoExpiresAtElapsedRealtime,
                    nowElapsedRealtimeMillis,
                ),
            )
        }

        attachmentExpiryJobs.values.forEach(Job::cancel)
        attachmentExpiryJobs.clear()
        pendingAttachmentDeletes = pendingAttachmentDeletes.filter { item ->
            remainingUndoMillis(
                item.token.undoExpiresAtElapsedRealtime,
                nowElapsedRealtimeMillis,
            ) > 0L
        }
        pendingAttachmentDeletes.forEach { item ->
            scheduleAttachmentExpiry(
                item,
                remainingUndoMillis(
                    item.token.undoExpiresAtElapsedRealtime,
                    nowElapsedRealtimeMillis,
                ),
            )
        }
    }

    private fun scheduleDeleteExpiry(item: PendingTodoDelete, remainingMillis: Long) {
        val key = deleteKey(item)
        deleteExpiryJobs.remove(key)?.cancel()
        deleteExpiryJobs[key] = expiryScheduler(remainingMillis) {
            deleteExpiryJobs.remove(key)
            pendingDeletes = pendingDeletes.filterNot { deleteKey(it) == key }
        }
    }

    private fun scheduleAttachmentExpiry(
        item: PendingTodoAttachmentDelete,
        remainingMillis: Long,
    ) {
        val attachmentId = item.token.attachmentId
        attachmentExpiryJobs.remove(attachmentId)?.cancel()
        attachmentExpiryJobs[attachmentId] = expiryScheduler(remainingMillis) {
            attachmentExpiryJobs.remove(attachmentId)
            pendingAttachmentDeletes = pendingAttachmentDeletes.filterNot {
                it.token.attachmentId == attachmentId
            }
        }
    }

    private fun isActive(editorKey: String, attempt: Long): Boolean =
        activeSaveEditorKey == editorKey && activeSaveAttempt == attempt

    private fun clearAttachmentCopyRequest(editorKey: String) {
        if (attachmentRequestEditorKey != editorKey) return
        clearAttachmentCopyRequest()
    }

    private fun clearAttachmentCopyRequest() {
        attachmentRequestEditorKey = null
        attachmentRequestDigest = null
        attachmentCopyAttemptId = null
        savedStateHandle.remove<String>(COPY_EDITOR_KEY)
        savedStateHandle.remove<String>(COPY_REQUEST_DIGEST_KEY)
        savedStateHandle.remove<String>(COPY_ATTEMPT_ID_KEY)
    }

    private fun clearOwnerRecovery() {
        ownerSessionKey = null
        ownerRequestDigest = null
        clientOperationToken = null
        savedOwnerId = null
        savedStateHandle.remove<String>(OWNER_SESSION_KEY)
        savedStateHandle.remove<String>(OWNER_REQUEST_DIGEST_KEY)
        savedStateHandle.remove<String>(CLIENT_OPERATION_TOKEN_KEY)
        savedStateHandle.remove<Long>(OWNER_ID_KEY)
    }

    private fun updateOwnerRequestDigest(editorKey: String, requestDigest: String) {
        if (ownerSessionKey != editorKey || ownerRequestDigest == requestDigest) return
        ownerRequestDigest = requestDigest
        savedStateHandle[OWNER_REQUEST_DIGEST_KEY] = requestDigest
        clearAttachmentCopyRequest(editorKey)
    }

    private fun deleteKey(item: PendingTodoDelete): String =
        "${item.receipt.itemId}:${item.receipt.deletedAt}"

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun deterministicUuid(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

    override fun onCleared() {
        activeSaveJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val SAVE_TIMEOUT_MILLIS = 45_000L
        const val OWNER_SESSION_KEY = "todo_save_owner_session"
        const val OWNER_REQUEST_DIGEST_KEY = "todo_save_owner_request"
        const val CLIENT_OPERATION_TOKEN_KEY = "todo_save_client_operation_token"
        const val OWNER_ID_KEY = "todo_save_owner_id"
        const val COPY_EDITOR_KEY = "todo_attachment_copy_editor"
        const val COPY_REQUEST_DIGEST_KEY = "todo_attachment_copy_request"
        const val COPY_ATTEMPT_ID_KEY = "todo_attachment_copy_attempt"
    }
}
