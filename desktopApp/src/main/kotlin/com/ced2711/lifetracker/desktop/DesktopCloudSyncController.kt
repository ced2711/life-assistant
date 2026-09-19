package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.cloudsync.CloudBackupStore
import com.ced2711.lifetracker.cloudsync.CloudRevision
import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.cloudsync.GoogleDriveBackupStore
import com.ced2711.lifetracker.cloudsync.NewCloudRevision
import com.ced2711.lifetracker.cloudsync.SyncDecision
import com.ced2711.lifetracker.cloudsync.cloudRevisionHeadIds
import com.ced2711.lifetracker.cloudsync.cloudRevisionHeads
import com.ced2711.lifetracker.cloudsync.decideSyncAction
import com.ced2711.lifetracker.cloudsync.latestCloudRevision
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
    val automaticSync: Boolean = false,
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
    cloudStore: CloudBackupStore? = null,
    private val connectionStatus: () -> Boolean = oauth::isConnected,
) {
    private val drive = cloudStore ?: GoogleDriveBackupStore(oauth)
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
            _state.update { it.copy(syncing = false) }
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
        val previousConflict = _state.value.conflict
        val approvedConflictId = resolution?.let { _state.value.conflict?.fileId } ?: run {
            if (resolution != null) return
            null
        }
        _state.update { it.copy(syncing = true, message = null, conflict = null) }
        try {
            val config = configStore.read()
            val revisions = drive.listRevisions(100)
            val heads = cloudRevisionHeads(revisions)
            val observedHeadIds = cloudRevisionHeadIds(revisions)
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
                    upload(remote, heads.map(CloudRevision::fileId), observedHeadIds)
                decision == SyncDecision.Conflict && resolution == ConflictResolution.USE_CLOUD -> {
                    val chosen = requireNotNull(remote)
                    useCloud(chosen, observedHeadIds, localFingerprint)
                }
                decision == SyncDecision.Upload -> upload(remote, emptyList(), observedHeadIds)
                decision == SyncDecision.Download -> download(requireNotNull(remote), localFingerprint)
                else -> _state.value = readState().copy(message = "Already up to date.")
            }
        } catch (cancelled: CancellationException) {
            _state.value = readState().copy(conflict = previousConflict)
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

    private suspend fun upload(
        remote: CloudRevision?,
        mergedRevisionIds: List<String>,
        expectedHeadIds: Set<String>,
    ) {
        val upload = dataStore.createUploadSnapshot()
        try {
            uploadFile(
                source = upload.file,
                fingerprint = upload.fingerprint,
                remote = remote,
                mergedRevisionIds = mergedRevisionIds,
                expectedHeadIds = expectedHeadIds,
                successMessage = "Encrypted backup uploaded.",
            )
        } finally {
            upload.file.delete()
        }
    }

    /**
     * Resolve USE_CLOUD without replacing local data until the new cloud lineage is settled.
     * The selected remote bytes remain temporary throughout download, validation, upload, and
     * postflight verification. A conditional replacement then protects any local edit made while
     * the cloud operation was in flight.
     */
    private suspend fun useCloud(
        remote: CloudRevision,
        expectedHeadIds: Set<String>,
        expectedLocalFingerprint: String,
    ) {
        val temporary = File(dataStore.appDirectory, "cloud-use-${UUID.randomUUID()}.tlb.part")
        try {
            drive.downloadRevision(remote, temporary)
            if (!dataStore.verifyEncrypted(temporary)) {
                throw IllegalArgumentException("The cloud backup uses a different data password or is damaged.")
            }
            val uploaded = uploadFile(
                    source = temporary,
                    fingerprint = remote.contentFingerprint,
                    remote = remote,
                    mergedRevisionIds = expectedHeadIds.toList(),
                    expectedHeadIds = expectedHeadIds,
                    successMessage = null,
                    recordBaseline = false,
                ) ?: return

            if (dataStore.captureCloudRecovery(expectedLocalFingerprint) == null) {
                _state.value = readState().copy(
                    conflict = uploaded,
                    message = "Local data changed before the cloud choice could be applied. Review the conflict.",
                )
                return
            }
            when (val result = dataStore.replaceFromEncrypted(temporary, expectedLocalFingerprint)) {
                is DesktopReplaceResult.Applied -> {
                    configStore.recordSync(uploaded.fileId, result.fingerprint, now())
                    _state.value = readState().copy(message = "Cloud changes restored.")
                }
                DesktopReplaceResult.LocalChanged -> {
                    _state.value = readState().copy(
                        conflict = uploaded,
                        message = "Local data changed before the cloud choice could be applied. Review the conflict.",
                    )
                }
                DesktopReplaceResult.Invalid -> throw IllegalArgumentException(
                    "The cloud backup uses a different data password or is damaged.",
                )
            }
        } finally {
            temporary.delete()
        }
    }

    /** Upload one immutable revision and record a baseline only after postflight succeeds. */
    private suspend fun uploadFile(
        source: File,
        fingerprint: String,
        remote: CloudRevision?,
        mergedRevisionIds: List<String>,
        expectedHeadIds: Set<String>,
        successMessage: String?,
        recordBaseline: Boolean = true,
    ): CloudRevision? {
        val config = configStore.read()
        val preflightRevisions = drive.listRevisions(100)
        if (cloudRevisionHeadIds(preflightRevisions) != expectedHeadIds) {
            _state.value = readState().copy(
                conflict = latestCloudRevision(preflightRevisions) ?: remote,
                message = "Google Drive changed before upload. Review the refreshed conflict.",
            )
            return null
        }
        val revision = drive.uploadRevision(
            source,
            NewCloudRevision(
                createdAt = now(),
                deviceId = config.syncState.deviceId,
                baseRevisionId = remote?.fileId,
                contentFingerprint = fingerprint,
                mergedRevisionIds = mergedRevisionIds,
            ),
        )
        val refreshed = drive.listRevisions(100)
        if (cloudRevisionHeadIds(refreshed) != setOf(revision.fileId)) {
            _state.value = readState().copy(
                conflict = latestCloudRevision(refreshed) ?: revision,
                message = "Another device uploaded at the same time. Both encrypted versions were kept; review the conflict.",
            )
            return null
        }
        if (recordBaseline) configStore.recordSync(revision.fileId, fingerprint, now())
        if (successMessage != null) {
            _state.value = readState().copy(message = successMessage)
        }
        return revision
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
            connected = connectionStatus(),
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
