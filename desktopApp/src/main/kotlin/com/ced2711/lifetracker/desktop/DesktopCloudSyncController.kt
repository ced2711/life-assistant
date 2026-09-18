package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.cloudsync.CloudRevision
import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.cloudsync.GoogleDriveBackupStore
import com.ced2711.lifetracker.cloudsync.NewCloudRevision
import com.ced2711.lifetracker.cloudsync.SyncDecision
import com.ced2711.lifetracker.cloudsync.cloudRevisionHeads
import com.ced2711.lifetracker.cloudsync.decideSyncAction
import com.ced2711.lifetracker.cloudsync.latestCloudRevision
import com.ced2711.lifetracker.cloudsync.prunableCloudRevisions
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DesktopCloudUiState(
    val connected: Boolean = false,
    val automaticSync: Boolean = true,
    val syncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val message: String? = null,
    val conflict: CloudRevision? = null,
)

class DesktopCloudSyncController(
    private val dataStore: DesktopDataStore,
    private val configStore: DesktopConfigStore,
    private val oauth: DesktopGoogleOAuth,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val drive = GoogleDriveBackupStore(oauth)
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<DesktopCloudUiState> = _state.asStateFlow()
    private var automaticJob: Job? = null

    fun start(scope: CoroutineScope) {
        automaticJob?.cancel()
        automaticJob = scope.launch {
            if (_state.value.connected && _state.value.automaticSync) synchronize()
            while (isActive) {
                delay(AUTOMATIC_INTERVAL_MILLIS)
                if (_state.value.connected && _state.value.automaticSync) synchronize()
            }
        }
    }

    suspend fun connect(clientId: String, clientSecret: CharArray) {
        if (_state.value.syncing) {
            clientSecret.fill('\u0000')
            return
        }
        _state.update { it.copy(syncing = true, message = null) }
        try {
            oauth.connect(clientId, clientSecret)
            // The consent may point at a different Google account. Keep the previous lineage until
            // consent succeeds, then force a safe first-sync comparison for the newly selected account.
            configStore.clearSyncState()
            _state.value = readState().copy(message = "Google Drive connected.")
            synchronize()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            _state.update { it.copy(syncing = false, message = error.safeMessage()) }
        } finally {
            clientSecret.fill('\u0000')
        }
    }

    suspend fun disconnect() {
        if (_state.value.syncing) return
        runCatching { oauth.disconnect() }
        _state.value = readState().copy(message = "Google Drive disconnected. Local data was kept.")
    }

    fun setAutomaticSync(enabled: Boolean) {
        configStore.setAutomaticSync(enabled)
        _state.update { it.copy(automaticSync = enabled) }
    }

    suspend fun synchronize(resolution: ConflictResolution? = null) {
        if (!_state.value.connected || _state.value.syncing) return
        val approvedConflictId = resolution?.let { _state.value.conflict?.fileId } ?: run {
            if (resolution != null) return
            null
        }
        _state.update { it.copy(syncing = true, message = null, conflict = null) }
        try {
            val config = configStore.read()
            val revisions = drive.listRevisions(100)
            val heads = cloudRevisionHeads(revisions)
            val remote = latestCloudRevision(revisions)
            if (resolution != null && remote?.fileId != approvedConflictId) {
                _state.value = readState().copy(
                    conflict = remote,
                    message = "Google Drive changed while the conflict choice was open. Review the refreshed conflict.",
                )
                return
            }
            val localFingerprint = dataStore.localFingerprint()
            val decision = decideSyncAction(
                localFingerprint = localFingerprint,
                localIsEmpty = dataStore.isUserDataEmpty(),
                state = config.syncState,
                revisions = revisions,
            )
            if (
                decision == SyncDecision.Conflict &&
                resolution != null &&
                !conflictingHeadsUseCurrentPassword(heads)
            ) {
                _state.value = readState().copy(
                    conflict = remote,
                    message = "The conflicting cloud history uses a different data password. No cloud data was changed.",
                )
                return
            }
            when {
                decision == SyncDecision.Conflict && resolution == null -> {
                    _state.value = readState().copy(
                        syncing = false,
                        conflict = requireNotNull(remote),
                        message = "This PC and Google Drive both changed.",
                    )
                }
                decision == SyncDecision.Conflict && resolution == ConflictResolution.KEEP_LOCAL ->
                    upload(remote, heads.map(CloudRevision::fileId))
                decision == SyncDecision.Conflict && resolution == ConflictResolution.USE_CLOUD -> {
                    val chosen = requireNotNull(remote)
                    if (download(chosen, localFingerprint)) {
                        upload(chosen, heads.map(CloudRevision::fileId))
                    }
                }
                decision == SyncDecision.Upload -> upload(remote, emptyList())
                decision == SyncDecision.Download -> download(requireNotNull(remote), localFingerprint)
                else -> _state.value = readState().copy(message = "Already up to date.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            _state.value = readState().copy(message = error.safeMessage())
        }
    }

    fun dismissConflict() {
        _state.update { it.copy(conflict = null) }
    }

    fun acknowledgeMessage() {
        _state.update { it.copy(message = null) }
    }

    private suspend fun upload(remote: CloudRevision?, mergedRevisionIds: List<String>) {
        val upload = dataStore.createUploadSnapshot()
        try {
            val config = configStore.read()
            val revision = drive.uploadRevision(
                upload.file,
                NewCloudRevision(
                    createdAt = now(),
                    deviceId = config.syncState.deviceId,
                    baseRevisionId = remote?.fileId,
                    contentFingerprint = upload.fingerprint,
                    mergedRevisionIds = mergedRevisionIds,
                ),
            )
            configStore.recordSync(revision.fileId, upload.fingerprint, now())
            val refreshed = drive.listRevisions(100)
            prunableCloudRevisions(refreshed, keepCount = 30).forEach { oldRevision ->
                drive.deleteRevision(oldRevision.fileId)
            }
            _state.value = readState().copy(message = "Encrypted backup uploaded.")
        } finally {
            upload.file.delete()
        }
    }

    private suspend fun download(remote: CloudRevision, expectedLocalFingerprint: String): Boolean {
        val temporary = File(dataStore.appDirectory, "cloud-download.tlb.part")
        try {
            drive.downloadRevision(remote, temporary)
            when (val result = dataStore.replaceFromEncrypted(temporary, expectedLocalFingerprint)) {
                is DesktopReplaceResult.Applied -> {
                    configStore.recordSync(remote.fileId, result.fingerprint, now())
                    _state.value = readState().copy(message = "Cloud changes restored.")
                    return true
                }
                DesktopReplaceResult.LocalChanged -> {
                    _state.value = readState().copy(
                        conflict = remote,
                        message = "Local data changed while the cloud copy was downloading.",
                    )
                    return false
                }
                DesktopReplaceResult.Invalid -> throw IllegalArgumentException(
                    "The cloud backup uses a different data password or is damaged.",
                )
            }
        } finally {
            temporary.delete()
        }
    }

    private suspend fun conflictingHeadsUseCurrentPassword(heads: List<CloudRevision>): Boolean {
        for (head in heads) {
            val temporary = File(dataStore.appDirectory, "cloud-validation-${UUID.randomUUID()}.tlb")
            try {
                drive.downloadRevision(head, temporary)
                if (!dataStore.verifyEncrypted(temporary)) return false
            } finally {
                temporary.delete()
            }
        }
        return true
    }

    private fun readState(): DesktopCloudUiState {
        val config = configStore.read()
        return DesktopCloudUiState(
            connected = oauth.isConnected(),
            automaticSync = config.automaticSync,
            lastSyncAt = config.syncState.lastSyncAt,
        )
    }

    private fun Throwable.safeMessage(): String = when (this) {
        is java.net.UnknownHostException -> "Google Drive could not be reached."
        is java.net.SocketTimeoutException -> "Google Drive timed out."
        is IllegalArgumentException -> message ?: "Cloud sync settings are invalid."
        else -> message?.takeIf { it.length in 1..180 } ?: "Google Drive sync failed."
    }

    private companion object {
        const val AUTOMATIC_INTERVAL_MILLIS = 15L * 60L * 1_000L
    }
}
