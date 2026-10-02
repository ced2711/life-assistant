package com.ced2711.lifetracker.ui.vault

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.translateUiText

/*
 * Shows the system prompts (fingerprint, face, screen lock) the Vault asks for and hands the
 * result back to the view model. No layout lives here; the screen is in VaultScreen.kt.
 */

@Composable
internal fun VaultAuthenticationCoordinator(
    viewModel: VaultViewModel,
    request: VaultAuthenticationRequest?,
    hasVault: Boolean,
    deviceSecure: Boolean,
) {
    val context = LocalContext.current
    val uiLanguage = LocalUiLanguage.current
    val activity = remember(context) { context.findFragmentActivity() }
    val legacyCredentialFragment = remember(activity) {
        activity?.getOrCreateVaultLegacyCredentialFragment()
    }
    var savedBiometricRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
    var savedBiometricGeneration by rememberSaveable { mutableLongStateOf(0L) }
    val biometricAttempts = remember {
        VaultBiometricAttemptRegistry(
            initialGeneration = savedBiometricGeneration,
            initialActive = savedBiometricRequestId?.let {
                VaultBiometricAttempt(it, savedBiometricGeneration)
            },
        )
    }
    var attachedBiometricAttempt by remember { mutableStateOf<VaultBiometricAttempt?>(null) }
    var activeBiometricPrompt by remember { mutableStateOf<BiometricPrompt?>(null) }

    val clearBiometricAttempt: (VaultBiometricAttempt) -> Unit = { attempt ->
        if (biometricAttempts.finish(attempt)) {
            activeBiometricPrompt = null
            attachedBiometricAttempt = null
            savedBiometricRequestId = null
            savedBiometricGeneration = biometricAttempts.generation
        }
    }
    val cancelActiveBiometric: () -> Unit = {
        val prompt = activeBiometricPrompt
        biometricAttempts.invalidate()
        activeBiometricPrompt = null
        attachedBiometricAttempt = null
        savedBiometricRequestId = null
        savedBiometricGeneration = biometricAttempts.generation
        prompt?.cancelAuthentication()
    }

    DisposableEffect(legacyCredentialFragment, viewModel) {
        legacyCredentialFragment?.setResultCallback { result ->
            if (result.authenticated) {
                viewModel.completeDeviceCredentialAuthentication(result.requestId)
            } else {
                viewModel.authenticationCancelled(result.requestId)
            }
        }
        onDispose { legacyCredentialFragment?.clearResultCallback() }
    }

    DisposableEffect(activity) {
        onDispose {
            if (activity?.isChangingConfigurations != true) cancelActiveBiometric()
        }
    }

    LaunchedEffect(request?.id, activity, deviceSecure, uiLanguage) {
        val pending = request
        val previousAttempt = biometricAttempts.active
        if (previousAttempt != null && previousAttempt.requestId != pending?.id) {
            cancelActiveBiometric()
        }
        if (pending == null) return@LaunchedEffect

        if (!deviceSecure) {
            viewModel.authenticationUnavailable(
                pending.id,
                "Set a secure screen lock before using the vault.",
            )
            return@LaunchedEffect
        }

        when (pending) {
            is VaultAuthenticationRequest.Modern -> {
                val host = activity
                if (host == null) {
                    viewModel.authenticationUnavailable(pending.id, "Authentication host unavailable.")
                    return@LaunchedEffect
                }
                val restoredAttempt = biometricAttempts.active?.takeIf {
                    it.requestId == pending.id
                }
                if (restoredAttempt != null && attachedBiometricAttempt == restoredAttempt) {
                    return@LaunchedEffect
                }
                val attempt = restoredAttempt ?: biometricAttempts.start(pending.id).also {
                    savedBiometricRequestId = it.requestId
                    savedBiometricGeneration = it.generation
                }
                val prompt = createVaultBiometricPrompt(
                    host = host,
                    viewModel = viewModel,
                    request = pending,
                    attempt = attempt,
                    onTerminal = clearBiometricAttempt,
                )
                activeBiometricPrompt = prompt
                attachedBiometricAttempt = attempt
                if (restoredAttempt != null) return@LaunchedEffect
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(translateUiText(pending.promptTitle(hasVault), uiLanguage))
                    .setSubtitle(translateUiText("Confirm your identity", uiLanguage))
                    .setAllowedAuthenticators(pending.allowedAuthenticators)
                    .build()
                runCatching { prompt.authenticate(promptInfo) }
                    .onFailure {
                        clearBiometricAttempt(attempt)
                        viewModel.authenticationUnavailable(
                            pending.id,
                            it.message ?: "Secure authentication is unavailable.",
                        )
                    }
            }

            is VaultAuthenticationRequest.LegacyBiometric -> {
                val host = activity
                if (host == null) {
                    viewModel.authenticationUnavailable(pending.id, "Authentication host unavailable.")
                    return@LaunchedEffect
                }
                val restoredAttempt = biometricAttempts.active?.takeIf {
                    it.requestId == pending.id
                }
                if (restoredAttempt != null && attachedBiometricAttempt == restoredAttempt) {
                    return@LaunchedEffect
                }
                val attempt = restoredAttempt ?: biometricAttempts.start(pending.id).also {
                    savedBiometricRequestId = it.requestId
                    savedBiometricGeneration = it.generation
                }
                val prompt = createVaultBiometricPrompt(
                    host = host,
                    viewModel = viewModel,
                    request = pending,
                    attempt = attempt,
                    onTerminal = clearBiometricAttempt,
                )
                activeBiometricPrompt = prompt
                attachedBiometricAttempt = attempt
                if (restoredAttempt != null) return@LaunchedEffect
                val negativeText = if (pending.purpose.isAccessAuthentication) {
                    "Use screen lock"
                } else {
                    "Cancel"
                }
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(translateUiText(pending.promptTitle(hasVault), uiLanguage))
                    .setSubtitle(translateUiText("Confirm your fingerprint", uiLanguage))
                    .setAllowedAuthenticators(pending.allowedAuthenticators)
                    .setNegativeButtonText(translateUiText(negativeText, uiLanguage))
                    .build()
                runCatching {
                    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(pending.cipher))
                }.onFailure {
                    clearBiometricAttempt(attempt)
                    viewModel.authenticationUnavailable(
                        pending.id,
                        it.message ?: "Fingerprint authentication is unavailable.",
                    )
                }
            }

            is VaultAuthenticationRequest.LegacyCredential -> {
                if (biometricAttempts.active != null) cancelActiveBiometric()
                val manager = context.getSystemService(KeyguardManager::class.java)
                val intent = manager?.createConfirmDeviceCredentialIntent(
                    translateUiText(pending.promptTitle(hasVault), uiLanguage),
                    translateUiText("Confirm your screen lock to continue.", uiLanguage),
                )
                if (intent == null) {
                    viewModel.authenticationUnavailable(
                        pending.id,
                        "A secure screen lock is required to use the vault.",
                    )
                } else {
                    val launcher = legacyCredentialFragment
                    if (launcher == null) {
                        viewModel.authenticationUnavailable(
                            pending.id,
                            "Authentication host unavailable.",
                        )
                    } else {
                        runCatching { launcher.launch(pending.id, intent) }
                            .onFailure {
                                viewModel.authenticationUnavailable(
                                    pending.id,
                                    it.message ?: "Secure authentication is unavailable.",
                                )
                            }
                    }
                }
            }

            is VaultAuthenticationRequest.SystemAuthentication -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    if (biometricAttempts.active != null) cancelActiveBiometric()
                    val manager = context.getSystemService(KeyguardManager::class.java)
                    val intent = manager?.createConfirmDeviceCredentialIntent(
                        translateUiText(pending.promptTitle(hasVault), uiLanguage),
                        translateUiText(
                            "Confirm your screen lock to permanently reset the vault.",
                            uiLanguage,
                        ),
                    )
                    val launcher = legacyCredentialFragment
                    when {
                        intent == null -> viewModel.authenticationUnavailable(
                            pending.id,
                            "A secure screen lock is required to reset the vault.",
                        )
                        launcher == null -> viewModel.authenticationUnavailable(
                            pending.id,
                            "Authentication host unavailable.",
                        )
                        else -> runCatching { launcher.launch(pending.id, intent) }
                            .onFailure {
                                viewModel.authenticationUnavailable(
                                    pending.id,
                                    it.message ?: "Secure authentication is unavailable.",
                                )
                            }
                    }
                    return@LaunchedEffect
                }

                val host = activity
                if (host == null) {
                    viewModel.authenticationUnavailable(pending.id, "Authentication host unavailable.")
                    return@LaunchedEffect
                }
                val restoredAttempt = biometricAttempts.active?.takeIf {
                    it.requestId == pending.id
                }
                if (restoredAttempt != null && attachedBiometricAttempt == restoredAttempt) {
                    return@LaunchedEffect
                }
                val attempt = restoredAttempt ?: biometricAttempts.start(pending.id).also {
                    savedBiometricRequestId = it.requestId
                    savedBiometricGeneration = it.generation
                }
                val prompt = createVaultBiometricPrompt(
                    host = host,
                    viewModel = viewModel,
                    request = pending,
                    attempt = attempt,
                    onTerminal = clearBiometricAttempt,
                )
                activeBiometricPrompt = prompt
                attachedBiometricAttempt = attempt
                if (restoredAttempt != null) return@LaunchedEffect
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(translateUiText(pending.promptTitle(hasVault), uiLanguage))
                    .setSubtitle(translateUiText("Confirm to permanently reset the vault", uiLanguage))
                    .setAllowedAuthenticators(pending.allowedAuthenticators)
                    .build()
                runCatching { prompt.authenticate(promptInfo) }
                    .onFailure {
                        clearBiometricAttempt(attempt)
                        viewModel.authenticationUnavailable(
                            pending.id,
                            it.message ?: "Secure authentication is unavailable.",
                        )
                    }
            }
        }
    }
}

private fun createVaultBiometricPrompt(
    host: FragmentActivity,
    viewModel: VaultViewModel,
    request: VaultAuthenticationRequest,
    attempt: VaultBiometricAttempt,
    onTerminal: (VaultBiometricAttempt) -> Unit,
): BiometricPrompt = BiometricPrompt(
    host,
    ContextCompat.getMainExecutor(host),
    object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onTerminal(attempt)
            when (request) {
                is VaultAuthenticationRequest.Modern ->
                    viewModel.completeModernAuthentication(attempt.requestId)

                is VaultAuthenticationRequest.LegacyBiometric -> {
                    val cipher = result.cryptoObject?.cipher
                    if (cipher == null) {
                        viewModel.authenticationError(
                            attempt.requestId,
                            "Authentication did not return the required secure cipher.",
                        )
                    } else {
                        viewModel.completeLegacyBiometricAuthentication(attempt.requestId, cipher)
                    }
                }

                is VaultAuthenticationRequest.LegacyCredential -> Unit
                is VaultAuthenticationRequest.SystemAuthentication ->
                    viewModel.completeSystemAuthentication(attempt.requestId)
            }
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onTerminal(attempt)
            when {
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    request is VaultAuthenticationRequest.LegacyBiometric &&
                    request.purpose.isAccessAuthentication ->
                    viewModel.useScreenLock(attempt.requestId)

                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ->
                    viewModel.authenticationCancelled(attempt.requestId)

                isVaultAuthenticationUnavailableError(errorCode) ->
                    viewModel.authenticationUnavailable(attempt.requestId, errString.toString())

                else -> viewModel.authenticationError(attempt.requestId, errString.toString())
            }
        }
    },
)

internal fun Context.canUseStrongBiometric(): Boolean =
    BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS

/** Lockout must use the separately wrapped device-credential envelope on Android 8-10. */
internal fun isVaultAuthenticationUnavailableError(errorCode: Int): Boolean = when (errorCode) {
    BiometricPrompt.ERROR_HW_NOT_PRESENT,
    BiometricPrompt.ERROR_HW_UNAVAILABLE,
    BiometricPrompt.ERROR_NO_BIOMETRICS,
    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
    BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
    -> true
    else -> false
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return current as? FragmentActivity
}

internal fun Context.openSecuritySettings() {
    startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
}

private fun VaultAuthenticationRequest.promptTitle(hasVault: Boolean): String = when (purpose) {
    VaultAuthenticationPurpose.ACCESS -> if (hasVault) "Unlock Vault" else "Create Vault"
    VaultAuthenticationPurpose.BACKUP -> "Authenticate backup"
    VaultAuthenticationPurpose.RESET -> "Reset Vault"
    VaultAuthenticationPurpose.LEGACY_FINGERPRINT_ENROLLMENT -> "Enable fingerprint unlock"
    VaultAuthenticationPurpose.MODERN_UPGRADE -> "Upgrade vault security"
}

internal data class VaultBiometricAttempt(
    val requestId: Long,
    val generation: Long,
)

/** Prevents a delayed callback from clearing a newer prompt, even if request ids are reused. */
internal class VaultBiometricAttemptRegistry(
    initialGeneration: Long = 0L,
    initialActive: VaultBiometricAttempt? = null,
) {
    var generation: Long = maxOf(initialGeneration, initialActive?.generation ?: 0L)
        private set
    var active: VaultBiometricAttempt? = initialActive
        private set

    fun start(requestId: Long): VaultBiometricAttempt {
        generation++
        return VaultBiometricAttempt(requestId, generation).also { active = it }
    }

    fun finish(attempt: VaultBiometricAttempt): Boolean {
        if (active != attempt) return false
        active = null
        return true
    }

    fun invalidate(): VaultBiometricAttempt? {
        generation++
        return active.also { active = null }
    }
}
