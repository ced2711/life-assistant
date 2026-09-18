package com.ced2711.lifetracker.data.cloud

import android.content.Context
import com.ced2711.lifetracker.cloudsync.CloudBackupStore
import com.ced2711.lifetracker.cloudsync.CloudRevision
import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.cloudsync.GoogleDriveBackupStore
import com.ced2711.lifetracker.cloudsync.NewCloudRevision
import com.ced2711.lifetracker.cloudsync.SyncDecision
import com.ced2711.lifetracker.cloudsync.cloudRevisionHeads
import com.ced2711.lifetracker.cloudsync.decideSyncAction
import com.ced2711.lifetracker.cloudsync.latestCloudRevision
import com.ced2711.lifetracker.cloudsync.prunableCloudRevisions
import com.ced2711.lifetracker.data.backup.BackupRepository
import com.ced2711.lifetracker.data.backup.BackupDataChangedException
import com.ced2711.lifetracker.data.backup.BackupRestoreResult
import com.ced2711.lifetracker.data.vault.VaultSession
import com.ced2711.lifetracker.data.vault.VaultAuthenticationRequiredException
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AndroidCloudSyncResult {
    data object Disabled : AndroidCloudSyncResult
    data object UpToDate : AndroidCloudSyncResult
    data class Uploaded(val revision: CloudRevision) : AndroidCloudSyncResult
    data class Downloaded(
        val revision: CloudRevision,
        val restoreResult: BackupRestoreResult,
    ) : AndroidCloudSyncResult
    data class Conflict(val remote: CloudRevision) : AndroidCloudSyncResult
    data class NeedsVaultUnlock(val remote: CloudRevision? = null) : AndroidCloudSyncResult
    data object NeedsGoogleConsent : AndroidCloudSyncResult
    data class Failed(val message: String) : AndroidCloudSyncResult
}

class AndroidCloudSyncEngine(
    context: Context,
    private val backupRepository: BackupRepository,
    private val preferences: CloudSyncPreferences,
    private val secretStore: CloudSyncSecretStore,
    private val authorization: GoogleDriveAuthorization,
    private val afterRestore: suspend () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    cloudStore: CloudBackupStore? = null,
) {
    private val cacheDirectory = File(context.cacheDir, "cloud-sync")
    private val store = cloudStore ?: GoogleDriveBackupStore(authorization)
    private val syncMutex = Mutex()

    suspend fun synchronize(
        vaultSession: VaultSession? = null,
        conflictResolution: ConflictResolution? = null,
        expectedRemoteRevisionId: String? = null,
    ): AndroidCloudSyncResult = syncMutex.withLock { withContext(ioDispatcher) {
        val settings = preferences.read()
        if (!settings.enabled) return@withContext AndroidCloudSyncResult.Disabled
        val password = secretStore.load()
            ?: return@withContext AndroidCloudSyncResult.Failed("The saved sync password is unavailable.")
        try {
            val revisions = try {
                store.listRevisions(limit = 100)
            } catch (_: CloudConsentRequiredException) {
                return@withContext AndroidCloudSyncResult.NeedsGoogleConsent
            }
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
                    AndroidCloudSyncResult.Conflict(requireNotNull(remote))
                decision == SyncDecision.Conflict && conflictResolution == ConflictResolution.KEEP_LOCAL ->
                    upload(
                        password,
                        remote,
                        vaultSession,
                        resolvedHeads,
                        revisionsToValidate = cloudRevisionHeads(revisions),
                    )
                decision == SyncDecision.Conflict && conflictResolution == ConflictResolution.USE_CLOUD -> {
                    val selected = requireNotNull(remote)
                    validateRevisionPasswords(
                        password,
                        cloudRevisionHeads(revisions).filterNot { it.fileId == selected.fileId },
                    )
                    when (
                        val downloaded = download(
                            password,
                            selected,
                            localFingerprint,
                            vaultSession,
                        )
                    ) {
                        is AndroidCloudSyncResult.Downloaded ->
                            upload(password, remote, vaultSession, resolvedHeads)
                        else -> downloaded
                    }
                }
                decision == SyncDecision.Upload -> upload(
                    password,
                    remote,
                    vaultSession,
                )
                decision == SyncDecision.Download ->
                    download(password, requireNotNull(remote), localFingerprint, vaultSession)
                else -> AndroidCloudSyncResult.UpToDate
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CloudConsentRequiredException) {
            AndroidCloudSyncResult.NeedsGoogleConsent
        } catch (error: Throwable) {
            AndroidCloudSyncResult.Failed(error.safeCloudMessage())
        } finally {
            password.fill('\u0000')
        }
    } }

    private suspend fun upload(
        password: CharArray,
        remote: CloudRevision?,
        vaultSession: VaultSession?,
        mergedRevisionIds: List<String> = emptyList(),
        revisionsToValidate: List<CloudRevision> = emptyList(),
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
            preferences.recordSuccessfulSync(revision.fileId, fingerprint, now())
            try {
                val currentRevisions = store.listRevisions(limit = 100)
                prunableCloudRevisions(currentRevisions).forEach { oldRevision ->
                    store.deleteRevision(oldRevision.fileId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The successful upload remains authoritative; retention is retried later.
            }
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
            runCatching { afterRestore() }
            return AndroidCloudSyncResult.Downloaded(remote, result)
        } finally {
            prepared?.close()
            if (encrypted.exists()) encrypted.delete()
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
        is java.net.UnknownHostException -> "Google Drive could not be reached."
        is java.net.SocketTimeoutException -> "Google Drive timed out."
        else -> message?.takeIf { it.length in 1..160 } ?: "Google Drive sync failed."
    }
}
