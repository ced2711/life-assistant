package com.ced2711.lifetracker.ui.ledger

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ced2711.lifetracker.data.attachment.AttachmentDeletionToken
import com.ced2711.lifetracker.domain.model.RecurringDeleteResult
import com.ced2711.lifetracker.ui.remainingUndoMillis
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val QUICK_LEDGER_SESSION_KEY = "ledger_quick_entry"

internal data class PendingLedgerDelete(
    val title: String,
    val receipt: RecurringDeleteResult,
)

internal data class PendingLedgerAttachmentDelete(
    val originalName: String,
    val token: AttachmentDeletionToken,
)

/**
 * Retains Ledger operation identity and receipts across activity recreation.
 *
 * Database and attachment callbacks may outlive the composition that started them. Keeping the
 * active attempt here prevents a rotated editor from re-enabling Save and lets the replacement
 * composition consume success, failure, and Undo receipts exactly once.
 */
internal class LedgerUiOperationsViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var savingSessionKey by mutableStateOf<String?>(null)
        private set
    var savedSessionKeys by mutableStateOf<Set<String>>(emptySet())
        private set
    var pendingDeletes by mutableStateOf<List<PendingLedgerDelete>>(emptyList())
        private set
    var pendingAttachmentDeletes by mutableStateOf<List<PendingLedgerAttachmentDelete>>(emptyList())
        private set
    var deletingEntryIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    private var nextSaveAttempt = 0L
    private var activeSaveAttempt: Long? = null
    private var ownerSessionKey: String? = savedStateHandle[OWNER_SESSION_KEY]
    private var ownerRequestDigest: String? = savedStateHandle[OWNER_REQUEST_DIGEST_KEY]
    private var clientOperationToken: String? = savedStateHandle[CLIENT_OPERATION_TOKEN_KEY]
    private var savedOwnerId by mutableStateOf(savedStateHandle.get<Long>(OWNER_ID_KEY))
    private var failureSessionKey: String? = null
    private var failureMessage: String? = null
    private var attachmentRequestSessionKey: String? = savedStateHandle[COPY_SESSION_KEY]
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

    fun beginSave(sessionKey: String): Long? {
        if (savingSessionKey != null) return null
        val attempt = ++nextSaveAttempt
        activeSaveAttempt = attempt
        savingSessionKey = sessionKey
        savedSessionKeys = savedSessionKeys - sessionKey
        if (failureSessionKey == sessionKey) {
            failureSessionKey = null
            failureMessage = null
        }
        return attempt
    }

    init {
        val completeOperationRecovery = ownerSessionKey != null &&
            ownerRequestDigest != null && clientOperationToken != null
        if (!completeOperationRecovery) clearOwnerRecovery()
        val completeCopyRecovery = attachmentRequestSessionKey != null &&
            attachmentRequestDigest != null && attachmentCopyAttemptId != null
        if (!completeCopyRecovery) clearAttachmentCopyRequest()
    }

    fun hasSavedOwner(sessionKey: String): Boolean =
        ownerSessionKey == sessionKey && savedOwnerId != null

    fun clientOperationToken(sessionKey: String, ownerRequest: String): String {
        val requestDigest = digest(ownerRequest)
        if (ownerSessionKey == sessionKey && clientOperationToken != null) {
            updateOwnerRequestDigest(sessionKey, requestDigest)
            return requireNotNull(clientOperationToken)
        }
        clearOwnerRecovery()
        clearAttachmentCopyRequest()
        return deterministicUuid("ledger-owner\u0000$sessionKey").also { token ->
            ownerSessionKey = sessionKey
            ownerRequestDigest = requestDigest
            clientOperationToken = token
            savedStateHandle[OWNER_SESSION_KEY] = sessionKey
            savedStateHandle[OWNER_REQUEST_DIGEST_KEY] = requestDigest
            savedStateHandle[CLIENT_OPERATION_TOKEN_KEY] = token
        }
    }

    fun savedOwnerId(sessionKey: String, ownerRequest: String): Long? {
        if (ownerSessionKey != sessionKey) return null
        updateOwnerRequestDigest(sessionKey, digest(ownerRequest))
        return savedOwnerId
    }

    fun markOwnerSaved(sessionKey: String, attempt: Long, ownerId: Long, ownerRequest: String) {
        if (!isActive(sessionKey, attempt)) return
        clientOperationToken(sessionKey, ownerRequest)
        savedOwnerId = ownerId
        savedStateHandle[OWNER_ID_KEY] = ownerId
    }

    fun rejectSavedOwner(sessionKey: String, ownerId: Long) {
        if (ownerSessionKey != sessionKey || savedOwnerId != ownerId) return
        clearOwnerRecovery()
        clearAttachmentCopyRequest(sessionKey)
    }

    fun markSaveSucceeded(sessionKey: String, attempt: Long) {
        if (!isActive(sessionKey, attempt)) return
        activeSaveAttempt = null
        savingSessionKey = null
        clearOwnerRecovery()
        clearAttachmentCopyRequest(sessionKey)
        failureSessionKey = null
        failureMessage = null
        savedSessionKeys = savedSessionKeys + sessionKey
    }

    fun markSaveFailed(sessionKey: String, attempt: Long, message: String) {
        if (!isActive(sessionKey, attempt)) return
        activeSaveAttempt = null
        savingSessionKey = null
        failureSessionKey = sessionKey
        failureMessage = message
        // Keep a successfully persisted owner so attachment-copy retry cannot insert it again.
    }

    fun failureFor(sessionKey: String): String? =
        failureMessage.takeIf { failureSessionKey == sessionKey }

    fun attachmentCopyAttemptId(sessionKey: String, uriStrings: List<String>): String {
        val requestDigest = digest(uriStrings.distinct().joinToString(separator = "\u0000"))
        if (
            attachmentRequestSessionKey == sessionKey &&
            attachmentRequestDigest == requestDigest &&
            attachmentCopyAttemptId != null
        ) {
            return requireNotNull(attachmentCopyAttemptId)
        }
        clearAttachmentCopyRequest()
        return deterministicUuid(
            "ledger-copy\u0000$sessionKey\u0000${ownerRequestDigest.orEmpty()}\u0000$requestDigest",
        ).also { attemptId ->
            attachmentRequestSessionKey = sessionKey
            attachmentRequestDigest = requestDigest
            attachmentCopyAttemptId = attemptId
            savedStateHandle[COPY_SESSION_KEY] = sessionKey
            savedStateHandle[COPY_REQUEST_DIGEST_KEY] = requestDigest
            savedStateHandle[COPY_ATTEMPT_ID_KEY] = attemptId
        }
    }

    fun consumeSaved(sessionKey: String) {
        savedSessionKeys = savedSessionKeys - sessionKey
    }

    fun abandonSession(sessionKey: String?) {
        if (sessionKey == null || savingSessionKey == sessionKey) return
        savedSessionKeys = savedSessionKeys - sessionKey
        if (ownerSessionKey == sessionKey) {
            clearOwnerRecovery()
        }
        if (failureSessionKey == sessionKey) {
            failureSessionKey = null
            failureMessage = null
        }
        clearAttachmentCopyRequest(sessionKey)
    }

    fun beginDelete(entryId: Long): Boolean {
        if (entryId in deletingEntryIds) return false
        deletingEntryIds = deletingEntryIds + entryId
        return true
    }

    fun publishDelete(
        entryId: Long,
        item: PendingLedgerDelete,
        nowElapsedRealtimeMillis: Long = elapsedRealtimeMillis(),
    ) {
        deletingEntryIds = deletingEntryIds - entryId
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

    fun markDeleteFailed(entryId: Long) {
        deletingEntryIds = deletingEntryIds - entryId
    }

    fun consumeDelete(item: PendingLedgerDelete) {
        reconcileUndoWindows()
        pendingDeletes = pendingDeletes.filterNot { it == item }
        deleteExpiryJobs.remove(deleteKey(item))?.cancel()
    }

    fun publishAttachmentDelete(
        item: PendingLedgerAttachmentDelete,
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

    fun consumeAttachmentDelete(item: PendingLedgerAttachmentDelete) {
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

    private fun scheduleDeleteExpiry(item: PendingLedgerDelete, remainingMillis: Long) {
        val key = deleteKey(item)
        deleteExpiryJobs.remove(key)?.cancel()
        deleteExpiryJobs[key] = expiryScheduler(remainingMillis) {
            deleteExpiryJobs.remove(key)
            pendingDeletes = pendingDeletes.filterNot { deleteKey(it) == key }
        }
    }

    private fun scheduleAttachmentExpiry(
        item: PendingLedgerAttachmentDelete,
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

    private fun isActive(sessionKey: String, attempt: Long): Boolean =
        savingSessionKey == sessionKey && activeSaveAttempt == attempt

    private fun clearAttachmentCopyRequest(sessionKey: String) {
        if (attachmentRequestSessionKey != sessionKey) return
        clearAttachmentCopyRequest()
    }

    private fun clearAttachmentCopyRequest() {
        attachmentRequestSessionKey = null
        attachmentRequestDigest = null
        attachmentCopyAttemptId = null
        savedStateHandle.remove<String>(COPY_SESSION_KEY)
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

    private fun updateOwnerRequestDigest(sessionKey: String, requestDigest: String) {
        if (ownerSessionKey != sessionKey || ownerRequestDigest == requestDigest) return
        ownerRequestDigest = requestDigest
        savedStateHandle[OWNER_REQUEST_DIGEST_KEY] = requestDigest
        clearAttachmentCopyRequest(sessionKey)
    }

    private fun deleteKey(item: PendingLedgerDelete): String =
        "${item.receipt.itemId}:${item.receipt.deletedAt}"

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun deterministicUuid(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

    private companion object {
        const val OWNER_SESSION_KEY = "ledger_save_owner_session"
        const val OWNER_REQUEST_DIGEST_KEY = "ledger_save_owner_request"
        const val CLIENT_OPERATION_TOKEN_KEY = "ledger_save_client_operation_token"
        const val OWNER_ID_KEY = "ledger_save_owner_id"
        const val COPY_SESSION_KEY = "ledger_attachment_copy_session"
        const val COPY_REQUEST_DIGEST_KEY = "ledger_attachment_copy_request"
        const val COPY_ATTEMPT_ID_KEY = "ledger_attachment_copy_attempt"
    }
}
