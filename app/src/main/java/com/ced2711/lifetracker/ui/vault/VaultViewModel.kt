package com.ced2711.lifetracker.ui.vault

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ced2711.lifetracker.data.vault.VaultClipboard
import com.ced2711.lifetracker.data.vault.VaultCorruptKeyEnvelopeException
import com.ced2711.lifetracker.data.vault.VaultKeyInvalidatedException
import com.ced2711.lifetracker.data.vault.VaultKeyManager
import com.ced2711.lifetracker.data.vault.VaultKeyMissingException
import com.ced2711.lifetracker.data.vault.VaultRepository
import com.ced2711.lifetracker.data.vault.VaultSession
import com.ced2711.lifetracker.data.vault.VaultUnsupportedDeviceException
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.domain.model.VaultEntryDraft
import java.net.URI
import javax.crypto.Cipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VaultAccessState {
    data object Locked : VaultAccessState
    data object Unlocking : VaultAccessState
    data object Unlocked : VaultAccessState
    data class Error(val message: String) : VaultAccessState
}

enum class VaultAuthenticationPurpose {
    ACCESS,
    BACKUP,
    RESET,
    LEGACY_FINGERPRINT_ENROLLMENT,
    MODERN_UPGRADE,
}

sealed interface VaultAuthenticationRequest : AutoCloseable {
    val id: Long
    val purpose: VaultAuthenticationPurpose
    val allowedAuthenticators: Int

    data class Modern(
        override val id: Long,
        override val purpose: VaultAuthenticationPurpose,
        val preparation: VaultKeyManager.ModernPreparation,
    ) : VaultAuthenticationRequest {
        override val allowedAuthenticators: Int = preparation.allowedAuthenticators
        override fun close() = preparation.close()
    }

    data class LegacyBiometric(
        override val id: Long,
        override val purpose: VaultAuthenticationPurpose,
        val preparation: VaultKeyManager.LegacyBiometricPreparation,
    ) : VaultAuthenticationRequest {
        override val allowedAuthenticators: Int = preparation.allowedAuthenticators
        val cipher: Cipher get() = preparation.cipher
        override fun close() = preparation.close()
    }

    data class LegacyCredential(
        override val id: Long,
        override val purpose: VaultAuthenticationPurpose,
        val preparation: VaultKeyManager.LegacyCredentialPreparation,
    ) : VaultAuthenticationRequest {
        override val allowedAuthenticators: Int = preparation.allowedAuthenticators
        override fun close() = preparation.close()
    }

    data class SystemAuthentication(
        override val id: Long,
        override val purpose: VaultAuthenticationPurpose,
        override val allowedAuthenticators: Int,
    ) : VaultAuthenticationRequest {
        override fun close() = Unit
    }
}

data class VaultEditorState(
    val id: String? = null,
    val label: String = "",
    val account: String = "",
    val password: String = "",
    val website: String = "",
    val notes: String = "",
    val passwordVisible: Boolean = false,
) {
    val isValid: Boolean
        get() = listOf(label, account, password, website, notes).any(String::isNotBlank)

    fun toDraft(): VaultEntryDraft = VaultEntryDraft(
        id = id,
        label = label,
        account = account,
        password = password,
        website = website,
        notes = notes,
    )
}

data class VaultUiState(
    val access: VaultAccessState = VaultAccessState.Locked,
    val hasVault: Boolean = false,
    val entries: List<VaultEntry> = emptyList(),
    val filteredEntries: List<VaultEntry> = emptyList(),
    val query: String = "",
    val editor: VaultEditorState? = null,
    val mutationInProgress: Boolean = false,
    val authenticationRequest: VaultAuthenticationRequest? = null,
    val offerFingerprintEnrollment: Boolean = false,
    val offerModernUpgrade: Boolean = false,
    val snackbarMessage: String? = null,
)

/**
 * Owns all decrypted Vault state. No session, entry, or editor field is written to SavedState.
 * Authentication prompts are displayed by the UI and completed here using the matching request id.
 */
class VaultViewModel(
    private val repository: VaultRepository,
    private val keyManager: VaultKeyManager,
    private val clipboard: VaultClipboard,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val inactivityTimeoutMillis: Long = DEFAULT_INACTIVITY_TIMEOUT_MILLIS,
    private val searchDebounceMillis: Long = DEFAULT_SEARCH_DEBOUNCE_MILLIS,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        VaultUiState(hasVault = runCatching(keyManager::hasVault).getOrDefault(false)),
    )
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private var session: VaultSession? = null
    private var sessionSupervisor: Job? = null
    private var entriesJob: Job? = null
    private var filterJob: Job? = null
    private var filterGeneration = 0L
    private var mutationJob: Job? = null
    private val mutationOwner = VaultMutationOwner(System.nanoTime())
    private var inactivityJob: Job? = null
    private var authenticationJob: Job? = null
    private val authenticationOwner = VaultAuthenticationOwner<VaultAuthenticationRequest>(
        System.nanoTime(),
    )
    private val backupLeaseOwner = VaultBackupSessionLeaseOwner(System.nanoTime())
    private var backupAuthenticationGeneration: Long? = null
    private var lastCanUseStrongBiometric = false

    /** Starts creation or unlock. The UI supplies current BIOMETRIC_STRONG availability. */
    fun requestAccess(canUseStrongBiometric: Boolean) {
        lastCanUseStrongBiometric = canUseStrongBiometric
        invalidateBackupAuthentication()
        invalidateAuthentication()
        clearSession(clearSnackbar = false)
        val id = nextAuthenticationId()
        _uiState.update {
            it.copy(
                access = VaultAccessState.Unlocking,
                hasVault = safeHasVault(),
                entries = emptyList(),
                filteredEntries = emptyList(),
                query = "",
                editor = null,
                snackbarMessage = null,
            )
        }
        prepareAuthentication(id, VaultAuthenticationPurpose.ACCESS) {
            accessRequest(id, canUseStrongBiometric, VaultAuthenticationPurpose.ACCESS)
        }
    }

    /** Alias kept intentionally small for call sites that use an Unlock button. */
    fun unlock(canUseStrongBiometric: Boolean) = requestAccess(canUseStrongBiometric)

    /**
     * Invalidates any previous Vault session and starts a new system-authenticated access flow.
     * A successful flow grants exactly one caller-owned [VaultSession] lease via
     * [acquireBackupSessionLease].
     */
    fun requestFreshBackupAuthentication(canUseStrongBiometric: Boolean) {
        lastCanUseStrongBiometric = canUseStrongBiometric
        invalidateAuthentication()
        clearSession(clearSnackbar = false)
        val backupGeneration = backupLeaseOwner.beginFreshAuthentication()
        backupAuthenticationGeneration = backupGeneration
        val id = nextAuthenticationId()
        _uiState.update {
            it.copy(
                access = VaultAccessState.Unlocking,
                hasVault = safeHasVault(),
                entries = emptyList(),
                filteredEntries = emptyList(),
                query = "",
                editor = null,
                snackbarMessage = null,
            )
        }
        prepareAuthentication(id, VaultAuthenticationPurpose.BACKUP) {
            accessRequest(id, canUseStrongBiometric, VaultAuthenticationPurpose.BACKUP)
        }
    }

    /** Returns the single caller-owned lease created by the latest fresh backup authentication. */
    fun acquireBackupSessionLease(): VaultSession? {
        val currentSession = session ?: return null
        if (_uiState.value.access != VaultAccessState.Unlocked) return null
        return backupLeaseOwner.acquire(currentSession)?.also { touch() }
    }

    /** API 26-29 only: add a biometric envelope after a successful credential unlock. */
    fun requestFingerprintEnrollment() {
        if (_uiState.value.mutationInProgress) return
        val currentSession = session ?: return
        if (sdkInt >= Build.VERSION_CODES.R || keyManager.hasLegacyBiometricEnvelope()) return
        val id = beginEnrollmentRequest()
        prepareAuthentication(id, VaultAuthenticationPurpose.LEGACY_FINGERPRINT_ENROLLMENT) {
            VaultAuthenticationRequest.LegacyBiometric(
                id = id,
                purpose = VaultAuthenticationPurpose.LEGACY_FINGERPRINT_ENROLLMENT,
                preparation = keyManager.prepareLegacyBiometric(currentSession),
            )
        }
    }

    /** API 30+: add the modern envelope after unlocking an envelope created on Android 8-10. */
    fun requestModernUpgrade() {
        if (_uiState.value.mutationInProgress) return
        val currentSession = session ?: return
        if (sdkInt < Build.VERSION_CODES.R || keyManager.hasModernEnvelope()) return
        val id = beginEnrollmentRequest()
        prepareAuthentication(id, VaultAuthenticationPurpose.MODERN_UPGRADE) {
            VaultAuthenticationRequest.Modern(
                id = id,
                purpose = VaultAuthenticationPurpose.MODERN_UPGRADE,
                preparation = keyManager.prepareModern(currentSession),
            )
        }
    }

    /** Converts the negative action on a legacy fingerprint prompt into credential fallback. */
    fun useScreenLock(requestId: Long) {
        val request = takeAuthenticationRequest(requestId) as? VaultAuthenticationRequest.LegacyBiometric
            ?: return
        if (!request.purpose.isAccessAuthentication) {
            request.close()
            return
        }
        request.close()
        val id = nextAuthenticationId()
        _uiState.update { it.copy(access = VaultAccessState.Unlocking) }
        prepareAuthentication(id, request.purpose) {
            VaultAuthenticationRequest.LegacyCredential(
                id = id,
                purpose = request.purpose,
                preparation = keyManager.prepareLegacyCredential(),
            )
        }
    }

    fun completeModernAuthentication(requestId: Long) {
        val taken = takeAuthenticationRequest(requestId) ?: return
        val request = taken as? VaultAuthenticationRequest.Modern ?: run {
            taken.close()
            return
        }
        val route = if (request.preparation.vaultWasInitialized) {
            VaultAccessRoute.MODERN
        } else {
            VaultAccessRoute.CREATE_MODERN
        }
        completeAuthentication(request, route) {
            keyManager.completeModern(request.preparation)
        }
    }

    fun completeLegacyBiometricAuthentication(requestId: Long, authenticatedCipher: Cipher) {
        val taken = takeAuthenticationRequest(requestId) ?: return
        val request = taken as? VaultAuthenticationRequest.LegacyBiometric ?: run {
            taken.close()
            return
        }
        completeAuthentication(request, VaultAccessRoute.LEGACY_BIOMETRIC) {
            keyManager.completeLegacyBiometric(request.preparation, authenticatedCipher)
        }
    }

    fun completeLegacyCredentialAuthentication(requestId: Long) =
        completeDeviceCredentialAuthentication(requestId)

    fun completeDeviceCredentialAuthentication(requestId: Long) {
        val taken = takeAuthenticationRequest(requestId) ?: return
        when (taken) {
            is VaultAuthenticationRequest.LegacyCredential -> completeAuthentication(
                taken,
                VaultAccessRoute.LEGACY_CREDENTIAL,
            ) {
                keyManager.completeLegacyCredential(taken.preparation)
            }
            is VaultAuthenticationRequest.SystemAuthentication -> {
                if (taken.purpose == VaultAuthenticationPurpose.RESET) {
                    performAuthenticatedReset(taken)
                } else {
                    taken.close()
                }
            }
            else -> taken.close()
        }
    }

    fun completeSystemAuthentication(requestId: Long) {
        val taken = takeAuthenticationRequest(requestId) ?: return
        val request = taken as? VaultAuthenticationRequest.SystemAuthentication ?: run {
            taken.close()
            return
        }
        if (request.purpose != VaultAuthenticationPurpose.RESET) {
            request.close()
            return
        }
        performAuthenticatedReset(request)
    }

    /** Prompt cancellation is not an error and always returns an access attempt to Locked. */
    fun authenticationCancelled(requestId: Long) {
        val request = takeAuthenticationRequest(requestId) ?: return
        request.close()
        if (request.purpose.isAccessAuthentication ||
            request.purpose == VaultAuthenticationPurpose.RESET
        ) {
            if (request.purpose == VaultAuthenticationPurpose.BACKUP) {
                invalidateBackupAuthentication()
            }
            _uiState.update { it.copy(access = VaultAccessState.Locked) }
        } else {
            _uiState.update { it.copy(access = VaultAccessState.Unlocked) }
            touch()
        }
    }

    fun authenticationUnavailable(requestId: Long, message: String) {
        val request = takeAuthenticationRequest(requestId) ?: return
        request.close()
        if (request.purpose.isAccessAuthentication) {
            val failedRoute = request.accessRoute(safeHasVault())
            if (startAccessFallback(request.purpose, failedRoute, emptyList(), message)) return
        }
        failAuthentication(request.purpose, message)
    }

    fun authenticationError(requestId: Long, message: String) {
        failAuthenticationRequest(requestId, message)
    }

    /** Called by pointer/key/editor interaction; decrypted state auto-locks after one idle minute. */
    fun touch() {
        if (session == null || _uiState.value.access != VaultAccessState.Unlocked) return
        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch {
            delay(inactivityTimeoutMillis.coerceAtLeast(1L))
            lock()
        }
    }

    /** Host calls this for explicit Lock and whenever the Vault destination is left. */
    fun lock(clearOwnedClipboard: Boolean = true) {
        invalidateBackupAuthentication()
        invalidateAuthentication()
        clearSession(clearSnackbar = true)
        if (clearOwnedClipboard) clipboard.onVaultLocked()
        _uiState.update {
            VaultUiState(
                access = VaultAccessState.Locked,
                hasVault = safeHasVault(),
            )
        }
    }

    fun setQuery(value: String) {
        requireUnlocked()
        if (_uiState.value.mutationInProgress) return
        _uiState.update { it.copy(query = value) }
        scheduleEntryFilter(debounce = true)
        touch()
    }

    fun addEntry() {
        requireUnlocked()
        if (_uiState.value.mutationInProgress) return
        _uiState.update { it.copy(editor = VaultEditorState()) }
        touch()
    }

    fun editEntry(id: String) {
        requireUnlocked()
        if (_uiState.value.mutationInProgress) return
        val entry = _uiState.value.entries.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(
                editor = VaultEditorState(
                    id = entry.id,
                    label = entry.label,
                    account = entry.account,
                    password = entry.password,
                    website = entry.website,
                    notes = entry.notes,
                ),
            )
        }
        touch()
    }

    fun closeEditor() {
        if (_uiState.value.mutationInProgress) return
        _uiState.update { it.copy(editor = null) }
        touch()
    }

    fun updateLabel(value: String) = updateEditor { copy(label = value) }
    fun updateAccount(value: String) = updateEditor { copy(account = value) }
    fun updatePassword(value: String) = updateEditor { copy(password = value) }
    fun updateWebsite(value: String) = updateEditor { copy(website = value) }
    fun updateNotes(value: String) = updateEditor { copy(notes = value) }
    fun setPasswordVisible(visible: Boolean) = updateEditor { copy(passwordVisible = visible) }

    fun saveEditor() {
        val currentSession = session ?: return
        val editor = _uiState.value.editor ?: return
        if (!editor.isValid) {
            showMessage("Enter at least one field.")
            return
        }
        val mutation = beginMutation() ?: return
        val job = sessionLaunch {
            try {
                val result = try {
                    Result.success(
                        withContext(ioDispatcher) {
                            repository.save(editor.toDraft().withDerivedLabel(), currentSession)
                        },
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Result.failure(error)
                }
                if (!isCurrentMutation(mutation, currentSession)) return@sessionLaunch
                result.onSuccess { saved ->
                    _uiState.update {
                        it.copy(
                            editor = VaultEditorState(
                                id = saved.id,
                                label = saved.label,
                                account = saved.account,
                                password = saved.password,
                                website = saved.website,
                                notes = saved.notes,
                            ),
                            snackbarMessage = "Saved.",
                        )
                    }
                    touch()
                }.onFailure(::showFailure)
            } finally {
                finishMutation(mutation)
            }
        }
        if (job == null) {
            finishMutation(mutation)
        } else if (mutationOwner.isCurrent(mutation)) {
            mutationJob = job
        }
    }

    fun deleteEntry(id: String) {
        val currentSession = session ?: return
        val mutation = beginMutation() ?: return
        val job = sessionLaunch {
            try {
                val result = try {
                    Result.success(withContext(ioDispatcher) { repository.delete(id) })
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Result.failure(error)
                }
                if (!isCurrentMutation(mutation, currentSession)) return@sessionLaunch
                result
                    .onSuccess { deleted ->
                        if (!deleted) {
                            showMessage("This entry no longer exists.")
                        } else {
                            _uiState.update {
                                it.copy(
                                    editor = it.editor?.takeUnless { editor -> editor.id == id },
                                    snackbarMessage = "Deleted.",
                                )
                            }
                        }
                        touch()
                    }
                    .onFailure(::showFailure)
            } finally {
                finishMutation(mutation)
            }
        }
        if (job == null) {
            finishMutation(mutation)
        } else if (mutationOwner.isCurrent(mutation)) {
            mutationJob = job
        }
    }

    /** UI confirmation is followed by fresh system authentication before deletion can begin. */
    fun resetVault() {
        if (_uiState.value.mutationInProgress) return
        invalidateBackupAuthentication()
        invalidateAuthentication()
        clearSession(clearSnackbar = false)
        val id = nextAuthenticationId()
        _uiState.update { it.copy(access = VaultAccessState.Unlocking, snackbarMessage = null) }
        prepareAuthentication(id, VaultAuthenticationPurpose.RESET) {
            resetAuthenticationRequest(id, sdkInt)
        }
    }

    fun copyEditorAccount() {
        if (_uiState.value.mutationInProgress) return
        val account = _uiState.value.editor?.account.orEmpty()
        if (account.isBlank()) return
        clipboard.copyAccount(account)
        showMessage("Account copied. Clears after 30 seconds, or on return if backgrounded.")
        touch()
    }

    fun copyEditorPassword() {
        if (_uiState.value.mutationInProgress) return
        val password = _uiState.value.editor?.password.orEmpty()
        if (password.isBlank()) return
        clipboard.copyPassword(password)
        showMessage("Password copied. Clears after 30 seconds, or on return if backgrounded.")
        touch()
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        lock()
        super.onCleared()
    }

    class Factory(
        private val repository: VaultRepository,
        private val keyManager: VaultKeyManager,
        private val clipboard: VaultClipboard,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(VaultViewModel::class.java))
            return VaultViewModel(repository, keyManager, clipboard) as T
        }
    }

    private fun accessRequest(
        id: Long,
        canUseStrongBiometric: Boolean,
        purpose: VaultAuthenticationPurpose,
    ): VaultAuthenticationRequest {
        check(purpose.isAccessAuthentication)
        val routes = vaultAccessRoutes(accessFacts(canUseStrongBiometric))
        return prepareFirstAvailableAccess(id, purpose, routes)
    }

    private fun prepareFirstAvailableAccess(
        id: Long,
        purpose: VaultAuthenticationPurpose,
        routes: List<VaultAccessRoute>,
        alreadyBroken: List<VaultAccessRoute> = emptyList(),
    ): VaultAuthenticationRequest {
        if (routes.isEmpty()) throw noUsableEnvelopeError()
        val brokenRoutes = alreadyBroken.toMutableList()
        var lastFailure: Throwable? = null

        routes.forEachIndexed { index, route ->
            try {
                val request = prepareAccessRoute(id, purpose, route)
                brokenRoutes.distinct().forEach(::discardBrokenRouteBestEffort)
                return request
            } catch (error: Throwable) {
                lastFailure = error
                val hasFallback = index < routes.lastIndex
                if (!hasFallback || !isRecoverablePreferredEnvelopeFailure(error)) throw error
                if (route.canBeDiscardedAfterRecovery) brokenRoutes += route
            }
        }
        throw lastFailure ?: noUsableEnvelopeError()
    }

    private fun prepareAccessRoute(
        id: Long,
        purpose: VaultAuthenticationPurpose,
        route: VaultAccessRoute,
    ): VaultAuthenticationRequest = when (route) {
        VaultAccessRoute.MODERN,
        VaultAccessRoute.CREATE_MODERN,
        -> VaultAuthenticationRequest.Modern(
            id,
            purpose,
            keyManager.prepareModern(),
        )
        VaultAccessRoute.LEGACY_BIOMETRIC -> VaultAuthenticationRequest.LegacyBiometric(
            id,
            purpose,
            keyManager.prepareLegacyBiometric(),
        )
        VaultAccessRoute.LEGACY_CREDENTIAL,
        VaultAccessRoute.CREATE_LEGACY_CREDENTIAL,
        -> VaultAuthenticationRequest.LegacyCredential(
            id,
            purpose,
            keyManager.prepareLegacyCredential(),
        )
    }

    private fun startAccessFallback(
        purpose: VaultAuthenticationPurpose,
        failedRoute: VaultAccessRoute,
        brokenRoutes: List<VaultAccessRoute>,
        originalMessage: String,
    ): Boolean {
        check(purpose.isAccessAuthentication)
        val allRoutes = vaultAccessRoutes(accessFacts(lastCanUseStrongBiometric))
        val remainingRoutes = routesAfter(allRoutes, failedRoute)
        if (remainingRoutes.isEmpty()) return false

        val id = nextAuthenticationId()
        _uiState.update {
            it.copy(
                access = VaultAccessState.Unlocking,
                authenticationRequest = null,
                snackbarMessage = if (originalMessage.isBlank()) null else originalMessage,
            )
        }
        prepareAuthentication(id, purpose) {
            prepareFirstAvailableAccess(id, purpose, remainingRoutes, brokenRoutes)
        }
        return true
    }

    private fun accessFacts(canUseStrongBiometric: Boolean) = VaultAccessFacts(
        sdkInt = sdkInt,
        hasVault = keyManager.hasVault(),
        hasModernEnvelope = keyManager.hasModernEnvelope(),
        hasLegacyBiometricEnvelope = keyManager.hasLegacyBiometricEnvelope(),
        hasLegacyCredentialEnvelope = keyManager.hasLegacyCredentialEnvelope(),
        canUseStrongBiometric = canUseStrongBiometric,
    )

    private fun discardBrokenRouteBestEffort(route: VaultAccessRoute) {
        runCatching {
            when (route) {
                VaultAccessRoute.MODERN -> keyManager.discardModernEnvelope()
                VaultAccessRoute.LEGACY_BIOMETRIC -> keyManager.discardLegacyBiometricEnvelope()
                else -> Unit
            }
        }
    }

    private fun noUsableEnvelopeError() = IllegalStateException(
        "No usable vault key envelope remains. Reset the vault to start over.",
    )

    private fun beginEnrollmentRequest(): Long {
        invalidateBackupAuthentication()
        invalidateAuthentication()
        val id = nextAuthenticationId()
        _uiState.update { it.copy(authenticationRequest = null, snackbarMessage = null) }
        return id
    }

    private fun prepareAuthentication(
        id: Long,
        purpose: VaultAuthenticationPurpose,
        preparation: () -> VaultAuthenticationRequest,
    ) {
        authenticationJob = viewModelScope.launch(ioDispatcher) {
            var preparedRequest: VaultAuthenticationRequest? = null
            try {
                preparedRequest = preparation()
                withContext(Dispatchers.Main.immediate) {
                    val request = preparedRequest ?: return@withContext
                    val eligible = purpose.isAccessAuthentication ||
                        purpose == VaultAuthenticationPurpose.RESET || session != null
                    val published = authenticationOwner.publish(id, eligible, request)
                    preparedRequest = null
                    if (published) {
                        _uiState.update { it.copy(authenticationRequest = request) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                withContext(Dispatchers.Main.immediate) {
                    if (authenticationOwner.isCurrent(id)) {
                        failAuthentication(purpose, error.userMessage())
                    }
                }
            } finally {
                preparedRequest?.close()
            }
        }
    }

    private fun completeAuthentication(
        request: VaultAuthenticationRequest,
        accessRoute: VaultAccessRoute,
        completion: () -> VaultSession,
    ) {
        val completionGeneration = authenticationOwner.generation
        authenticationJob = viewModelScope.launch(ioDispatcher) {
            var unlockedSession: VaultSession? = null
            try {
                unlockedSession = completion()
                val offerFingerprintEnrollment = sdkInt < Build.VERSION_CODES.R &&
                    !keyManager.hasLegacyBiometricEnvelope()
                val offerModernUpgrade = sdkInt >= Build.VERSION_CODES.R &&
                    !keyManager.hasModernEnvelope()
                withContext(Dispatchers.Main.immediate) {
                    if (!authenticationOwner.isCurrent(completionGeneration)) return@withContext
                    val acceptedSession = unlockedSession ?: return@withContext
                    unlockedSession = null
                    acceptSession(
                        acceptedSession,
                        preserveEditor = !request.purpose.isAccessAuthentication,
                        offerFingerprintEnrollment = offerFingerprintEnrollment,
                        offerModernUpgrade = offerModernUpgrade,
                    )
                    if (request.purpose == VaultAuthenticationPurpose.BACKUP) {
                        val generation = backupAuthenticationGeneration
                        if (generation == null ||
                            !backupLeaseOwner.markAuthenticated(generation, acceptedSession)
                        ) {
                            clearSession(clearSnackbar = false)
                            failAuthentication(
                                VaultAuthenticationPurpose.BACKUP,
                                "Backup authentication expired. Try again.",
                            )
                            return@withContext
                        }
                        backupAuthenticationGeneration = null
                    } else {
                        invalidateBackupAuthentication()
                    }
                    if (request.purpose == VaultAuthenticationPurpose.LEGACY_FINGERPRINT_ENROLLMENT) {
                        showMessage("Fingerprint unlock enabled.")
                    } else if (request.purpose == VaultAuthenticationPurpose.MODERN_UPGRADE) {
                        showMessage("Vault security upgraded.")
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                withContext(Dispatchers.Main.immediate) {
                    if (!authenticationOwner.isCurrent(completionGeneration)) return@withContext
                    if (request.purpose.isAccessAuthentication &&
                        isRecoverablePreferredEnvelopeFailure(error) &&
                        startAccessFallback(
                            purpose = request.purpose,
                            failedRoute = accessRoute,
                            brokenRoutes = listOf(accessRoute).filter(
                                VaultAccessRoute::canBeDiscardedAfterRecovery,
                            ),
                            originalMessage = error.userMessage(),
                        )
                    ) {
                        return@withContext
                    }
                    failAuthentication(request.purpose, error.userMessage())
                }
            } finally {
                unlockedSession?.close()
                request.close()
            }
        }
    }

    private fun performAuthenticatedReset(
        request: VaultAuthenticationRequest.SystemAuthentication,
    ) {
        val completionGeneration = authenticationOwner.generation
        authenticationJob = viewModelScope.launch(ioDispatcher) {
            try {
                withContext(NonCancellable) { repository.reset() }
                withContext(Dispatchers.Main.immediate) {
                    if (!authenticationOwner.isCurrent(completionGeneration)) return@withContext
                    clipboard.reset()
                    lock()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                withContext(Dispatchers.Main.immediate) {
                    if (!authenticationOwner.isCurrent(completionGeneration)) return@withContext
                    failAuthentication(VaultAuthenticationPurpose.RESET, error.userMessage())
                }
            } finally {
                request.close()
            }
        }
    }

    private fun acceptSession(
        unlockedSession: VaultSession,
        preserveEditor: Boolean,
        offerFingerprintEnrollment: Boolean,
        offerModernUpgrade: Boolean,
    ) {
        val previousEditor = _uiState.value.editor.takeIf { preserveEditor }
        clearSession(clearSnackbar = false)
        session = unlockedSession
        val supervisor = SupervisorJob(viewModelScope.coroutineContext[Job])
        sessionSupervisor = supervisor
        val scope = CoroutineScope(viewModelScope.coroutineContext + supervisor)
        _uiState.update {
            it.copy(
                access = VaultAccessState.Unlocked,
                hasVault = true,
                entries = emptyList(),
                filteredEntries = emptyList(),
                editor = previousEditor,
                mutationInProgress = false,
                authenticationRequest = null,
                offerFingerprintEnrollment = offerFingerprintEnrollment,
                offerModernUpgrade = offerModernUpgrade,
            )
        }
        entriesJob = scope.launch {
            runCatching {
                repository.observeEntries(unlockedSession).collect { entries ->
                    if (session === unlockedSession) {
                        _uiState.update { it.copy(entries = entries) }
                        scheduleEntryFilter(debounce = false)
                    }
                }
            }.onFailure { error ->
                if (session === unlockedSession) {
                    clearSession(clearSnackbar = false)
                    _uiState.update {
                        it.copy(
                            access = VaultAccessState.Error(error.userMessage()),
                            entries = emptyList(),
                            filteredEntries = emptyList(),
                            editor = null,
                            offerFingerprintEnrollment = false,
                            offerModernUpgrade = false,
                        )
                    }
                }
            }
        }
        touch()
    }

    private fun failAuthenticationRequest(requestId: Long, message: String) {
        val request = takeAuthenticationRequest(requestId) ?: return
        request.close()
        failAuthentication(request.purpose, message)
    }

    private fun failAuthentication(purpose: VaultAuthenticationPurpose, message: String) {
        if (purpose.isAccessAuthentication ||
            purpose == VaultAuthenticationPurpose.RESET
        ) {
            if (purpose == VaultAuthenticationPurpose.BACKUP) {
                invalidateBackupAuthentication()
            }
            clearSession(clearSnackbar = false)
            _uiState.update {
                it.copy(
                    access = VaultAccessState.Error(message),
                    authenticationRequest = null,
                    entries = emptyList(),
                    filteredEntries = emptyList(),
                    editor = null,
                    snackbarMessage = message,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    access = VaultAccessState.Unlocked,
                    authenticationRequest = null,
                    snackbarMessage = message,
                )
            }
            touch()
        }
    }

    private fun takeAuthenticationRequest(requestId: Long): VaultAuthenticationRequest? {
        val request = authenticationOwner.take(requestId) ?: return null
        authenticationJob = null
        _uiState.update { it.copy(authenticationRequest = null) }
        return request
    }

    private fun invalidateAuthentication() {
        val obsoleteRequest = authenticationOwner.invalidate()
        authenticationJob?.cancel()
        authenticationJob = null
        obsoleteRequest?.close()
        _uiState.update { it.copy(authenticationRequest = null) }
    }

    private fun invalidateBackupAuthentication() {
        backupAuthenticationGeneration = null
        backupLeaseOwner.invalidate()
    }

    private fun nextAuthenticationId(): Long = authenticationOwner.nextId()

    private fun clearSession(clearSnackbar: Boolean) {
        cancelMutation()
        filterGeneration++
        filterJob?.cancel()
        filterJob = null
        inactivityJob?.cancel()
        inactivityJob = null
        entriesJob?.cancel()
        entriesJob = null
        sessionSupervisor?.cancel()
        sessionSupervisor = null
        session?.close()
        session = null
        _uiState.update {
            it.copy(
                entries = emptyList(),
                filteredEntries = emptyList(),
                query = "",
                editor = null,
                mutationInProgress = false,
                offerFingerprintEnrollment = false,
                offerModernUpgrade = false,
                snackbarMessage = if (clearSnackbar) null else it.snackbarMessage,
            )
        }
    }

    private fun sessionLaunch(block: suspend CoroutineScope.() -> Unit): Job? {
        val supervisor = sessionSupervisor ?: return null
        return CoroutineScope(viewModelScope.coroutineContext + supervisor).launch(block = block)
    }

    private fun updateEditor(transform: VaultEditorState.() -> VaultEditorState) {
        if (session == null || _uiState.value.mutationInProgress) return
        _uiState.update { state -> state.copy(editor = state.editor?.transform()) }
        touch()
    }

    private fun scheduleEntryFilter(debounce: Boolean) {
        val currentSession = session ?: return
        val state = _uiState.value
        val entries = state.entries
        val query = state.query
        val generation = ++filterGeneration
        filterJob?.cancel()
        filterJob = viewModelScope.launch(searchDispatcher) {
            if (debounce) delay(searchDebounceMillis.coerceAtLeast(0L))
            val filteringJob = coroutineContext[Job]
            val filtered = filteredVaultEntries(entries, query) {
                if (filteringJob?.isActive == false) throw CancellationException()
            }
            withContext(Dispatchers.Main.immediate) {
                if (session !== currentSession || filterGeneration != generation) return@withContext
                _uiState.update { it.copy(filteredEntries = filtered) }
                filterJob = null
            }
        }
    }

    private fun beginMutation(): Long? {
        if (session == null || _uiState.value.access != VaultAccessState.Unlocked) return null
        val mutation = mutationOwner.begin() ?: return null
        _uiState.update { it.copy(mutationInProgress = true) }
        return mutation
    }

    private fun isCurrentMutation(mutation: Long, expectedSession: VaultSession): Boolean =
        session === expectedSession && mutationOwner.isCurrent(mutation)

    private fun finishMutation(mutation: Long) {
        if (!mutationOwner.finish(mutation)) return
        mutationJob = null
        _uiState.update { it.copy(mutationInProgress = false) }
    }

    private fun cancelMutation() {
        mutationOwner.invalidate()
        mutationJob?.cancel()
        mutationJob = null
        _uiState.update { it.copy(mutationInProgress = false) }
    }

    private fun requireUnlocked() {
        check(session != null && _uiState.value.access == VaultAccessState.Unlocked) {
            "Vault is locked."
        }
    }

    private fun showFailure(error: Throwable) = showMessage(error.userMessage())

    private fun showMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    private fun safeHasVault(): Boolean = runCatching(keyManager::hasVault).getOrDefault(false)

    private fun Throwable.userMessage(): String = message
        ?.takeIf(String::isNotBlank)
        ?: "The vault operation failed."

    companion object {
        const val DEFAULT_INACTIVITY_TIMEOUT_MILLIS = 60_000L
        const val DEFAULT_SEARCH_DEBOUNCE_MILLIS = 150L
    }
}

internal fun VaultEntry.displayLabel(): String = label.trim().ifBlank {
    deriveVaultLabel(website = website, account = account, notes = notes)
}

internal fun VaultEntryDraft.withDerivedLabel(): VaultEntryDraft = if (label.isBlank()) {
    copy(label = deriveVaultLabel(website = website, account = account, notes = notes))
} else {
    this
}

/** Derives a short, editable label without ever inspecting the password field. */
internal fun deriveVaultLabel(website: String, account: String, notes: String): String {
    val candidate = websiteHostLabel(website)
        ?: account.trim().takeIf(String::isNotEmpty)
        ?: notes.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
        ?: "Untitled"
    return candidate.takeCodePoints(MAX_DERIVED_LABEL_CODE_POINTS)
}

private fun websiteHostLabel(value: String): String? {
    val website = value.trim()
    if (website.isEmpty()) return null
    val normalized = if (SCHEME_PATTERN.containsMatchIn(website)) website else "https://$website"
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    val authorityHost = uri.rawAuthority
        ?.substringAfterLast('@')
        ?.let { authority ->
            if (authority.startsWith("[")) {
                authority.substringBefore(']', missingDelimiterValue = "").takeIf(String::isNotEmpty)
                    ?.plus("]")
            } else {
                authority.substringBefore(':')
            }
        }
    val host = uri.host?.takeIf(String::isNotBlank)
        ?: authorityHost?.takeIf(String::isNotBlank)
        ?: return null
    return host.removePrefixIgnoringCase("www.").takeIf(String::isNotBlank)
}

private fun String.removePrefixIgnoringCase(prefix: String): String =
    if (startsWith(prefix, ignoreCase = true)) drop(prefix.length) else this

private fun String.takeCodePoints(limit: Int): String {
    if (codePointCount(0, length) <= limit) return this
    return substring(0, offsetByCodePoints(0, limit)).trimEnd()
}

internal fun filteredVaultEntries(entries: List<VaultEntry>, query: String): List<VaultEntry> =
    filteredVaultEntries(entries, query, checkCancellation = {})

private fun filteredVaultEntries(
    entries: List<VaultEntry>,
    query: String,
    checkCancellation: () -> Unit,
): List<VaultEntry> {
    val term = query.trim()
    val matches = ArrayList<Pair<VaultEntry, String>>(entries.size)
    entries.forEach { entry ->
        checkCancellation()
        val included = term.isBlank() ||
            entry.label.contains(term, ignoreCase = true) ||
            entry.account.contains(term, ignoreCase = true) ||
            entry.website.contains(term, ignoreCase = true) ||
            entry.notes.contains(term, ignoreCase = true)
        if (included) matches += entry to entry.displayLabel()
    }
    matches.sortWith { left, right ->
        checkCancellation()
        String.CASE_INSENSITIVE_ORDER.compare(left.second, right.second)
    }
    return matches.map { (entry, _) ->
        checkCancellation()
        entry
    }
}

/** A single-flight generation token that rejects double starts and stale CRUD completions. */
internal class VaultMutationOwner(initialGeneration: Long = 0L) {
    var generation: Long = initialGeneration
        private set
    private var active: Long? = null

    @Synchronized
    fun begin(): Long? {
        if (active != null) return null
        generation++
        return generation.also { active = it }
    }

    @Synchronized
    fun isCurrent(token: Long): Boolean = active == token

    @Synchronized
    fun finish(token: Long): Boolean {
        if (active != token) return false
        active = null
        return true
    }

    @Synchronized
    fun invalidate() {
        generation++
        active = null
    }
}

private val SCHEME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
private const val MAX_DERIVED_LABEL_CODE_POINTS = 80

internal enum class VaultAccessRoute {
    CREATE_MODERN,
    CREATE_LEGACY_CREDENTIAL,
    MODERN,
    LEGACY_BIOMETRIC,
    LEGACY_CREDENTIAL,
}

internal data class VaultAccessFacts(
    val sdkInt: Int,
    val hasVault: Boolean,
    val hasModernEnvelope: Boolean,
    val hasLegacyBiometricEnvelope: Boolean,
    val hasLegacyCredentialEnvelope: Boolean,
    val canUseStrongBiometric: Boolean,
)

/** Pure, injectable access policy used by the ViewModel and unit tests. */
internal fun vaultAccessRoutes(facts: VaultAccessFacts): List<VaultAccessRoute> {
    if (!facts.hasVault) {
        return listOf(
            if (facts.sdkInt >= Build.VERSION_CODES.R) {
                VaultAccessRoute.CREATE_MODERN
            } else {
                VaultAccessRoute.CREATE_LEGACY_CREDENTIAL
            },
        )
    }
    return buildList {
        if (facts.sdkInt >= Build.VERSION_CODES.R && facts.hasModernEnvelope) {
            add(VaultAccessRoute.MODERN)
        }
        if (facts.hasLegacyBiometricEnvelope && facts.canUseStrongBiometric) {
            add(VaultAccessRoute.LEGACY_BIOMETRIC)
        }
        if (facts.hasLegacyCredentialEnvelope) {
            add(VaultAccessRoute.LEGACY_CREDENTIAL)
        }
    }
}

internal fun routesAfter(
    routes: List<VaultAccessRoute>,
    failedRoute: VaultAccessRoute,
): List<VaultAccessRoute> {
    val failedIndex = routes.indexOf(failedRoute)
    return if (failedIndex < 0 || failedIndex == routes.lastIndex) {
        emptyList()
    } else {
        routes.drop(failedIndex + 1)
    }
}

internal fun isRecoverablePreferredEnvelopeFailure(error: Throwable): Boolean = when (error) {
    is VaultKeyInvalidatedException,
    is VaultKeyMissingException,
    is VaultCorruptKeyEnvelopeException,
    is VaultUnsupportedDeviceException,
    -> true
    else -> false
}

internal fun resetAuthenticatorsForSdk(sdkInt: Int): Int =
    if (sdkInt >= Build.VERSION_CODES.R) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

internal fun resetAuthenticationRequest(
    id: Long,
    sdkInt: Int,
): VaultAuthenticationRequest.SystemAuthentication =
    VaultAuthenticationRequest.SystemAuthentication(
        id = id,
        purpose = VaultAuthenticationPurpose.RESET,
        allowedAuthenticators = resetAuthenticatorsForSdk(sdkInt),
    )

internal fun VaultAuthenticationRequest.usesExternalDeviceCredentialPrompt(
    sdkInt: Int,
): Boolean = when (this) {
    is VaultAuthenticationRequest.LegacyCredential -> true
    is VaultAuthenticationRequest.SystemAuthentication ->
        purpose == VaultAuthenticationPurpose.RESET && sdkInt < Build.VERSION_CODES.R
    is VaultAuthenticationRequest.Modern,
    is VaultAuthenticationRequest.LegacyBiometric,
    -> false
}

internal val VaultAuthenticationPurpose.isAccessAuthentication: Boolean
    get() = this == VaultAuthenticationPurpose.ACCESS ||
        this == VaultAuthenticationPurpose.BACKUP

/** Accessed only from the ViewModel owner context; background jobs return through that context. */
internal class VaultAuthenticationOwner<T : AutoCloseable>(initialGeneration: Long) {
    var generation: Long = initialGeneration
        private set
    private var request: T? = null

    fun nextId(): Long = ++generation

    fun publish(id: Long, eligible: Boolean, prepared: T): Boolean {
        if (id != generation || !eligible || request != null) {
            prepared.close()
            return false
        }
        request = prepared
        return true
    }

    fun take(id: Long): T? {
        val current = request ?: return null
        if (id != generation) return null
        request = null
        generation++
        return current
    }

    fun invalidate(): T? {
        generation++
        return request.also { request = null }
    }

    fun isCurrent(idOrGeneration: Long): Boolean = generation == idOrGeneration
}

private val VaultAccessRoute.canBeDiscardedAfterRecovery: Boolean
    get() = this == VaultAccessRoute.MODERN || this == VaultAccessRoute.LEGACY_BIOMETRIC

private fun VaultAuthenticationRequest.accessRoute(hasVault: Boolean): VaultAccessRoute = when (this) {
    is VaultAuthenticationRequest.Modern -> if (hasVault) {
        VaultAccessRoute.MODERN
    } else {
        VaultAccessRoute.CREATE_MODERN
    }
    is VaultAuthenticationRequest.LegacyBiometric -> VaultAccessRoute.LEGACY_BIOMETRIC
    is VaultAuthenticationRequest.LegacyCredential -> if (hasVault) {
        VaultAccessRoute.LEGACY_CREDENTIAL
    } else {
        VaultAccessRoute.CREATE_LEGACY_CREDENTIAL
    }
    is VaultAuthenticationRequest.SystemAuthentication -> error(
        "System-only authentication does not unlock a vault envelope.",
    )
}
