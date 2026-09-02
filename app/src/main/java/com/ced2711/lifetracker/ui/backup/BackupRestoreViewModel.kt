package com.ced2711.lifetracker.ui.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.DateFormat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ced2711.lifetracker.AppContainer
import com.ced2711.lifetracker.BuildConfig
import com.ced2711.lifetracker.data.backup.BackupAuthenticationException
import com.ced2711.lifetracker.data.backup.BackupExportResult
import com.ced2711.lifetracker.data.backup.BackupPreview
import com.ced2711.lifetracker.data.backup.BackupRepository
import com.ced2711.lifetracker.data.backup.BackupRestoreResult
import com.ced2711.lifetracker.data.backup.InvalidBackupException
import com.ced2711.lifetracker.data.backup.PreparedBackupRestore
import com.ced2711.lifetracker.data.backup.UnsupportedBackupException
import com.ced2711.lifetracker.data.settings.SettingsRepository
import com.ced2711.lifetracker.data.vault.VaultSession
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.widget.WidgetRefreshCoordinator
import com.ced2711.lifetracker.worker.WorkScheduler
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackupAuthenticationPurpose {
    EXPORT,
    RESTORE,
}

enum class BackupAuthenticationStatus {
    REQUESTED,
    DISPATCHED,
    AUTHENTICATING,
}

data class BackupAuthenticationRequest(
    val nonce: Long,
    val purpose: BackupAuthenticationPurpose,
    val status: BackupAuthenticationStatus,
)

/** Keeps platform Uri instances out of local ViewModel tests without persisting their value. */
@JvmInline
internal value class BackupDocumentReference(val encodedUri: String)

internal interface BackupDocumentAccess {
    fun openOutput(reference: BackupDocumentReference): OutputStream?
    fun openInput(reference: BackupDocumentReference): InputStream?
    fun delete(reference: BackupDocumentReference): Boolean
}

internal interface BackupPreparedRestore : AutoCloseable {
    val preview: BackupPreview
    val requiresVaultAuthentication: Boolean
    suspend fun commit(vaultSession: VaultSession?): BackupRestoreResult
}

internal interface BackupGateway {
    suspend fun export(
        destination: OutputStream,
        password: CharArray,
        vaultSession: VaultSession?,
    ): BackupExportResult

    suspend fun prepareRestore(source: InputStream, password: CharArray): BackupPreparedRestore
}

private class RepositoryBackupGateway(
    private val repository: BackupRepository,
) : BackupGateway {
    override suspend fun export(
        destination: OutputStream,
        password: CharArray,
        vaultSession: VaultSession?,
    ): BackupExportResult = repository.export(destination, password, vaultSession)

    override suspend fun prepareRestore(
        source: InputStream,
        password: CharArray,
    ): BackupPreparedRestore = RepositoryPreparedRestore(repository, repository.prepareRestore(source, password))
}

private class RepositoryPreparedRestore(
    private val repository: BackupRepository,
    private val prepared: PreparedBackupRestore,
) : BackupPreparedRestore {
    override val preview: BackupPreview get() = prepared.preview
    override val requiresVaultAuthentication: Boolean
        get() = prepared.requiresVaultAuthentication

    override suspend fun commit(vaultSession: VaultSession?): BackupRestoreResult =
        repository.restore(prepared, vaultSession)

    override fun close() = prepared.close()
}

private class ResolverBackupDocumentAccess(
    private val resolver: ContentResolver,
) : BackupDocumentAccess {
    override fun openOutput(reference: BackupDocumentReference): OutputStream? =
        resolver.openOutputStream(Uri.parse(reference.encodedUri), "wt")

    override fun openInput(reference: BackupDocumentReference): InputStream? =
        resolver.openInputStream(Uri.parse(reference.encodedUri))

    override fun delete(reference: BackupDocumentReference): Boolean = runCatching {
        DocumentsContract.deleteDocument(resolver, Uri.parse(reference.encodedUri))
    }.getOrDefault(false)
}

/**
 * Owns only in-memory backup work. Passwords, authenticated previews, and Vault leases are never
 * saved and are cleared or closed on every terminal path.
 */
class BackupRestoreViewModel internal constructor(
    private val gateway: BackupGateway,
    private val documents: BackupDocumentAccess,
    private val hasVault: () -> Boolean,
    private val previewLabel: suspend (BackupPreview) -> String,
    private val rebuildAfterRestore: suspend () -> Unit,
    private val openIncludedBackup: (() -> InputStream?)? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel(), BackupRestoreActions {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val nonceSource = AtomicLong(System.nanoTime())
    private val stateLock = Any()
    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()
    private val _authenticationRequest = MutableStateFlow<BackupAuthenticationRequest?>(null)
    val authenticationRequest: StateFlow<BackupAuthenticationRequest?> =
        _authenticationRequest.asStateFlow()

    private var preparedRestore: BackupPreparedRestore? = null
    private var preparedPreview: BackupRestorePreview? = null
    private var pendingAuthentication: PendingAuthentication? = null

    override fun exportBackup(destination: Uri, password: CharArray) {
        exportBackup(BackupDocumentReference(destination.toString()), password)
    }

    override fun discardExportDestination(destination: Uri) {
        deleteExportBestEffort(BackupDocumentReference(destination.toString()))
    }

    internal fun exportBackup(destination: BackupDocumentReference, password: CharArray) {
        val requiresAuthentication = runCatching(hasVault).getOrElse {
            password.clear()
            deleteExportBestEffort(destination)
            publishIdle(notice = BackupRestoreNotice.EXPORT_FAILED)
            return
        }
        synchronized(stateLock) {
            if (!canStartOperationLocked() || preparedRestore != null) {
                password.clear()
                deleteExportBestEffort(destination)
                return
            }
            _uiState.value = BackupRestoreUiState(task = BackupRestoreTask.EXPORTING)
            if (requiresAuthentication) {
                requestAuthenticationLocked(
                    PendingAuthentication.Export(destination, password),
                    BackupAuthenticationPurpose.EXPORT,
                )
                return
            }
            launchExportLocked(destination, password, vaultSession = null)
        }
    }

    override fun prepareRestore(source: Uri, password: CharArray) {
        prepareRestore(BackupDocumentReference(source.toString()), password)
    }

    internal fun prepareRestore(source: BackupDocumentReference, password: CharArray) {
        prepareRestore(password) { documents.openInput(source) }
    }

    override fun prepareIncludedBackup(password: CharArray) {
        val opener = openIncludedBackup
        if (opener == null) {
            password.clear()
            publishIdle(BackupRestoreNotice.BACKUP_NOT_READABLE)
            return
        }
        prepareRestore(password, opener)
    }

    private fun prepareRestore(
        password: CharArray,
        openSource: () -> InputStream?,
    ) {
        synchronized(stateLock) {
            if (!canStartOperationLocked() || preparedRestore != null) {
                password.clear()
                return
            }
            _uiState.value = BackupRestoreUiState(task = BackupRestoreTask.VALIDATING_RESTORE)
            scope.launch {
                var unclaimedPrepared: BackupPreparedRestore? = null
                try {
                    val prepared = openSource()?.use { input ->
                        gateway.prepareRestore(input, password)
                    } ?: throw FileNotFoundException()
                    unclaimedPrepared = prepared
                    val preview = prepared.preview.toUiPreview(previewLabel(prepared.preview))
                    synchronized(stateLock) {
                        preparedRestore?.close()
                        preparedRestore = prepared
                        preparedPreview = preview
                        unclaimedPrepared = null
                        _uiState.value = BackupRestoreUiState(restorePreview = preview)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    synchronized(stateLock) {
                        _uiState.value = BackupRestoreUiState(
                            notice = prepareRestoreNotice(failure),
                        )
                    }
                } finally {
                    unclaimedPrepared?.close()
                    password.clear()
                }
            }
        }
    }

    override fun commitPreparedRestore() {
        synchronized(stateLock) {
            val prepared = preparedRestore ?: return
            if (!canStartOperationLocked()) return
            _uiState.value = BackupRestoreUiState(task = BackupRestoreTask.COMMITTING_RESTORE)
            if (prepared.requiresVaultAuthentication) {
                requestAuthenticationLocked(
                    PendingAuthentication.Restore,
                    BackupAuthenticationPurpose.RESTORE,
                )
            } else {
                launchRestoreLocked(prepared, vaultSession = null)
            }
        }
    }

    override fun discardPreparedRestore() {
        synchronized(stateLock) {
            if (_uiState.value.task != BackupRestoreTask.NONE) return
            preparedRestore?.close()
            preparedRestore = null
            preparedPreview = null
            _uiState.value = BackupRestoreUiState(notice = _uiState.value.notice)
        }
    }

    override fun acknowledgeNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    /** Returns true once for a nonce, including across host recreation. */
    fun markAuthenticationDispatched(nonce: Long): Boolean = synchronized(stateLock) {
        val request = _authenticationRequest.value ?: return@synchronized false
        if (request.nonce != nonce || request.status != BackupAuthenticationStatus.REQUESTED) {
            return@synchronized false
        }
        _authenticationRequest.value = request.copy(status = BackupAuthenticationStatus.DISPATCHED)
        true
    }

    fun markAuthenticationInProgress(nonce: Long) = synchronized(stateLock) {
        val request = _authenticationRequest.value ?: return@synchronized
        if (request.nonce == nonce && request.status != BackupAuthenticationStatus.REQUESTED) {
            _authenticationRequest.value = request.copy(
                status = BackupAuthenticationStatus.AUTHENTICATING,
            )
        }
    }

    fun provideAuthenticatedLease(nonce: Long, lease: VaultSession) {
        synchronized(stateLock) {
            val request = _authenticationRequest.value
            val pending = pendingAuthentication
            if (request?.nonce != nonce || pending == null) {
                lease.close()
                return
            }
            pendingAuthentication = null
            _authenticationRequest.value = null
            when (pending) {
                is PendingAuthentication.Export ->
                    launchExportLocked(pending.destination, pending.password, lease)
                PendingAuthentication.Restore -> {
                    val prepared = preparedRestore
                    if (prepared == null) {
                        lease.close()
                        publishIdleLocked(BackupRestoreNotice.RESTORE_FAILED)
                    } else {
                        launchRestoreLocked(prepared, lease)
                    }
                }
            }
        }
    }

    fun authenticationCancelled(nonce: Long) {
        synchronized(stateLock) {
            val request = _authenticationRequest.value ?: return
            if (request.nonce != nonce) return
            clearPendingAuthenticationLocked()
            _uiState.value = BackupRestoreUiState(
                restorePreview = preparedPreview,
                notice = BackupRestoreNotice.VAULT_AUTHENTICATION_CANCELLED,
            )
        }
    }

    /** Called only after Unlocking/a prompt has been observed, avoiding a false initial cancel. */
    fun authenticationReturnedLocked(nonce: Long) {
        val request = _authenticationRequest.value ?: return
        if (request.nonce == nonce && request.status == BackupAuthenticationStatus.AUTHENTICATING) {
            authenticationCancelled(nonce)
        }
    }

    /** Clears authenticated preview and pending credentials when the auxiliary destination closes. */
    fun leaveBackupScreen() {
        synchronized(stateLock) {
            if (_uiState.value.task == BackupRestoreTask.COMMITTING_RESTORE) return
            clearPendingAuthenticationLocked()
            preparedRestore?.close()
            preparedRestore = null
            preparedPreview = null
            _uiState.value = BackupRestoreUiState()
        }
    }

    override fun onCleared() {
        synchronized(stateLock) {
            clearPendingAuthenticationLocked()
            if (closePreparedOnClear(preparedRestore)) {
                preparedRestore = null
                preparedPreview = null
            }
        }
        scope.cancel()
        super.onCleared()
    }

    private fun requestAuthenticationLocked(
        pending: PendingAuthentication,
        purpose: BackupAuthenticationPurpose,
    ) {
        check(pendingAuthentication == null)
        pendingAuthentication = pending
        _authenticationRequest.value = BackupAuthenticationRequest(
            nonce = nonceSource.incrementAndGet(),
            purpose = purpose,
            status = BackupAuthenticationStatus.REQUESTED,
        )
    }

    private fun launchExportLocked(
        destination: BackupDocumentReference,
        password: CharArray,
        vaultSession: VaultSession?,
    ) {
        scope.launch {
            var succeeded = false
            try {
                vaultSession.useNullable { session ->
                    documents.openOutput(destination)?.use { output ->
                        gateway.export(output, password, session)
                    } ?: throw FileNotFoundException()
                }
                succeeded = true
                publishIdle(BackupRestoreNotice.EXPORT_COMPLETE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                publishIdle(BackupRestoreNotice.EXPORT_FAILED)
            } finally {
                if (!succeeded) deleteExportBestEffort(destination)
                password.clear()
            }
        }
    }

    private fun launchRestoreLocked(
        prepared: BackupPreparedRestore,
        vaultSession: VaultSession?,
    ) {
        scope.launch {
            try {
                vaultSession.useNullable { session -> prepared.commit(session) }
                runCatching { rebuildAfterRestore() }
                synchronized(stateLock) {
                    preparedRestore = null
                    preparedPreview = null
                    prepared.close()
                    _uiState.value = BackupRestoreUiState(
                        notice = BackupRestoreNotice.RESTORE_COMPLETE,
                    )
                }
            } catch (cancelled: CancellationException) {
                synchronized(stateLock) {
                    if (closePreparedOnClear(prepared)) {
                        if (preparedRestore === prepared) preparedRestore = null
                        preparedPreview = null
                    }
                }
                throw cancelled
            } catch (_: Throwable) {
                synchronized(stateLock) {
                    _uiState.value = BackupRestoreUiState(
                        restorePreview = preparedPreview ?: prepared.preview.toUiPreview(
                            fallbackCreatedAtLabel(prepared.preview.createdAt),
                        ),
                        notice = BackupRestoreNotice.RESTORE_FAILED,
                    )
                }
            }
        }
    }

    private fun publishIdle(notice: BackupRestoreNotice) = synchronized(stateLock) {
        publishIdleLocked(notice)
    }

    private fun publishIdleLocked(notice: BackupRestoreNotice) {
        _uiState.value = BackupRestoreUiState(notice = notice)
    }

    private fun canStartOperationLocked(): Boolean =
        _uiState.value.task == BackupRestoreTask.NONE &&
            pendingAuthentication == null

    private fun clearPendingAuthenticationLocked() {
        when (val pending = pendingAuthentication) {
            is PendingAuthentication.Export -> {
                pending.password.clear()
                deleteExportBestEffort(pending.destination)
            }
            PendingAuthentication.Restore, null -> Unit
        }
        pendingAuthentication = null
        _authenticationRequest.value = null
    }

    private fun deleteExportBestEffort(destination: BackupDocumentReference) {
        runCatching { documents.delete(destination) }
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BackupRestoreViewModel::class.java))
            val context = container.appContext
            return BackupRestoreViewModel(
                gateway = RepositoryBackupGateway(container.backupRepository),
                documents = ResolverBackupDocumentAccess(context.contentResolver),
                hasVault = container.vaultKeyManager::hasVault,
                previewLabel = { preview ->
                    formatPreviewCreatedAt(
                        timestamp = preview.createdAt,
                        settingsRepository = container.settingsRepository,
                        systemUses24Hour = DateFormat.is24HourFormat(context),
                    )
                },
                rebuildAfterRestore = {
                    WorkScheduler.rescheduleAfterSystemTimeChange(context)
                    WidgetRefreshCoordinator.refresh(context)
                },
                openIncludedBackup = if (BuildConfig.HAS_INCLUDED_PERSONAL_BACKUP) {
                    { context.assets.open(INCLUDED_PERSONAL_BACKUP_ASSET) }
                } else {
                    null
                },
            ) as T
        }
    }

    private sealed interface PendingAuthentication {
        data class Export(
            val destination: BackupDocumentReference,
            val password: CharArray,
        ) : PendingAuthentication

        data object Restore : PendingAuthentication
    }
}

private const val INCLUDED_PERSONAL_BACKUP_ASSET = "personal-backup.tlb"

private suspend fun formatPreviewCreatedAt(
    timestamp: Long,
    settingsRepository: SettingsRepository,
    systemUses24Hour: Boolean,
): String {
    val dateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    return runCatching {
        val settings = settingsRepository.snapshot()
        val date = UserFormatting.formatDate(dateTime.toLocalDate(), settings.dateFormat, Locale.US)
        val time = UserFormatting.formatTime(
            dateTime.toLocalTime(),
            settings.timeFormat,
            systemUses24Hour,
            Locale.US,
        )
        "$date, $time"
    }.getOrElse { fallbackCreatedAtLabel(timestamp) }
}

private fun fallbackCreatedAtLabel(timestamp: Long): String = runCatching {
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US))
}.getOrDefault("Unknown date")

private fun BackupPreview.toUiPreview(createdAtLabel: String) = BackupRestorePreview(
    createdAtLabel = createdAtLabel,
    todoCount = todoCount,
    ledgerCount = ledgerCount,
    vaultCount = vaultCount,
    attachmentCount = attachmentCount,
    attachmentBytes = attachmentBytes,
    totalBytes = totalBytes,
    noteCount = noteCount,
)

private fun prepareRestoreNotice(failure: Throwable): BackupRestoreNotice = when (failure) {
    is BackupAuthenticationException -> BackupRestoreNotice.WRONG_PASSWORD_OR_MODIFIED
    is UnsupportedBackupException -> BackupRestoreNotice.UNSUPPORTED_BACKUP
    is InvalidBackupException -> if (failure.message.orEmpty().containsSizeFailure()) {
        BackupRestoreNotice.BACKUP_TOO_LARGE
    } else {
        BackupRestoreNotice.BACKUP_NOT_READABLE
    }
    is FileNotFoundException, is IOException, is SecurityException ->
        BackupRestoreNotice.BACKUP_NOT_READABLE
    else -> BackupRestoreNotice.BACKUP_NOT_READABLE
}

private fun String.containsSizeFailure(): Boolean {
    val normalized = lowercase(Locale.US)
    return "size" in normalized || "large" in normalized || "exceed" in normalized
}

internal fun closePreparedOnClear(prepared: BackupPreparedRestore?): Boolean =
    prepared == null || runCatching { prepared.close() }.isSuccess

private inline fun <T> VaultSession?.useNullable(block: (VaultSession?) -> T): T =
    if (this == null) block(null) else use { block(it) }

private fun CharArray.clear() = fill('\u0000')
