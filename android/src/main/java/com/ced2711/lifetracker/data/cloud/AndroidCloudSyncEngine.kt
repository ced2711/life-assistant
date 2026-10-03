package com.ced2711.lifetracker.data.cloud

import android.content.Context
import com.ced2711.lifetracker.cloudsync.CloudBackupStore
import com.ced2711.lifetracker.cloudsync.CloudRevision
import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.cloudsync.CloudAuthorizationException
import com.ced2711.lifetracker.cloudsync.GitHubBackupStore
import com.ced2711.lifetracker.cloudsync.GitHubRepository
import com.ced2711.lifetracker.cloudsync.GitHubDeviceAuthorization
import com.ced2711.lifetracker.cloudsync.GitHubSession
import com.ced2711.lifetracker.cloudsync.GitHubSignInExpiredException
import com.ced2711.lifetracker.cloudsync.GoogleDriveBackupStore
import com.ced2711.lifetracker.cloudsync.NewCloudRevision
import com.ced2711.lifetracker.cloudsync.SyncDecision
import com.ced2711.lifetracker.cloudsync.cloudRevisionHeadIds
import com.ced2711.lifetracker.cloudsync.cloudRevisionHeads
import com.ced2711.lifetracker.cloudsync.decideSyncAction
import com.ced2711.lifetracker.cloudsync.latestCloudRevision
import com.ced2711.lifetracker.cloudsync.prunableCloudRevisions
import com.ced2711.lifetracker.data.backup.BackupRepository
import com.ced2711.lifetracker.data.backup.BackupDataChangedException
import com.ced2711.lifetracker.data.backup.BackupRestoreResult
import com.ced2711.lifetracker.data.backup.mergeEncryptedBackups
import com.ced2711.lifetracker.data.vault.VaultSession
import com.ced2711.lifetracker.data.vault.VaultAuthenticationRequiredException
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AndroidCloudSyncResult {
    data object Disabled : AndroidCloudSyncResult
    data object UpToDate : AndroidCloudSyncResult
    data class Uploaded(val revision: CloudRevision) : AndroidCloudSyncResult
    data class Downloaded(
        val revision: CloudRevision,
        val restoreResult: BackupRestoreResult,
        /** Changes from this device and the cloud were merged, not just downloaded. */
        val merged: Boolean = false,
        /** Texts edited on both sides; both versions were kept. */
        val textConflicts: Int = 0,
    ) : AndroidCloudSyncResult
    data class Conflict(val remote: CloudRevision) : AndroidCloudSyncResult
    data class NeedsVaultUnlock(val remote: CloudRevision? = null) : AndroidCloudSyncResult
    data object NeedsGoogleConsent : AndroidCloudSyncResult
    /** GitHub no longer accepts this device's sign-in; reconnecting fixes it, nothing else does. */
    data object NeedsGitHubSignIn : AndroidCloudSyncResult
    data class Failed(val message: String) : AndroidCloudSyncResult
}

class AndroidCloudSyncEngine(
    context: Context,
    private val backupRepository: BackupRepository,
    private val preferences: CloudSyncPreferences,
    private val secretStore: CloudSyncSecretStore,
    private val authorization: GoogleDriveAuthorization,
    private val gitHubTokenStore: CloudSyncSecretStore? = null,
    private val afterRestore: suspend () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    cloudStore: CloudBackupStore? = null,
    // Off only in tests of the manual keep-local / use-cloud choice.
    private val automaticMerge: Boolean = true,
) {
    private val cacheDirectory = File(context.cacheDir, "cloud-sync")
    private val recoveryDirectory = File(context.filesDir, CLOUD_RECOVERY_DIRECTORY)
    private val syncBaseDirectory = File(context.filesDir, "cloud-sync-base")
    private val injectedStore = cloudStore
    private val driveStore by lazy { GoogleDriveBackupStore(authorization) }
    // Chosen at the start of each sync from the configured provider; guarded by [syncMutex].
    private lateinit var store: CloudBackupStore
    private val syncMutex = Mutex()

    /** App-private encrypted recovery snapshots, newest first, for user-initiated export. */
    fun recoveryFiles(): List<File> = recoveryDirectory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { file ->
            file.isFile &&
                file.name.startsWith(CLOUD_RECOVERY_FILE_PREFIX) &&
                file.name.endsWith(".tlb")
        }
        .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
        .toList()

    fun latestRecoveryFile(): File? = recoveryFiles().firstOrNull()

    suspend fun synchronize(
        vaultSession: VaultSession? = null,
        conflictResolution: ConflictResolution? = null,
        expectedRemoteRevisionId: String? = null,
    ): AndroidCloudSyncResult = syncMutex.withLock { trackActivity { withContext(ioDispatcher) {
        val settings = preferences.read()
        if (!settings.enabled) return@withContext AndroidCloudSyncResult.Disabled
        store = try {
            injectedStore ?: storeFor(settings)
        } catch (error: IllegalArgumentException) {
            return@withContext AndroidCloudSyncResult.Failed(error.message ?: "The GitHub repository name is invalid.")
        }
        val password = secretStore.load()
            ?: return@withContext AndroidCloudSyncResult.Failed("The saved sync password could not be read. Sync will try again.")
        try {
            val revisions = try {
                store.listRevisions(limit = 100)
            } catch (_: CloudConsentRequiredException) {
                return@withContext AndroidCloudSyncResult.NeedsGoogleConsent
            }
            val observedHeadIds = cloudRevisionHeadIds(revisions)
            val remote = latestCloudRevision(revisions)
            val localFingerprint = backupRepository.syncFingerprint()
            val localIsEmpty = backupRepository.isUserDataEmpty()
            val decision = decideSyncAction(localFingerprint, localIsEmpty, settings.state, revisions)
            if (
                conflictResolution != null &&
                (expectedRemoteRevisionId == null || remote?.fileId != expectedRemoteRevisionId)
            ) {
                return@withContext AndroidCloudSyncResult.Conflict(requireNotNull(remote))
            }
            val resolvedHeads = if (decision == SyncDecision.Conflict) {
                cloudRevisionHeads(revisions).map(CloudRevision::fileId)
            } else {
                emptyList()
            }
            when {
                decision == SyncDecision.Conflict && conflictResolution == null ->
                    mergeAutomatically(password, vaultSession, localFingerprint, settings.state.lastRevisionId)
                        ?: AndroidCloudSyncResult.Conflict(requireNotNull(remote))
                decision == SyncDecision.Conflict && conflictResolution == ConflictResolution.KEEP_LOCAL ->
                    upload(
                        password,
                        remote,
                        vaultSession,
                        resolvedHeads,
                        revisionsToValidate = cloudRevisionHeads(revisions),
                        expectedHeadIds = observedHeadIds,
                    )
                decision == SyncDecision.Conflict && conflictResolution == ConflictResolution.USE_CLOUD -> {
                    val selected = requireNotNull(remote)
                    validateRevisionPasswords(
                        password,
                        cloudRevisionHeads(revisions).filterNot { it.fileId == selected.fileId },
                    )
                    useCloud(
                        password = password,
                        selected = selected,
                        expectedLocalFingerprint = localFingerprint,
                        vaultSession = vaultSession,
                        mergedRevisionIds = resolvedHeads,
                        expectedHeadIds = observedHeadIds,
                    )
                }
                decision == SyncDecision.Upload -> upload(
                    password,
                    remote,
                    vaultSession,
                    expectedHeadIds = observedHeadIds,
                ).let { result ->
                    // Another device uploaded at the same moment: merge both instead of asking.
                    if (result is AndroidCloudSyncResult.Conflict) {
                        mergeAutomatically(password, vaultSession, backupRepository.syncFingerprint(), settings.state.lastRevisionId) ?: result
                    } else {
                        result
                    }
                }
                decision == SyncDecision.Download ->
                    download(password, requireNotNull(remote), localFingerprint, vaultSession)
                else -> AndroidCloudSyncResult.UpToDate
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CloudConsentRequiredException) {
            AndroidCloudSyncResult.NeedsGoogleConsent
        } catch (_: GitHubSignInExpiredException) {
            AndroidCloudSyncResult.NeedsGitHubSignIn
        } catch (error: Throwable) {
            AndroidCloudSyncResult.Failed(error.safeCloudMessage())
        } finally {
            password.fill('\u0000')
        }
    } } }

    private val _running = MutableStateFlow(false)

    /** True while a sync runs, whoever started it (the app, the worker, or the sync button). */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _finishedSyncs = MutableStateFlow(0L)

    /** Increments after every sync so observers re-read the recorded sync state. */
    val finishedSyncs: StateFlow<Long> = _finishedSyncs.asStateFlow()

    private inline fun <T> trackActivity(block: () -> T): T {
        _running.value = true
        try {
            return block()
        } finally {
            _running.value = false
            _finishedSyncs.value += 1
        }
    }

    private fun storeFor(settings: AndroidCloudSyncSettings): CloudBackupStore = when (settings.provider) {
        CloudProvider.GOOGLE_DRIVE -> driveStore
        CloudProvider.GITHUB -> GitHubBackupStore(
            tokenProvider = gitHubSession,
            repository = GitHubRepository.parse(settings.gitHubRepository),
        )
    }

    // One session for the app: it renews the eight-hour GitHub token and saves the new one.
    private val gitHubSession = GitHubSession(
        load = {
            val tokens = gitHubTokenStore
            val saved = tokens?.load()
            if (saved == null && tokens?.hasSecret() == true) {
                // Saved but unreadable for the moment: try again later rather than ask to sign in.
                throw IOException("The saved GitHub sign-in could not be read. Sync will try again.")
            }
            saved?.let { try { it.concatToString() } finally { it.fill('\u0000') } }
        },
        save = { text -> requireNotNull(gitHubTokenStore) { "No storage for the GitHub sign-in." }.save(text.toCharArray()) },
        authorization = {
            GitHubDeviceAuthorization(preferences.read().gitHubClientId.ifBlank { com.ced2711.lifetracker.BuildConfig.GITHUB_CLIENT_ID })
        },
        now = now,
    )

    /** Old revisions only cost cloud space; keep recent history and never touch competing tips. */
    private suspend fun pruneOldRevisions(revisions: List<CloudRevision>) {
        runCatching {
            prunableCloudRevisions(revisions, keepCount = KEPT_CLOUD_REVISIONS).forEach { store.deleteRevision(it.fileId) }
        }
    }

    private suspend fun upload(
        password: CharArray,
        remote: CloudRevision?,
        vaultSession: VaultSession?,
        mergedRevisionIds: List<String> = emptyList(),
        revisionsToValidate: List<CloudRevision> = emptyList(),
        expectedHeadIds: Set<String>,
    ): AndroidCloudSyncResult {
        cacheDirectory.mkdirs()
        validateRevisionPasswords(password, revisionsToValidate)
        val encrypted = File.createTempFile("life-tracker-upload-", ".tlb", cacheDirectory)
        try {
            var fingerprint = backupRepository.syncFingerprint()
            var exported = false
            for (attempt in 0 until 2) {
                encrypted.outputStream().buffered().use { output ->
                    try {
                        backupRepository.export(output, password.copyOf(), vaultSession)
                    } catch (_: VaultAuthenticationRequiredException) {
                        return AndroidCloudSyncResult.NeedsVaultUnlock()
                    }
                }
                val after = backupRepository.syncFingerprint()
                if (after == fingerprint) {
                    exported = true
                    break
                }
                fingerprint = after
            }
            if (!exported) {
                return AndroidCloudSyncResult.Failed("Data kept changing while cloud backup was created.")
            }
            val preflightRevisions = store.listRevisions(limit = 100)
            if (cloudRevisionHeadIds(preflightRevisions) != expectedHeadIds) {
                val changedRemote = latestCloudRevision(preflightRevisions) ?: remote
                    ?: return AndroidCloudSyncResult.Failed(
                        "The cloud backup changed before upload. Sync again to review it.",
                    )
                return AndroidCloudSyncResult.Conflict(changedRemote)
            }
            val revision = store.uploadRevision(
                encrypted,
                NewCloudRevision(
                    createdAt = now(),
                    deviceId = preferences.read().state.deviceId,
                    baseRevisionId = remote?.fileId,
                    contentFingerprint = fingerprint,
                    mergedRevisionIds = mergedRevisionIds,
                ),
            )
            val postflightRevisions = store.listRevisions(limit = 100)
            if (cloudRevisionHeadIds(postflightRevisions) != setOf(revision.fileId)) {
                return AndroidCloudSyncResult.Conflict(
                    latestCloudRevision(postflightRevisions) ?: revision,
                )
            }
            preferences.recordSuccessfulSync(revision.fileId, fingerprint, now())
            saveSyncBase(encrypted, revision.fileId)
            pruneOldRevisions(postflightRevisions)
            return AndroidCloudSyncResult.Uploaded(revision)
        } finally {
            if (encrypted.exists()) encrypted.delete()
        }
    }

    private suspend fun download(
        password: CharArray,
        remote: CloudRevision,
        expectedLocalFingerprint: String,
        vaultSession: VaultSession?,
    ): AndroidCloudSyncResult {
        cacheDirectory.mkdirs()
        val encrypted = File.createTempFile("life-tracker-download-", ".tlb", cacheDirectory)
        var prepared: com.ced2711.lifetracker.data.backup.PreparedBackupRestore? = null
        try {
            store.downloadRevision(remote, encrypted)
            prepared = encrypted.inputStream().buffered().use {
                backupRepository.prepareRestore(it, password.copyOf())
            }
            if (prepared.requiresVaultAuthentication && vaultSession == null) {
                return AndroidCloudSyncResult.NeedsVaultUnlock(remote)
            }
            val result = try {
                backupRepository.restore(
                    prepared,
                    vaultSession,
                    expectedLocalSyncFingerprint = expectedLocalFingerprint,
                )
            } catch (_: VaultAuthenticationRequiredException) {
                return AndroidCloudSyncResult.NeedsVaultUnlock(remote)
            } catch (_: BackupDataChangedException) {
                return AndroidCloudSyncResult.Conflict(remote)
            }
            prepared = null
            val fingerprint = result.appliedSyncFingerprint
                ?: throw IllegalStateException("Cloud restore did not report its applied fingerprint.")
            preferences.recordSuccessfulSync(remote.fileId, fingerprint, now())
            saveSyncBase(encrypted, remote.fileId)
            runCatching { afterRestore() }
            return AndroidCloudSyncResult.Downloaded(remote, result)
        } finally {
            prepared?.close()
            if (encrypted.exists()) encrypted.delete()
        }
    }

    /**
     * Resolves a conflict in favour of the selected cloud snapshot without replacing live local
     * data until the new immutable cloud revision has passed both head checks. The selected
     * encrypted bytes are uploaded unchanged so the revision fingerprint describes exactly what
     * will be restored. If another device changes the heads at any point, this returns before
     * restore and leaves both local data and the sync baseline untouched.
     */
    private suspend fun useCloud(
        password: CharArray,
        selected: CloudRevision,
        expectedLocalFingerprint: String,
        vaultSession: VaultSession?,
        mergedRevisionIds: List<String>,
        expectedHeadIds: Set<String>,
    ): AndroidCloudSyncResult {
        cacheDirectory.mkdirs()
        val encrypted = File.createTempFile("life-tracker-use-cloud-", ".tlb", cacheDirectory)
        var prepared: com.ced2711.lifetracker.data.backup.PreparedBackupRestore? = null
        try {
            store.downloadRevision(selected, encrypted)
            prepared = encrypted.inputStream().buffered().use {
                backupRepository.prepareRestore(it, password.copyOf())
            }
            if (prepared.requiresVaultAuthentication && vaultSession == null) {
                return AndroidCloudSyncResult.NeedsVaultUnlock(selected)
            }

            // Download/prepare may take long enough for another device to publish a head. Do
            // this check before creating the immutable merged revision so no cloud mutation or
            // local restore happens for stale conflict approval.
            val preflightRevisions = store.listRevisions(limit = 100)
            if (cloudRevisionHeadIds(preflightRevisions) != expectedHeadIds) {
                val changedRemote = latestCloudRevision(preflightRevisions) ?: selected
                return AndroidCloudSyncResult.Conflict(changedRemote)
            }

            val revision = store.uploadRevision(
                encrypted,
                NewCloudRevision(
                    createdAt = now(),
                    deviceId = preferences.read().state.deviceId,
                    baseRevisionId = selected.fileId,
                    contentFingerprint = selected.contentFingerprint,
                    mergedRevisionIds = mergedRevisionIds,
                ),
            )
            val postflightRevisions = store.listRevisions(limit = 100)
            if (cloudRevisionHeadIds(postflightRevisions) != setOf(revision.fileId)) {
                return AndroidCloudSyncResult.Conflict(
                    latestCloudRevision(postflightRevisions) ?: revision,
                )
            }

            // Preserve the exact local dataset that was present when the conflict choice began.
            // This is durable app-private data and intentionally survives successful restore so
            // a mistaken choice can be recovered without relying on cloud history.
            try {
                persistLocalRecoverySnapshot(password, vaultSession, expectedLocalFingerprint)
            } catch (_: VaultAuthenticationRequiredException) {
                return AndroidCloudSyncResult.NeedsVaultUnlock(revision)
            } catch (_: BackupDataChangedException) {
                return AndroidCloudSyncResult.Conflict(revision)
            }

            // The expected fingerprint is rechecked inside restore immediately before its
            // transactional commit. A local edit therefore leaves the local dataset intact and
            // prevents recording a baseline for a revision that was never applied.
            val result = try {
                backupRepository.restore(
                    prepared,
                    vaultSession,
                    expectedLocalSyncFingerprint = expectedLocalFingerprint,
                )
            } catch (_: VaultAuthenticationRequiredException) {
                return AndroidCloudSyncResult.NeedsVaultUnlock(selected)
            } catch (_: BackupDataChangedException) {
                return AndroidCloudSyncResult.Conflict(revision)
            }
            prepared = null
            val fingerprint = result.appliedSyncFingerprint
                ?: throw IllegalStateException("Cloud restore did not report its applied fingerprint.")
            // The cloud metadata is deliberately preserved from the selected revision. Android
            // and Windows use different local dirty-check fingerprints (logical snapshot versus
            // encrypted-file SHA-256), so only the applied Android fingerprint belongs in the
            // Android sync baseline.
            pruneOldRevisions(postflightRevisions)
            preferences.recordSuccessfulSync(revision.fileId, fingerprint, now())
            saveSyncBase(encrypted, revision.fileId)
            runCatching { afterRestore() }
            return AndroidCloudSyncResult.Downloaded(revision, result)
        } finally {
            prepared?.close()
            if (encrypted.exists()) encrypted.delete()
        }
    }

    /**
     * Merges this device's data with every cloud head record by record (see mergeSnapshots),
     * uploads the result as the single new head, then restores it here. Returns null when the
     * user has to choose instead: a head uses another password, or no valid merge exists.
     * Another device racing the upload just means merging again.
     */
    private suspend fun mergeAutomatically(
        password: CharArray,
        vaultSession: VaultSession?,
        expectedLocalFingerprint: String,
        lastRevisionId: String?,
    ): AndroidCloudSyncResult? {
        if (!automaticMerge) return null
        var localFingerprint = expectedLocalFingerprint
        repeat(MERGE_ATTEMPTS) {
            val revisions = store.listRevisions(limit = 100)
            val heads = cloudRevisionHeads(revisions)
            val primary = latestCloudRevision(revisions) ?: return null
            cacheDirectory.mkdirs()
            val local = File.createTempFile("life-tracker-merge-local-", ".tlb", cacheDirectory)
            val headFiles = ArrayList<File>()
            val work = File(cacheDirectory, "merge-${UUID.randomUUID()}")
            val merged = File.createTempFile("life-tracker-merged-", ".tlb", cacheDirectory)
            var downloadedBase: File? = null
            var prepared: com.ced2711.lifetracker.data.backup.PreparedBackupRestore? = null
            try {
                local.outputStream().buffered().use { output ->
                    try {
                        backupRepository.export(output, password.copyOf(), vaultSession)
                    } catch (_: VaultAuthenticationRequiredException) {
                        return AndroidCloudSyncResult.NeedsVaultUnlock(primary)
                    }
                }
                val exportedFingerprint = backupRepository.syncFingerprint()
                if (exportedFingerprint != localFingerprint) {
                    localFingerprint = exportedFingerprint // Edited while exporting: merge again.
                    return@repeat
                }
                heads.forEach { head ->
                    val file = File.createTempFile("life-tracker-merge-head-", ".tlb", cacheDirectory)
                    headFiles += file
                    store.downloadRevision(head, file)
                }
                val base = syncBase(lastRevisionId, revisions)
                if (base != null && base != syncBaseFile) downloadedBase = base
                val textConflicts = try {
                    mergeEncryptedBackups(local, base, headFiles, password, work, merged, now())
                } catch (_: com.ced2711.lifetracker.data.backup.BackupException) {
                    return null // Another password, or nothing valid to merge: let the user choose.
                }

                val preflight = store.listRevisions(limit = 100)
                if (cloudRevisionHeadIds(preflight) != heads.mapTo(HashSet(), CloudRevision::fileId)) return@repeat
                val revision = store.uploadRevision(
                    merged,
                    NewCloudRevision(
                        createdAt = now(),
                        deviceId = preferences.read().state.deviceId,
                        baseRevisionId = primary.fileId,
                        contentFingerprint = sha256Hex(merged),
                        mergedRevisionIds = heads.map(CloudRevision::fileId),
                    ),
                )
                val postflight = store.listRevisions(limit = 100)
                if (cloudRevisionHeadIds(postflight) != setOf(revision.fileId)) return@repeat

                prepared = merged.inputStream().buffered().use { backupRepository.prepareRestore(it, password.copyOf()) }
                if (prepared.requiresVaultAuthentication && vaultSession == null) {
                    return AndroidCloudSyncResult.NeedsVaultUnlock(revision)
                }
                val result = try {
                    backupRepository.restore(prepared, vaultSession, expectedLocalSyncFingerprint = localFingerprint)
                } catch (_: VaultAuthenticationRequiredException) {
                    return AndroidCloudSyncResult.NeedsVaultUnlock(revision)
                } catch (_: BackupDataChangedException) {
                    // A local edit arrived after the upload; the next sync merges it on top.
                    return AndroidCloudSyncResult.Uploaded(revision)
                }
                prepared = null
                val fingerprint = result.appliedSyncFingerprint
                    ?: throw IllegalStateException("Cloud restore did not report its applied fingerprint.")
                preferences.recordSuccessfulSync(revision.fileId, fingerprint, now())
                saveSyncBase(merged, revision.fileId)
                pruneOldRevisions(postflight)
                runCatching { afterRestore() }
                return AndroidCloudSyncResult.Downloaded(revision, result, merged = true, textConflicts = textConflicts)
            } finally {
                prepared?.close()
                local.delete()
                headFiles.forEach(File::delete)
                downloadedBase?.delete()
                merged.delete()
                work.deleteRecursively()
            }
        }
        return AndroidCloudSyncResult.Failed("Other devices kept changing the cloud backup. Sync again in a moment.")
    }

    private val syncBaseFile: File get() = File(syncBaseDirectory, "sync-base.tlb")
    private val syncBaseRevisionFile: File get() = File(syncBaseDirectory, "sync-base.revision")

    /** Keeps the version just agreed with the cloud, so the next merge can tell edits from deletions. */
    private fun saveSyncBase(source: File, revisionId: String) {
        runCatching {
            syncBaseDirectory.mkdirs()
            val temporary = File(syncBaseDirectory, "sync-base.tlb.part")
            source.copyTo(temporary, overwrite = true)
            syncBaseRevisionFile.delete()
            Files.move(temporary.toPath(), syncBaseFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            syncBaseRevisionFile.writeText(revisionId)
        }
    }

    /** The last agreed version: the local copy, else the cloud revision if it was not pruned. */
    private suspend fun syncBase(revisionId: String?, revisions: List<CloudRevision>): File? {
        if (revisionId == null) return null
        if (syncBaseFile.isFile && runCatching { syncBaseRevisionFile.readText() }.getOrNull() == revisionId) return syncBaseFile
        val revision = revisions.firstOrNull { it.fileId == revisionId } ?: return null
        val file = File.createTempFile("life-tracker-merge-base-", ".tlb", cacheDirectory)
        return runCatching { store.downloadRevision(revision, file); file }.getOrElse { file.delete(); null }
    }

    private fun sha256Hex(file: File): String = file.inputStream().use { input ->
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun persistLocalRecoverySnapshot(
        password: CharArray,
        vaultSession: VaultSession?,
        expectedLocalFingerprint: String,
    ): File {
        val before = backupRepository.syncFingerprint()
        if (before != expectedLocalFingerprint) throw BackupDataChangedException()
        cacheDirectory.mkdirs()
        if (!recoveryDirectory.exists() && !recoveryDirectory.mkdirs()) {
            throw IOException("Could not create the cloud recovery directory.")
        }
        val temporary = File.createTempFile(
            CLOUD_RECOVERY_FILE_PREFIX,
            ".tlb.part",
            cacheDirectory,
        )
        try {
            temporary.outputStream().buffered().use { output ->
                backupRepository.export(output, password.copyOf(), vaultSession)
            }
            if (
                backupRepository.syncFingerprint() != before ||
                before != expectedLocalFingerprint
            ) {
                throw BackupDataChangedException()
            }
            val destination = File(
                recoveryDirectory,
                "$CLOUD_RECOVERY_FILE_PREFIX${now()}-${UUID.randomUUID()}.tlb",
            )
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath())
            }
            recoveryFiles().drop(KEPT_RECOVERY_COPIES).forEach(File::delete)
            return destination
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    /** Existing heads must use the same password before a new revision can reference them. */
    private suspend fun validateRevisionPasswords(
        password: CharArray,
        revisions: List<CloudRevision>,
    ) {
        if (revisions.isEmpty()) return
        cacheDirectory.mkdirs()
        revisions.distinctBy(CloudRevision::fileId).forEach { revision ->
            val encrypted = File.createTempFile("life-tracker-password-check-", ".tlb", cacheDirectory)
            var prepared: com.ced2711.lifetracker.data.backup.PreparedBackupRestore? = null
            try {
                store.downloadRevision(revision, encrypted)
                prepared = encrypted.inputStream().buffered().use {
                    backupRepository.prepareRestore(it, password.copyOf())
                }
            } finally {
                prepared?.close()
                if (encrypted.exists()) encrypted.delete()
            }
        }
    }

    private fun Throwable.safeCloudMessage(): String = when (this) {
        is java.net.UnknownHostException -> "The cloud service could not be reached."
        is java.net.SocketTimeoutException -> "The cloud service timed out."
        else -> message?.takeIf { it.length in 1..200 } ?: "Cloud sync failed."
    }

    private companion object {
        const val CLOUD_RECOVERY_DIRECTORY = "cloud-recovery"
        const val KEPT_CLOUD_REVISIONS = 10
        const val KEPT_RECOVERY_COPIES = 5
        const val MERGE_ATTEMPTS = 3
        const val CLOUD_RECOVERY_FILE_PREFIX = "life-tracker-cloud-recovery-"
    }
}
