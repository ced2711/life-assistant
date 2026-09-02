package com.ced2711.lifetracker.ui.vault

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    isWide: Boolean,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyguardManager = remember(context) {
        context.getSystemService(KeyguardManager::class.java)
    }
    var deviceSecure by remember { mutableStateOf(keyguardManager?.isDeviceSecure == true) }
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteEntryId by remember { mutableStateOf<String?>(null) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    val requestBack: () -> Unit = {
        if (viewModel.uiState.value.mutationInProgress) {
            viewModel.touch()
        } else {
            viewModel.touch()
            onBack()
        }
    }
    BackHandler(enabled = uiState.access == VaultAccessState.Unlocked) {
        requestBack()
    }

    DisposableEffect(lifecycleOwner, keyguardManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                deviceSecure = keyguardManager?.isDeviceSecure == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    VaultAuthenticationCoordinator(
        viewModel = viewModel,
        request = uiState.authenticationRequest,
        hasVault = uiState.hasVault,
        deviceSecure = deviceSecure,
    )

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbarMessage()
    }

    val interactionModifier = Modifier
        .pointerInput(viewModel) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.any { it.pressed || it.previousPressed }) viewModel.touch()
                }
            }
        }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) viewModel.touch()
            false
        }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(interactionModifier),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding(),
        ) {
            VaultActions(
                access = uiState.access,
                mutationInProgress = uiState.mutationInProgress,
                onLock = {
                    if (!viewModel.uiState.value.mutationInProgress) viewModel.lock()
                },
                onAdd = viewModel::addEntry,
            )
            HorizontalDivider()

            when (val access = uiState.access) {
                VaultAccessState.Locked -> LockedVaultContent(
                    hasVault = uiState.hasVault,
                    deviceSecure = deviceSecure,
                    onUnlock = {
                        deviceSecure = keyguardManager?.isDeviceSecure == true
                        if (deviceSecure) viewModel.requestAccess(context.canUseStrongBiometric())
                    },
                    onOpenSecuritySettings = context::openSecuritySettings,
                    onReset = { showResetConfirmation = true },
                )

                VaultAccessState.Unlocking -> VaultStatusContent(
                    title = "Confirm your identity",
                    message = "Waiting for secure device authentication.",
                    progress = true,
                )

                is VaultAccessState.Error -> VaultStatusContent(
                    title = "Vault unavailable",
                    message = access.message,
                    actionLabel = if (deviceSecure) "Try again" else "Open Android security settings",
                    onAction = {
                        if (deviceSecure) {
                            viewModel.requestAccess(context.canUseStrongBiometric())
                        } else {
                            context.openSecuritySettings()
                        }
                    },
                    secondaryActionLabel = "Reset vault".takeIf { uiState.hasVault },
                    onSecondaryAction = { showResetConfirmation = true },
                )

                VaultAccessState.Unlocked -> VaultUnlockedContent(
                    uiState = uiState,
                    isWide = isWide,
                    viewModel = viewModel,
                    onDelete = {
                        if (!viewModel.uiState.value.mutationInProgress) deleteEntryId = it
                    },
                    onReset = {
                        if (!viewModel.uiState.value.mutationInProgress) {
                            showResetConfirmation = true
                        }
                    },
                )
            }
        }
    }

    val deleteTarget = uiState.entries.firstOrNull { it.id == deleteEntryId }
    if (deleteEntryId != null) {
        HingeSafeAlertDialog(
            onDismissRequest = {
                if (!uiState.mutationInProgress) {
                    viewModel.touch()
                    deleteEntryId = null
                }
            },
            title = { Text("Delete entry?") },
            text = {
                Text(
                    "Delete ${deleteTarget?.displayLabel() ?: "this entry"}? This cannot be undone.",
                )
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.mutationInProgress,
                    onClick = {
                        viewModel.touch()
                        deleteEntryId = null
                    },
                ) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.mutationInProgress,
                    onClick = {
                        val id = deleteEntryId
                        deleteEntryId = null
                        if (id != null) viewModel.deleteEntry(id)
                    },
                ) { Text("Delete") }
            },
        )
    }

    if (showResetConfirmation) {
        HingeSafeAlertDialog(
            onDismissRequest = {
                if (!uiState.mutationInProgress) {
                    viewModel.touch()
                    showResetConfirmation = false
                }
            },
            title = { Text("Reset vault?") },
            text = {
                Text(
                    "Android will ask you to confirm your identity, then permanently delete " +
                        "every vault entry and encryption key. This cannot be undone.",
                )
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.mutationInProgress,
                    onClick = {
                        viewModel.touch()
                        showResetConfirmation = false
                    },
                ) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.mutationInProgress,
                    onClick = {
                        showResetConfirmation = false
                        viewModel.resetVault()
                    },
                ) { Text("Reset vault") }
            },
        )
    }
}

@Composable
internal fun VaultAuthenticationCoordinator(
    viewModel: VaultViewModel,
    request: VaultAuthenticationRequest?,
    hasVault: Boolean,
    deviceSecure: Boolean,
) {
    val context = LocalContext.current
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

    LaunchedEffect(request?.id, activity, deviceSecure) {
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
                    .setTitle(pending.promptTitle(hasVault))
                    .setSubtitle("Confirm your identity")
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
                    .setTitle(pending.promptTitle(hasVault))
                    .setSubtitle("Confirm your fingerprint")
                    .setAllowedAuthenticators(pending.allowedAuthenticators)
                    .setNegativeButtonText(negativeText)
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
                    pending.promptTitle(hasVault),
                    "Confirm your screen lock to continue.",
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
                        pending.promptTitle(hasVault),
                        "Confirm your screen lock to permanently reset the vault.",
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
                    .setTitle(pending.promptTitle(hasVault))
                    .setSubtitle("Confirm to permanently reset the vault")
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

@Composable
private fun VaultActions(
    access: VaultAccessState,
    mutationInProgress: Boolean,
    onLock: () -> Unit,
    onAdd: () -> Unit,
) {
    val unlocked = access == VaultAccessState.Unlocked
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onLock, enabled = unlocked && !mutationInProgress) {
            Icon(Icons.Default.Lock, contentDescription = "Lock vault")
        }
        IconButton(onClick = onAdd, enabled = unlocked && !mutationInProgress) {
            Icon(Icons.Default.Add, contentDescription = "Add vault entry")
        }
    }
}

@Composable
private fun LockedVaultContent(
    hasVault: Boolean,
    deviceSecure: Boolean,
    onUnlock: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onReset: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (hasVault) "Vault locked" else "Create your vault",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = if (deviceSecure) {
                    "Your accounts and passwords are encrypted on this device. Unlocking requires your fingerprint, face, PIN, pattern, or password."
                } else {
                    "Set a secure screen lock in Android Settings before using the vault."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = onUnlock, enabled = deviceSecure) {
                Text(if (hasVault) "Unlock" else "Create vault")
            }
            if (!deviceSecure) {
                OutlinedButton(onClick = onOpenSecuritySettings) {
                    Text("Open Android security settings")
                }
            }
            if (hasVault) {
                TextButton(onClick = onReset) {
                    Text("Reset vault", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun VaultStatusContent(
    title: String,
    message: String,
    progress: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (progress) CircularProgressIndicator()
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (actionLabel != null) Button(onClick = onAction) { Text(actionLabel) }
            if (secondaryActionLabel != null) {
                TextButton(onClick = onSecondaryAction) {
                    Text(secondaryActionLabel, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun VaultUnlockedContent(
    uiState: VaultUiState,
    isWide: Boolean,
    viewModel: VaultViewModel,
    onDelete: (String) -> Unit,
    onReset: () -> Unit,
) {
    if (isWide) {
        Row(Modifier.fillMaxSize()) {
            VaultEntryList(
                uiState = uiState,
                viewModel = viewModel,
                onReset = onReset,
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
            )
            VerticalDivider()
            Box(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
            ) {
                val editor = uiState.editor
                if (editor == null) {
                    EmptyEditorPlaceholder()
                } else {
                    VaultEditor(
                        editor = editor,
                        mutationInProgress = uiState.mutationInProgress,
                        viewModel = viewModel,
                        onDelete = onDelete,
                        showBack = false,
                    )
                }
            }
        }
    } else {
        val editor = uiState.editor
        if (editor == null) {
            VaultEntryList(
                uiState = uiState,
                viewModel = viewModel,
                onReset = onReset,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            VaultEditor(
                editor = editor,
                mutationInProgress = uiState.mutationInProgress,
                viewModel = viewModel,
                onDelete = onDelete,
                showBack = true,
            )
        }
    }
}

@Composable
private fun VaultEntryList(
    uiState: VaultUiState,
    viewModel: VaultViewModel,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = uiState.filteredEntries
    val actionsEnabled = !uiState.mutationInProgress
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                enabled = actionsEnabled,
            )
        }

        if (uiState.offerFingerprintEnrollment) {
            item {
                VaultOfferCard(
                    title = "Enable fingerprint unlock",
                    message = "Use your enrolled fingerprint next time, with screen lock as a fallback.",
                    actionLabel = "Enable",
                    onClick = viewModel::requestFingerprintEnrollment,
                    enabled = actionsEnabled,
                )
            }
        }
        if (uiState.offerModernUpgrade) {
            item {
                VaultOfferCard(
                    title = "Upgrade vault security",
                    message = "Add support for the current Android authentication system on this device.",
                    actionLabel = "Upgrade",
                    onClick = viewModel::requestModernUpgrade,
                    enabled = actionsEnabled,
                )
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    text = if (uiState.query.isBlank()) {
                        "No vault entries yet. Use Add to store an account."
                    } else {
                        "No entries match your search."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            items(entries, key = VaultEntry::id) { entry ->
                VaultEntryRow(
                    entry = entry,
                    enabled = actionsEnabled,
                    onClick = { viewModel.editEntry(entry.id) },
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onReset,
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset vault", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun VaultEntryRow(entry: VaultEntry, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.displayLabel(),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onClick, enabled = enabled) {
                Icon(Icons.Default.Edit, contentDescription = "Edit ${entry.displayLabel()}")
            }
        }
    }
}

@Composable
private fun VaultOfferCard(
    title: String,
    message: String,
    actionLabel: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun VaultEditor(
    editor: VaultEditorState,
    mutationInProgress: Boolean,
    viewModel: VaultViewModel,
    onDelete: (String) -> Unit,
    showBack: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(
                    onClick = viewModel::closeEditor,
                    enabled = !mutationInProgress,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to entries")
                }
            }
            Text(
                text = if (editor.id == null) "New entry" else "Edit entry",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (editor.id != null) {
                IconButton(
                    onClick = { onDelete(editor.id) },
                    enabled = !mutationInProgress,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete entry")
                }
            }
        }

        OutlinedTextField(
            value = editor.label,
            onValueChange = viewModel::updateLabel,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Label") },
            singleLine = true,
            enabled = !mutationInProgress,
        )
        OutlinedTextField(
            value = editor.account,
            onValueChange = viewModel::updateAccount,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Account or username") },
            singleLine = true,
            enabled = !mutationInProgress,
            trailingIcon = {
                IconButton(
                    onClick = viewModel::copyEditorAccount,
                    enabled = editor.account.isNotBlank() && !mutationInProgress,
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy account")
                }
            },
        )
        VaultPasswordField(
            password = editor.password,
            passwordVisible = editor.passwordVisible,
            enabled = !mutationInProgress,
            onPasswordChange = viewModel::updatePassword,
            onPasswordVisibilityChange = viewModel::setPasswordVisible,
            onCopyPassword = viewModel::copyEditorPassword,
        )
        OutlinedTextField(
            value = editor.website,
            onValueChange = viewModel::updateWebsite,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Website") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !mutationInProgress,
        )
        OutlinedTextField(
            value = editor.notes,
            onValueChange = viewModel::updateNotes,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes") },
            minLines = 4,
            enabled = !mutationInProgress,
        )
        Text(
            text = "Enter at least one field. Passwords stay encrypted on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = viewModel::closeEditor,
                enabled = !mutationInProgress,
            ) { Text("Close") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = viewModel::saveEditor,
                enabled = editor.isValid && !mutationInProgress,
            ) {
                if (mutationInProgress) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (mutationInProgress) "Working…" else "Save")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * The visible value is deliberately read-only and its editable semantics subtree is replaced.
 * This keeps the plaintext on screen for a sighted user without sending it to accessibility
 * services. The separate Hide and Copy actions remain discoverable outside the cleared subtree.
 */
@Composable
internal fun VaultPasswordField(
    password: String,
    passwordVisible: Boolean,
    enabled: Boolean,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    onCopyPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val protectedSemantics = if (passwordVisible) {
        Modifier.clearAndSetSemantics {
            password()
            contentDescription = VAULT_PASSWORD_VISIBLE_ACCESSIBILITY_DESCRIPTION
        }
    } else {
        Modifier.semantics { password() }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = password,
            onValueChange = { if (!passwordVisible) onPasswordChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .then(protectedSemantics),
            label = { Text("Password") },
            singleLine = true,
            enabled = enabled,
            readOnly = passwordVisible,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = { onPasswordVisibilityChange(!passwordVisible) },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = if (passwordVisible) {
                        Icons.Default.VisibilityOff
                    } else {
                        Icons.Default.Visibility
                    },
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (passwordVisible) "Hide" else "Show")
            }
            TextButton(
                onClick = onCopyPassword,
                enabled = password.isNotBlank() && enabled,
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Copy")
            }
        }
    }
}

internal const val VAULT_PASSWORD_VISIBLE_ACCESSIBILITY_DESCRIPTION =
    "Password visible; hide to edit"

@Composable
private fun EmptyEditorPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Select an entry or use Add.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

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

private fun Context.openSecuritySettings() {
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
