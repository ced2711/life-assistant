package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.cloudsync.CloudBackupStore
import com.ced2711.lifetracker.cloudsync.CloudRevision
import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.cloudsync.CloudAuthorizationException
import com.ced2711.lifetracker.cloudsync.GitHubBackupStore
import com.ced2711.lifetracker.cloudsync.GitHubDeviceAuthorization
import com.ced2711.lifetracker.cloudsync.GitHubDeviceCode
import com.ced2711.lifetracker.cloudsync.GitHubRepository
import com.ced2711.lifetracker.cloudsync.GoogleDriveBackupStore
import com.ced2711.lifetracker.cloudsync.NewCloudRevision
import com.ced2711.lifetracker.cloudsync.SyncDecision
import com.ced2711.lifetracker.cloudsync.cloudRevisionHeadIds
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
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
    val provider: DesktopCloudProvider = DesktopCloudProvider.GOOGLE_DRIVE,
    val gitHubRepository: String = "",
    // Shown while the user approves this PC on GitHub.
    val gitHubCode: GitHubDeviceCode? = null,
    // Local data differs from what was last synced.
    val pendingChanges: Boolean = false,
    val lastSyncFailed: Boolean = false,
)

class DesktopCloudSyncController(
    private val dataStore: DesktopDataStore,
    private val configStore: DesktopConfigStore,
    private val oauth: DesktopGoogleOAuth,
    private val now: () -> Long = System::currentTimeMillis,
    cloudStore: CloudBackupStore? = null,
    private val credentials: DesktopCredentialStore? = null,
    private val connectionStatus: (() -> Boolean)? = null,
    private val editDebounceMillis: Long = EDIT_DEBOUNCE_MILLIS,
    private val pollIntervalMillis: Long = AUTOMATIC_INTERVAL_MILLIS,
    // Off only in tests of the manual keep-local / use-cloud choice.
    private val automaticMerge: Boolean = true,
) {
    private val injectedStore = cloudStore
    private val googleDrive by lazy { GoogleDriveBackupStore(oauth) }
    private var appScope: CoroutineScope? = null

    // The provider is read per operation so switching providers never mixes histories.
    private val drive: CloudBackupStore
        get() = injectedStore ?: configStore.read().let { config ->
            when (config.provider) {
                DesktopCloudProvider.GOOGLE_DRIVE -> googleDrive
                DesktopCloudProvider.GITHUB -> GitHubBackupStore(
                    tokenProvider = { gitHubToken() },
                    repository = GitHubRepository.parse(config.gitHubRepository),
                )
            }
        }

    private fun gitHubToken(): String {
        val token = credentials?.load(DesktopCredentialStore.GITHUB_TOKEN)
            ?: throw CloudAuthorizationException("GitHub is not connected. Reconnect GitHub.")
        return try { token.concatToString() } finally { token.fill('\u0000') }
    }

    private fun isConnected(): Boolean = connectionStatus?.invoke() ?: when (configStore.read().provider) {
        DesktopCloudProvider.GOOGLE_DRIVE -> oauth.isConnected()
        DesktopCloudProvider.GITHUB -> credentials?.exists(DesktopCredentialStore.GITHUB_TOKEN) == true &&
            configStore.read().gitHubRepository.isNotBlank()
    }

    /**
     * Runs sign-in and sync in the app-wide scope, so leaving the Settings page no longer cancels
     * a browser sign-in or an upload halfway through.
     */
    fun launch(block: suspend DesktopCloudSyncController.() -> Unit) {
        val scope = appScope ?: return
        scope.launch { block() }
    }
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<DesktopCloudUiState> = _state.asStateFlow()
    private var automaticJob: Job? = null
    @Volatile private var lastAutomaticAttemptAt = 0L

    fun start(scope: CoroutineScope) {
        appScope = scope
        automaticJob?.cancel()
        automaticJob = scope.launch {
            launch { refreshPendingChanges() }
            // Periodic check so changes from the other device arrive while this app is open.
            launch {
                while (isActive) {
                    syncWhenIdle()
                    delay(pollIntervalMillis)
                }
            }
            // Sync shortly after local edits. The sync itself runs outside collectLatest, so a
            // download that changes local data cannot cancel the sync that caused it.
            dataStore.state
                .map { (it as? DesktopStoreState.Open)?.snapshot }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest {
                    refreshPendingChanges()
                    delay(editDebounceMillis)
                    scope.launch { syncWhenIdle() }
                }
        }
    }

    /** Called when the window regains focus: catch up, but not more than twice a minute. */
    fun onWindowFocused() {
        val scope = appScope ?: return
        if (now() - lastAutomaticAttemptAt < FOCUS_SYNC_MIN_INTERVAL_MILLIS) return
        scope.launch { syncWhenIdle() }
    }

    /** Runs an automatic sync, waiting for a sync already in progress instead of skipping. */
    private suspend fun syncWhenIdle() {
        if (!_state.value.connected || !_state.value.automaticSync) return
        while (_state.value.syncing) delay(500)
        lastAutomaticAttemptAt = now()
        synchronize()
    }

    suspend fun connect(clientId: String, clientSecret: CharArray) {
        if (_state.value.syncing) {
            clientSecret.fill('\u0000')
            return
        }
        _state.update { it.copy(syncing = true, message = null) }
        try {
            oauth.connect(clientId, clientSecret)
            configStore.setProvider(DesktopCloudProvider.GOOGLE_DRIVE)
            configStore.setAutomaticSync(true)
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

    private var gitHubJob: Job? = null

    /** Device-flow sign-in; the code to enter at GitHub is published in [state] meanwhile. */
    suspend fun connectGitHub(clientId: String, repository: String) {
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true, message = null) }
        try {
            val authorization = GitHubDeviceAuthorization(clientId.trim())
            val code = authorization.start()
            _state.update { it.copy(gitHubCode = code) }
            val token = authorization.awaitToken(code)
            _state.update { it.copy(gitHubCode = null) }
            val resolved = authorization.resolveRepository(token.accessToken, repository)
            GitHubBackupStore({ token.accessToken }, resolved).listRevisions()
            requireNotNull(credentials) { "Secure credential storage is unavailable." }
                .save(DesktopCredentialStore.GITHUB_TOKEN, token.accessToken.toCharArray())
            val sameRepository = configStore.read().let {
                it.provider == DesktopCloudProvider.GITHUB && it.gitHubRepository == resolved.fullName
            }
            configStore.setProvider(DesktopCloudProvider.GITHUB, clientId.trim(), resolved.fullName)
            configStore.setAutomaticSync(true)
            // Reconnecting to the same repository keeps the sync baseline, so no false conflict.
            if (!sameRepository) configStore.clearSyncState()
            _state.value = readState().copy(message = "GitHub connected.")
            synchronize()
        } catch (cancelled: CancellationException) {
            _state.value = readState().copy(message = "GitHub connection cancelled.")
            throw cancelled
        } catch (error: Throwable) {
            _state.value = readState().copy(message = error.safeMessage())
        }
    }

    fun startGitHubConnect(clientId: String, repository: String) {
        val scope = appScope ?: return
        gitHubJob?.cancel()
        gitHubJob = scope.launch { connectGitHub(clientId, repository) }
    }

    fun cancelGitHubConnect() {
        gitHubJob?.cancel()
    }

    suspend fun disconnect() {
        if (_state.value.syncing) return
        val provider = configStore.read().provider
        if (provider == DesktopCloudProvider.GITHUB) {
            credentials?.delete(DesktopCredentialStore.GITHUB_TOKEN)
            configStore.clearSyncState()
            configStore.setProvider(DesktopCloudProvider.GOOGLE_DRIVE)
            _state.value = readState().copy(message = "GitHub disconnected. Local data was kept.")
        } else {
            runCatching { oauth.disconnect() }
            _state.value = readState().copy(message = "Google Drive disconnected. Local data was kept.")
        }
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
                    message = "The cloud backup changed while the conflict choice was open. Review the refreshed conflict.",
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
                    if (!mergeAutomatically(localFingerprint)) {
                        _state.value = readState().copy(
                            syncing = false,
                            conflict = requireNotNull(remote),
                            message = _state.value.message ?: "This PC and the cloud backup both changed.",
                        )
                    }
                }
                decision == SyncDecision.Conflict && resolution == ConflictResolution.KEEP_LOCAL ->
                    upload(remote, heads.map(CloudRevision::fileId), observedHeadIds)
                decision == SyncDecision.Conflict && resolution == ConflictResolution.USE_CLOUD -> {
                    val chosen = requireNotNull(remote)
                    useCloud(chosen, observedHeadIds, localFingerprint)
                }
                decision == SyncDecision.Upload -> {
                    upload(remote, emptyList(), observedHeadIds)
                    // The other device uploaded at the same moment: merge both instead of asking.
                    if (_state.value.conflict != null && mergeAutomatically(dataStore.localFingerprint())) {
                        _state.update { it.copy(conflict = null) }
                    }
                }
                decision == SyncDecision.Download -> download(requireNotNull(remote), localFingerprint)
                else -> _state.value = readState().copy(message = "Already up to date.")
            }
        } catch (cancelled: CancellationException) {
            _state.value = readState().copy(conflict = previousConflict)
            throw cancelled
        } catch (error: Throwable) {
            _state.value = readState().copy(message = error.safeMessage(), lastSyncFailed = true)
        }
        refreshPendingChanges()
    }

    /**
     * Merges this PC's changes with every cloud head record by record (see mergeSnapshots), uploads
     * the result as the one new head, then applies it here. Returns false when the user has to
     * choose instead: a head uses another password, or the merge itself was impossible. Another
     * device racing this upload just means merging again.
     */
    private suspend fun mergeAutomatically(expectedLocalFingerprint: String): Boolean {
        if (!automaticMerge) return false
        var localFingerprint = expectedLocalFingerprint
        repeat(MERGE_ATTEMPTS) {
            val revisions = drive.listRevisions(100)
            val heads = cloudRevisionHeads(revisions)
            val primary = latestCloudRevision(revisions) ?: return false
            val headFiles = heads.map { head ->
                File(dataStore.appDirectory, "cloud-merge-${UUID.randomUUID()}.tlb.part").also { drive.downloadRevision(head, it) }
            }
            var baseFile: File? = null
            var merged: DesktopMergedFile? = null
            try {
                if (!headFiles.all { dataStore.verifyEncrypted(it) }) {
                    _state.update { it.copy(message = "The cloud backup uses a different data password. Choose which version to keep.") }
                    return false
                }
                baseFile = syncBase(configStore.read().syncState.lastRevisionId, revisions)
                merged = try {
                    dataStore.createMergedFile(baseFile, headFiles, localFingerprint, now())
                } catch (_: Exception) {
                    return false // Nothing could be merged safely; let the user choose.
                } ?: run {
                    localFingerprint = dataStore.localFingerprint() // Edited meanwhile: merge again.
                    return@repeat
                }
                val uploaded = uploadFile(
                    source = merged.file,
                    fingerprint = merged.fingerprint,
                    remote = primary,
                    mergedRevisionIds = heads.map(CloudRevision::fileId),
                    expectedHeadIds = heads.mapTo(HashSet(), CloudRevision::fileId),
                    successMessage = null,
                    recordBaseline = false,
                ) ?: return@repeat // Another device uploaded meanwhile.
                when (val result = dataStore.replaceFromEncrypted(merged.file, localFingerprint)) {
                    is DesktopReplaceResult.Applied -> {
                        configStore.recordSync(uploaded.fileId, result.fingerprint, now())
                        saveSyncBase(merged.file, uploaded.fileId)
                        _state.value = readState().copy(
                            message = if (merged.textConflicts > 0) {
                                "Merged changes from your other devices. Some texts were edited on both; both versions were kept."
                            } else {
                                "Merged changes from your other devices."
                            },
                        )
                        return true
                    }
                    // A local edit arrived after the upload; the next sync merges it on top.
                    DesktopReplaceResult.LocalChanged -> {
                        _state.value = readState().copy(message = "Merged changes from your other devices.")
                        return true
                    }
                    DesktopReplaceResult.Invalid -> return false
                }
            } finally {
                headFiles.forEach(File::delete)
                if (baseFile != null && baseFile != syncBaseFile) baseFile.delete()
                merged?.file?.delete()
            }
        }
        _state.update { it.copy(message = "Other devices kept changing the cloud backup. Sync again in a moment.") }
        return false
    }

    private val syncBaseFile: File get() = File(dataStore.appDirectory, "sync-base.tlb")
    private val syncBaseRevisionFile: File get() = File(dataStore.appDirectory, "sync-base.revision")

    /** Keeps the version just agreed with the cloud, so the next merge can tell edits from deletions. */
    private fun saveSyncBase(source: File, revisionId: String) {
        runCatching {
            val temporary = File(dataStore.appDirectory, "sync-base.tlb.part")
            source.copyTo(temporary, overwrite = true)
            syncBaseRevisionFile.delete()
            java.nio.file.Files.move(temporary.toPath(), syncBaseFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            syncBaseRevisionFile.writeText(revisionId)
        }
    }

    /** The last agreed version: the local copy, else the cloud revision if it was not pruned. */
    private suspend fun syncBase(revisionId: String?, revisions: List<CloudRevision>): File? {
        if (revisionId == null) return null
        if (syncBaseFile.isFile && runCatching { syncBaseRevisionFile.readText() }.getOrNull() == revisionId) return syncBaseFile
        val revision = revisions.firstOrNull { it.fileId == revisionId } ?: return null
        val file = File(dataStore.appDirectory, "cloud-base-${UUID.randomUUID()}.tlb.part")
        return runCatching { drive.downloadRevision(revision, file); file }.getOrElse { file.delete(); null }
    }

    /** Compares the local data file with the one recorded at the last sync. */
    private suspend fun refreshPendingChanges() {
        if (!_state.value.connected) return
        val pending = runCatching {
            dataStore.localFingerprint() != configStore.read().syncState.lastContentFingerprint
        }.getOrDefault(false)
        _state.update { if (it.syncing) it else it.copy(pendingChanges = pending) }
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
                    saveSyncBase(temporary, uploaded.fileId)
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
                message = "The cloud backup changed before upload. Review the refreshed conflict.",
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
        if (recordBaseline) {
            configStore.recordSync(revision.fileId, fingerprint, now())
            saveSyncBase(source, revision.fileId)
        }
        runCatching { prunableCloudRevisions(refreshed, keepCount = KEPT_CLOUD_REVISIONS).forEach { drive.deleteRevision(it.fileId) } }
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
                    saveSyncBase(temporary, remote.fileId)
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
            connected = isConnected(),
            provider = config.provider,
            gitHubRepository = config.gitHubRepository,
            automaticSync = config.automaticSync,
            lastSyncAt = config.syncState.lastSyncAt,
        )
    }

    private fun Throwable.safeMessage(): String = when (this) {
        is java.net.UnknownHostException -> "The cloud service could not be reached."
        is java.net.SocketTimeoutException -> "The cloud service timed out."
        is IllegalArgumentException -> message ?: "Cloud sync settings are invalid."
        else -> message?.takeIf { it.length in 1..220 } ?: "Cloud sync failed."
    }

    companion object {
        const val AUTOMATIC_INTERVAL_MILLIS = 2L * 60L * 1_000L
        const val EDIT_DEBOUNCE_MILLIS = 8_000L
        const val FOCUS_SYNC_MIN_INTERVAL_MILLIS = 30_000L
        const val KEPT_CLOUD_REVISIONS = 10
        const val MERGE_ATTEMPTS = 3
    }
}
