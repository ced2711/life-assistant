package com.ced2711.lifetracker.ui.backup

import android.app.PendingIntent
import android.content.Intent
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ced2711.lifetracker.AppContainer
import com.ced2711.lifetracker.cloudsync.CloudRevision
import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.data.cloud.AndroidCloudSyncEngine
import com.ced2711.lifetracker.data.cloud.AndroidCloudSyncResult
import com.ced2711.lifetracker.data.cloud.CloudSyncPreferences
import com.ced2711.lifetracker.data.cloud.CloudSyncAttention
import com.ced2711.lifetracker.data.cloud.CloudSyncSecretStore
import com.ced2711.lifetracker.data.cloud.GoogleDriveAuthorization
import com.ced2711.lifetracker.data.cloud.GoogleDriveAuthorizationFailure
import com.ced2711.lifetracker.data.cloud.GoogleDriveAuthorizationResult
import com.ced2711.lifetracker.data.vault.VaultSession
import com.ced2711.lifetracker.worker.CloudSyncScheduler
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CloudSyncTask {
    NONE,
    CONNECTING,
    SYNCING,
    EXPORTING_RECOVERY,
}

data class CloudConsentRequest(
    val id: Long,
    val pendingIntent: PendingIntent,
    val dispatched: Boolean = false,
)

internal fun shouldDispatchCloudConsent(
    requestId: Long?,
    expectedRequestId: Long,
    alreadyDispatched: Boolean,
): Boolean = requestId == expectedRequestId && !alreadyDispatched

internal fun cloudConnectionFailureMessage(
    error: Throwable,
    authorizationComplete: Boolean,
): String {
    val authorizationFailure = error as? GoogleDriveAuthorizationFailure
    return when {
        authorizationFailure != null ->
            authorizationFailure.message ?: "Google Drive authorization failed."
        authorizationComplete ->
            "Google Drive connection failed while saving local credentials."
        else -> "Google Drive connection failed."
    }
}

data class CloudSyncUiState(
    val connected: Boolean = false,
    val automaticSync: Boolean = true,
    val task: CloudSyncTask = CloudSyncTask.NONE,
    val lastSyncAt: Long? = null,
    val attention: CloudSyncAttention? = null,
    val message: String? = null,
    /** Persistent connection/setup error; unlike [message], this is not dismissed by the snackbar. */
    val connectionError: String? = null,
    val conflict: CloudRevision? = null,
    val recoveryFiles: List<String> = emptyList(),
) {
    val busy: Boolean get() = task != CloudSyncTask.NONE
}

class CloudSyncViewModel internal constructor(
    private val engine: AndroidCloudSyncEngine,
    private val preferences: CloudSyncPreferences,
    private val secretStore: CloudSyncSecretStore,
    private val authorization: GoogleDriveAuthorization,
    private val scheduleAutomaticSync: (Boolean) -> Unit,
    private val contentResolver: ContentResolver,
) : ViewModel() {
    private val nonce = AtomicLong(System.nanoTime())
    private val _uiState = MutableStateFlow(readState())
    val uiState: StateFlow<CloudSyncUiState> = _uiState.asStateFlow()
    private val _consentRequest = MutableStateFlow<CloudConsentRequest?>(null)
    val consentRequest: StateFlow<CloudConsentRequest?> = _consentRequest.asStateFlow()
    private val _authenticationRequest = MutableStateFlow<BackupAuthenticationRequest?>(null)
    val authenticationRequest: StateFlow<BackupAuthenticationRequest?> =
        _authenticationRequest.asStateFlow()

    private var pendingPassword: CharArray? = null
    private var pendingResolution: ConflictResolution? = null
    private var pendingExpectedRemoteRevisionId: String? = null
    private var pendingReconnect = false

    /** Copies already-encrypted bytes only; the original private recovery file is never removed. */
    fun exportRecovery(fileName: String, destination: Uri) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(task = CloudSyncTask.EXPORTING_RECOVERY, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            var completed = false
            try {
                val source = engine.recoveryFiles().singleOrNull { it.name == fileName }
                    ?: error("Recovery copy is unavailable")
                source.inputStream().buffered().use { input ->
                    val output = contentResolver.openOutputStream(destination, "wt")
                        ?: error("Recovery export destination is unavailable")
                    output.buffered().use { input.copyTo(it) }
                }
                completed = true
                _uiState.update { it.copy(message = "Encrypted recovery copy exported. Restore it with the sync password used when it was created.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.update { it.copy(message = "Could not export the recovery copy. The private original was kept.") }
            } finally {
                if (!completed) runCatching { DocumentsContract.deleteDocument(contentResolver, destination) }
                _uiState.update { it.copy(task = CloudSyncTask.NONE) }
            }
        }
    }

    fun connect(password: CharArray) {
        if (password.size < MINIMUM_BACKUP_PASSWORD_LENGTH || _uiState.value.busy) {
            password.fill('\u0000')
            return
        }
        pendingPassword?.fill('\u0000')
        pendingPassword = password
        pendingReconnect = false
        _uiState.update {
            it.copy(task = CloudSyncTask.CONNECTING, message = null, connectionError = null)
        }
        viewModelScope.launch {
            var authorizationComplete = false
            try {
                when (val result = authorization.authorize()) {
                    is GoogleDriveAuthorizationResult.Authorized -> {
                        authorizationComplete = true
                        finishConnection()
                    }
                    is GoogleDriveAuthorizationResult.NeedsConsent -> {
                        _consentRequest.value = CloudConsentRequest(
                            nonce.incrementAndGet(),
                            result.pendingIntent,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                handleConnectionFailure(error, authorizationComplete)
            }
        }
    }

    fun markConsentDispatched(requestId: Long): Boolean {
        val request = _consentRequest.value ?: return false
        if (!shouldDispatchCloudConsent(request.id, requestId, request.dispatched)) return false
        _consentRequest.value = request.copy(dispatched = true)
        return true
    }

    fun consentLaunchFailed(requestId: Long, error: Throwable? = null) {
        if (_consentRequest.value?.id != requestId) return
        _consentRequest.value = null
        handleConnectionFailure(error ?: IllegalStateException("Consent launch failed"), false)
    }

    fun completeConsent(data: Intent?, resultCode: Int = android.app.Activity.RESULT_OK) {
        if (_consentRequest.value == null) return
        _consentRequest.value = null
        viewModelScope.launch {
            var authorizationComplete = false
            try {
                when (authorization.completeConsent(data, resultCode)) {
                    is GoogleDriveAuthorizationResult.Authorized -> {
                        authorizationComplete = true
                        if (pendingReconnect) finishReconnect() else finishConnection()
                    }
                    is GoogleDriveAuthorizationResult.NeedsConsent -> error("Authorization did not complete")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                handleConnectionFailure(error, authorizationComplete)
            }
        }
    }

    fun reconnect() {
        if (!_uiState.value.connected || _uiState.value.busy) return
        pendingReconnect = true
        _uiState.update {
            it.copy(task = CloudSyncTask.CONNECTING, message = null, connectionError = null)
        }
        viewModelScope.launch {
            var authorizationComplete = false
            try {
                when (val result = authorization.authorize()) {
                    is GoogleDriveAuthorizationResult.Authorized -> {
                        authorizationComplete = true
                        finishReconnect()
                    }
                    is GoogleDriveAuthorizationResult.NeedsConsent -> {
                        _consentRequest.value = CloudConsentRequest(
                            nonce.incrementAndGet(),
                            result.pendingIntent,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                handleConnectionFailure(error, authorizationComplete)
            }
        }
    }

    fun setAutomaticSync(enabled: Boolean) {
        if (!_uiState.value.connected) return
        preferences.setAutomaticSync(enabled)
        scheduleAutomaticSync(enabled)
        _uiState.update { it.copy(automaticSync = enabled) }
    }

    fun syncNow(resolution: ConflictResolution? = null) {
        if (!_uiState.value.connected || _uiState.value.busy) return
        val expectedRemoteRevisionId = if (resolution == null) null else {
            _uiState.value.conflict?.fileId ?: return
        }
        pendingResolution = resolution
        pendingExpectedRemoteRevisionId = expectedRemoteRevisionId
        launchSync(vaultSession = null)
    }

    fun markAuthenticationDispatched(requestNonce: Long): Boolean {
        val request = _authenticationRequest.value ?: return false
        if (request.nonce != requestNonce || request.status != BackupAuthenticationStatus.REQUESTED) return false
        _authenticationRequest.value = request.copy(status = BackupAuthenticationStatus.DISPATCHED)
        return true
    }

    fun markAuthenticationInProgress(requestNonce: Long) {
        val request = _authenticationRequest.value ?: return
        if (request.nonce == requestNonce && request.status != BackupAuthenticationStatus.REQUESTED) {
            _authenticationRequest.value = request.copy(status = BackupAuthenticationStatus.AUTHENTICATING)
        }
    }

    fun provideAuthenticatedLease(requestNonce: Long, lease: VaultSession) {
        val request = _authenticationRequest.value
        if (request?.nonce != requestNonce) {
            lease.close()
            return
        }
        _authenticationRequest.value = null
        launchSync(lease)
    }

    fun authenticationCancelled(requestNonce: Long) {
        if (_authenticationRequest.value?.nonce != requestNonce) return
        _authenticationRequest.value = null
        pendingResolution = null
        pendingExpectedRemoteRevisionId = null
        _uiState.update {
            it.copy(task = CloudSyncTask.NONE, message = "Vault authentication was cancelled.")
        }
    }

    fun authenticationReturnedLocked(requestNonce: Long) {
        val request = _authenticationRequest.value ?: return
        if (request.nonce == requestNonce && request.status == BackupAuthenticationStatus.AUTHENTICATING) {
            authenticationCancelled(requestNonce)
        }
    }

    fun disconnect() {
        if (_uiState.value.busy) return
        viewModelScope.launch { runCatching { authorization.disconnect() } }
        clearPendingPassword()
        pendingResolution = null
        pendingExpectedRemoteRevisionId = null
        _authenticationRequest.value = null
        _consentRequest.value = null
        secretStore.clear()
        preferences.clearConnection()
        scheduleAutomaticSync(false)
        _uiState.value = readState().copy(
            message = "Google Drive disconnected. Local data was kept.",
            connectionError = null,
        )
    }

    fun acknowledgeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun dismissConflict() {
        pendingResolution = null
        pendingExpectedRemoteRevisionId = null
        _uiState.update { it.copy(conflict = null) }
    }

    override fun onCleared() {
        clearPendingPassword()
        super.onCleared()
    }

    private suspend fun finishConnection() {
        val password = pendingPassword ?: error("Missing sync password")
        pendingPassword = null
        try {
            preferences.setEnabled(false)
            preferences.resetSyncLineage()
            secretStore.save(password)
            preferences.setEnabled(true)
            val settings = preferences.read()
            scheduleAutomaticSync(settings.automaticSync)
            _uiState.value = readState().copy(
                task = CloudSyncTask.NONE,
                message = "Google Drive connected.",
            )
            syncNow()
        } finally {
            password.fill('\u0000')
        }
    }

    private fun finishReconnect() {
        pendingReconnect = false
        preferences.resetSyncLineage()
        _uiState.value = readState().copy(
            task = CloudSyncTask.NONE,
            message = "Google Drive reconnected.",
        )
        syncNow()
    }

    private fun launchSync(vaultSession: VaultSession?) {
        _uiState.update { it.copy(task = CloudSyncTask.SYNCING, message = null, conflict = null) }
        viewModelScope.launch {
            try {
                vaultSession.useNullable { session ->
                    handleResult(
                        engine.synchronize(
                            vaultSession = session,
                            conflictResolution = pendingResolution,
                            expectedRemoteRevisionId = pendingExpectedRemoteRevisionId,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                pendingResolution = null
                pendingExpectedRemoteRevisionId = null
                preferences.setAttention(CloudSyncAttention.FAILED)
                _uiState.update { it.copy(task = CloudSyncTask.NONE, message = "Google Drive sync failed.") }
            }
        }
    }

    private fun handleResult(result: AndroidCloudSyncResult) {
        preferences.setAttention(
            when (result) {
                is AndroidCloudSyncResult.Conflict -> CloudSyncAttention.CONFLICT
                is AndroidCloudSyncResult.NeedsVaultUnlock -> CloudSyncAttention.VAULT_UNLOCK
                AndroidCloudSyncResult.NeedsGoogleConsent -> CloudSyncAttention.GOOGLE_CONSENT
                is AndroidCloudSyncResult.Failed -> CloudSyncAttention.FAILED
                else -> null
            },
        )
        when (result) {
            AndroidCloudSyncResult.Disabled ->
                _uiState.value = readState().copy(message = "Google Drive sync is disabled.")
            AndroidCloudSyncResult.UpToDate ->
                _uiState.value = readState().copy(message = "Already up to date.")
            is AndroidCloudSyncResult.Uploaded ->
                _uiState.value = readState().copy(message = "Encrypted backup uploaded.")
            is AndroidCloudSyncResult.Downloaded ->
                _uiState.value = readState().copy(message = "Cloud changes restored.")
            is AndroidCloudSyncResult.Conflict ->
                _uiState.value = readState().copy(
                    task = CloudSyncTask.NONE,
                    conflict = result.remote,
                    message = "Both this device and Google Drive changed.",
                )
            is AndroidCloudSyncResult.NeedsVaultUnlock -> {
                _uiState.update { it.copy(task = CloudSyncTask.SYNCING) }
                _authenticationRequest.value = BackupAuthenticationRequest(
                    nonce = nonce.incrementAndGet(),
                    purpose = BackupAuthenticationPurpose.CLOUD_SYNC,
                    status = BackupAuthenticationStatus.REQUESTED,
                )
            }
            AndroidCloudSyncResult.NeedsGoogleConsent ->
                _uiState.value = readState().copy(message = "Reconnect Google Drive to continue.")
            is AndroidCloudSyncResult.Failed ->
                _uiState.value = readState().copy(message = result.message)
        }
        if (result !is AndroidCloudSyncResult.NeedsVaultUnlock) {
            pendingResolution = null
            pendingExpectedRemoteRevisionId = null
        }
    }

    private fun readState(): CloudSyncUiState {
        val value = preferences.read()
        return CloudSyncUiState(
            connected = value.enabled && secretStore.hasSecret(),
            automaticSync = value.automaticSync,
            lastSyncAt = value.state.lastSyncAt,
            attention = value.attention,
            recoveryFiles = engine.recoveryFiles().map { it.name },
        )
    }

    private fun clearPendingPassword() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
    }

    private fun handleConnectionFailure(error: Throwable, authorizationComplete: Boolean) {
        clearPendingPassword()
        pendingReconnect = false
        val message = cloudConnectionFailureMessage(error, authorizationComplete)
        _uiState.update {
            it.copy(
                task = CloudSyncTask.NONE,
                message = message,
                connectionError = message,
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CloudSyncViewModel::class.java))
            return CloudSyncViewModel(
                engine = container.cloudSyncEngine,
                preferences = container.cloudSyncPreferences,
                secretStore = container.cloudSyncSecretStore,
                authorization = container.googleDriveAuthorization,
                contentResolver = container.appContext.contentResolver,
                scheduleAutomaticSync = { enabled ->
                    CloudSyncScheduler.update(container.appContext, enabled)
                },
            ) as T
        }
    }
}

private inline fun <T> VaultSession?.useNullable(block: (VaultSession?) -> T): T =
    if (this == null) block(null) else use { block(it) }
