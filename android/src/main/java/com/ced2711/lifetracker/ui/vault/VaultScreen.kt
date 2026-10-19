package com.ced2711.lifetracker.ui.vault

import android.app.KeyguardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.ui.components.ConfirmDialog
import com.ced2711.lifetracker.ui.components.EditorSheet
import com.ced2711.lifetracker.ui.components.FieldLabel
import com.ced2711.lifetracker.ui.components.SearchField
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.IconTile
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.ReadableWidth
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.theme.LifeTheme

/**
 * Accounts and passwords. Closed until the owner confirms with fingerprint, face or screen lock;
 * then a searchable list, a reading view with copy buttons, and an editor. The Vault locks after a
 * minute without touch and when it is left; copied values leave the clipboard after 30 seconds.
 */
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
    val keyguardManager = remember(context) { context.getSystemService(KeyguardManager::class.java) }
    var deviceSecure by remember { mutableStateOf(keyguardManager?.isDeviceSecure == true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiLanguage = LocalUiLanguage.current

    // Back leaves the Vault (which locks it); on wide screens it first closes the open entry.
    BackHandler(enabled = uiState.access == VaultAccessState.Unlocked) {
        viewModel.touch()
        val current = viewModel.uiState.value
        when {
            current.mutationInProgress -> Unit
            isWide && current.viewingEntryId != null -> viewModel.closeEntry()
            else -> onBack()
        }
    }

    // A screen lock may have been set or removed while the app was in the background.
    DisposableEffect(lifecycleOwner, keyguardManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) deviceSecure = keyguardManager?.isDeviceSecure == true
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

    LaunchedEffect(uiState.snackbarMessage, uiLanguage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(translateUiText(message, uiLanguage))
        viewModel.consumeSnackbarMessage()
    }

    val actions = remember(viewModel, context, keyguardManager) {
        VaultActions(
            onUnlock = {
                deviceSecure = keyguardManager?.isDeviceSecure == true
                if (deviceSecure) viewModel.requestAccess(context.canUseStrongBiometric())
            },
            onOpenSecuritySettings = { context.openSecuritySettings() },
            onReset = viewModel::resetVault,
            onLock = { if (!viewModel.uiState.value.mutationInProgress) viewModel.lock() },
            onQueryChange = viewModel::setQuery,
            onEnableFingerprint = viewModel::requestFingerprintEnrollment,
            onUpgradeSecurity = viewModel::requestModernUpgrade,
            onOpenEntry = viewModel::openEntry,
            onCloseEntry = viewModel::closeEntry,
            onCopyAccount = viewModel::copyEntryAccount,
            onCopyPassword = viewModel::copyEntryPassword,
            onCopyWebsite = viewModel::copyEntryWebsite,
            onAdd = viewModel::addEntry,
            onEdit = viewModel::editEntry,
            onCloseEditor = viewModel::closeEditor,
            onLabelChange = viewModel::updateLabel,
            onAccountChange = viewModel::updateAccount,
            onPasswordChange = viewModel::updatePassword,
            onPasswordVisibleChange = viewModel::setPasswordVisible,
            onWebsiteChange = viewModel::updateWebsite,
            onNotesChange = viewModel::updateNotes,
            onSave = viewModel::saveEditor,
            onDelete = viewModel::deleteEntry,
            onInteraction = viewModel::touch,
        )
    }

    VaultContent(
        uiState = uiState,
        deviceSecure = deviceSecure,
        isWide = isWide,
        actions = actions,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** Everything the Vault screen can ask for; the screen wires these to the view model. */
internal class VaultActions(
    val onUnlock: () -> Unit = {},
    val onOpenSecuritySettings: () -> Unit = {},
    /** Called after the owner confirmed; the system then asks for authentication. */
    val onReset: () -> Unit = {},
    val onLock: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onEnableFingerprint: () -> Unit = {},
    val onUpgradeSecurity: () -> Unit = {},
    val onOpenEntry: (String) -> Unit = {},
    val onCloseEntry: () -> Unit = {},
    val onCopyAccount: (String) -> Unit = {},
    val onCopyPassword: (String) -> Unit = {},
    val onCopyWebsite: (String) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onEdit: (String) -> Unit = {},
    val onCloseEditor: () -> Unit = {},
    val onLabelChange: (String) -> Unit = {},
    val onAccountChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onPasswordVisibleChange: (Boolean) -> Unit = {},
    val onWebsiteChange: (String) -> Unit = {},
    val onNotesChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    /** Called after the owner confirmed. */
    val onDelete: (String) -> Unit = {},
    /** Any touch or key press; it restarts the minute after which the Vault locks. */
    val onInteraction: () -> Unit = {},
)

/** A question the screen asks before doing something that cannot be undone. */
internal sealed interface VaultConfirmation {
    data object Reset : VaultConfirmation
    data class Delete(val entryId: String) : VaultConfirmation
    data object DiscardEdits : VaultConfirmation
}

@Composable
internal fun VaultContent(
    uiState: VaultUiState,
    deviceSecure: Boolean,
    isWide: Boolean,
    actions: VaultActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    initialConfirmation: VaultConfirmation? = null,
) {
    var confirmation by remember { mutableStateOf(initialConfirmation) }
    val busy = uiState.mutationInProgress
    val unlocked = uiState.access == VaultAccessState.Unlocked
    val editor = uiState.editor.takeIf { unlocked }
    val viewing = uiState.entries.firstOrNull { it.id == uiState.viewingEntryId }.takeIf { unlocked }
    val askReset = { if (!busy) confirmation = VaultConfirmation.Reset }
    val askDelete = { id: String -> if (!busy) confirmation = VaultConfirmation.Delete(id) }

    // A question about an entry or about typed text makes no sense once that is gone.
    LaunchedEffect(unlocked, editor == null) {
        val open = confirmation
        if ((!unlocked && open is VaultConfirmation.Delete) || (editor == null && open == VaultConfirmation.DiscardEdits)) {
            confirmation = null
        }
    }

    Box(modifier.fillMaxSize().reportsInteraction(actions.onInteraction)) {
        when (val access = uiState.access) {
            VaultAccessState.Locked -> VaultGate(
                title = localizedText(if (uiState.hasVault) "Vault locked" else "Create your vault"),
                message = localizedText(
                    when {
                        !deviceSecure -> "Set a secure screen lock in Android Settings before using the vault."
                        uiState.hasVault -> "Your accounts and passwords are encrypted on this device."
                        else -> "Keep accounts and passwords encrypted on this device, opened with your fingerprint, face or screen lock."
                    },
                ),
                actionLabel = localizedText(
                    when {
                        !deviceSecure -> "Open Android security settings"
                        uiState.hasVault -> "Unlock"
                        else -> "Create vault"
                    },
                ),
                onAction = if (deviceSecure) actions.onUnlock else actions.onOpenSecuritySettings,
                onReset = askReset.takeIf { uiState.hasVault },
            )

            VaultAccessState.Unlocking -> VaultGate(
                title = localizedText("Confirm your identity"),
                message = localizedText("Waiting for secure device authentication."),
                progress = true,
            )

            is VaultAccessState.Error -> VaultGate(
                title = localizedText("Vault unavailable"),
                message = localizedText(access.message),
                tint = LifeTheme.colors.danger,
                actionLabel = localizedText(if (deviceSecure) "Try again" else "Open Android security settings"),
                onAction = if (deviceSecure) actions.onUnlock else actions.onOpenSecuritySettings,
                onReset = askReset.takeIf { uiState.hasVault },
            )

            VaultAccessState.Unlocked -> VaultUnlocked(
                uiState = uiState,
                viewing = viewing,
                isWide = isWide,
                actions = actions,
                onAskReset = askReset,
                onAskDelete = askDelete,
            )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    // The editor lies on top of the reading view; closing it returns there.
    if (editor != null) {
        VaultEditorSheet(
            editor = editor,
            busy = busy,
            actions = actions,
            snackbarHostState = snackbarHostState,
            onClose = {
                val original = uiState.entries.firstOrNull { it.id == editor.id }
                when {
                    busy -> Unit
                    editor.differsFrom(original) -> confirmation = VaultConfirmation.DiscardEdits
                    else -> actions.onCloseEditor()
                }
            },
            onAskDelete = askDelete,
        )
    } else if (viewing != null && !isWide) {
        VaultEntrySheet(
            entry = viewing,
            busy = busy,
            actions = actions,
            snackbarHostState = snackbarHostState,
            onAskDelete = askDelete,
        )
    }

    when (val open = confirmation) {
        VaultConfirmation.Reset -> ConfirmDialog(
            title = localizedText("Reset vault?"),
            text = localizedText("Android will ask you to confirm your identity, then permanently delete every vault entry and encryption key. This cannot be undone."),
            confirmLabel = localizedText("Reset vault"),
            destructive = true,
            onConfirm = {
                confirmation = null
                if (!busy) actions.onReset()
            },
            onDismiss = {
                actions.onInteraction()
                confirmation = null
            },
        )

        is VaultConfirmation.Delete -> {
            val entry = uiState.entries.firstOrNull { it.id == open.entryId }
            if (unlocked && entry != null) {
                ConfirmDialog(
                    title = localizedText("Delete this Vault entry?"),
                    text = shownLabel(entry),
                    confirmLabel = localizedText("Delete"),
                    destructive = true,
                    onConfirm = {
                        confirmation = null
                        if (!busy) actions.onDelete(entry.id)
                    },
                    onDismiss = {
                        actions.onInteraction()
                        confirmation = null
                    },
                )
            }
        }

        VaultConfirmation.DiscardEdits -> if (editor != null) {
            ConfirmDialog(
                title = localizedText("Discard changes?"),
                text = localizedText("What you typed here has not been saved."),
                confirmLabel = localizedText("Discard"),
                dismissLabel = localizedText("Keep editing"),
                destructive = true,
                onConfirm = {
                    confirmation = null
                    actions.onCloseEditor()
                },
                onDismiss = {
                    actions.onInteraction()
                    confirmation = null
                },
            )
        }

        null -> Unit
    }
}

/** The closed Vault: one calm card saying what is going on, with the one thing to do next. */
@Composable
private fun VaultGate(
    title: String,
    message: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    progress: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onReset: (() -> Unit)? = null,
) {
    Box(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(Space.xxl)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    if (progress) {
                        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                        }
                    } else {
                        IconTile(Icons.Rounded.Lock, tint = tint, size = 56.dp)
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (actionLabel != null) {
                        Button(onClick = onAction, modifier = Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = 48.dp)) {
                            Text(actionLabel, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            if (onReset != null) QuietResetButton(onReset, enabled = true)
        }
    }
}

/** Starting over is possible but rarely wanted, so it stays quiet; it always asks first. */
@Composable
private fun QuietResetButton(onClick: () -> Unit, enabled: Boolean) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(localizedText("Reset vault"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VaultUnlocked(
    uiState: VaultUiState,
    viewing: VaultEntry?,
    isWide: Boolean,
    actions: VaultActions,
    onAskReset: () -> Unit,
    onAskDelete: (String) -> Unit,
) {
    if (!isWide) {
        ReadableWidth(Modifier.fillMaxSize()) {
            VaultEntryList(uiState, selectedId = null, isWide = false, actions = actions, onAskReset = onAskReset)
        }
        return
    }
    // Wide screens: the list on the left, the chosen entry on the right.
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(400.dp).fillMaxHeight()) {
            VaultEntryList(uiState, selectedId = viewing?.id, isWide = true, actions = actions, onAskReset = onAskReset)
        }
        Box(Modifier.fillMaxHeight().width(1.dp).background(LifeTheme.colors.divider))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (viewing == null) {
                Text(
                    localizedText("Choose an entry, or tap + for a new one."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(Space.xxl),
                )
            } else {
                VaultEntryPane(viewing, busy = uiState.mutationInProgress, actions = actions, onAskDelete = onAskDelete)
            }
        }
    }
}

@Composable
private fun VaultEntryList(
    uiState: VaultUiState,
    selectedId: String?,
    isWide: Boolean,
    actions: VaultActions,
    onAskReset: () -> Unit,
) {
    val entries = uiState.filteredEntries
    val busy = uiState.mutationInProgress
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.xs, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchField(uiState.query, actions.onQueryChange, Modifier.weight(1f))
                    TextButton(onClick = actions.onLock, enabled = !busy, modifier = Modifier.padding(start = Space.xs)) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(localizedText("Lock"))
                    }
                }
            }
            if (uiState.offerFingerprintEnrollment) {
                item {
                    VaultOffer(
                        icon = Icons.Rounded.Fingerprint,
                        title = localizedText("Enable fingerprint unlock"),
                        message = localizedText("Use your enrolled fingerprint next time, with screen lock as a fallback."),
                        actionLabel = localizedText("Enable"),
                        enabled = !busy,
                        onClick = actions.onEnableFingerprint,
                    )
                }
            }
            if (uiState.offerModernUpgrade) {
                item {
                    VaultOffer(
                        icon = Icons.Rounded.Shield,
                        title = localizedText("Upgrade vault security"),
                        message = localizedText("Add support for the current Android authentication system on this device."),
                        actionLabel = localizedText("Upgrade"),
                        enabled = !busy,
                        onClick = actions.onUpgradeSecurity,
                    )
                }
            }
            if (entries.isEmpty()) {
                item {
                    val searching = uiState.query.isNotBlank()
                    EmptyState(
                        title = localizedText(if (searching) "No entries match" else "No Vault entries yet"),
                        icon = Icons.Rounded.Key,
                        body = localizedText(if (searching) "Try other words." else "Tap + to store an account and its password."),
                    )
                }
            } else {
                item { Spacer(Modifier.size(Space.xs)) }
                items(entries, key = VaultEntry::id) { entry ->
                    val title = shownLabel(entry)
                    ListRow(
                        title = title,
                        supporting = listOf(entry.account, entry.website).map(String::trim).firstOrNull { it.isNotEmpty() && it != title },
                        leading = { EntryTile(title) },
                        selected = isWide && entry.id == selectedId,
                        maxTitleLines = 1,
                        onClick = { if (!busy) actions.onOpenEntry(entry.id) },
                    )
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(top = Space.lg), contentAlignment = Alignment.Center) {
                    QuietResetButton(onAskReset, enabled = !busy)
                }
            }
        }
        FloatingActionButton(
            onClick = { if (!busy) actions.onAdd() },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Space.lg),
        ) {
            Icon(Icons.Rounded.Add, localizedText("Add vault entry"))
        }
    }
}

/** A slim suggestion above the list, such as switching on fingerprint unlock. */
@Composable
private fun VaultOffer(icon: ImageVector, title: String, message: String, actionLabel: String, enabled: Boolean, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().padding(top = Space.xs), padding = PaddingValues(start = Space.md, top = Space.sm, bottom = Space.sm, end = Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Column(Modifier.weight(1f).padding(start = Space.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClick, enabled = enabled) { Text(actionLabel) }
        }
    }
}

/** The entry's first letter in a soft tile, or a key when the name starts with something else. */
@Composable
private fun EntryTile(label: String) {
    val first = label.trim().takeIf(String::isNotEmpty)?.let { it.substring(0, it.offsetByCodePoints(0, 1)) }
    if (first == null || !Character.isLetterOrDigit(first.codePointAt(0))) {
        IconTile(Icons.Rounded.Key)
        return
    }
    val tint = MaterialTheme.colorScheme.primary
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        // The letter is decoration; the row's title is what a screen reader reads.
        Text(
            first.uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** The reading view on phones: a full-height sheet with Edit at the top and Delete at the bottom. */
@Composable
private fun VaultEntrySheet(
    entry: VaultEntry,
    busy: Boolean,
    actions: VaultActions,
    snackbarHostState: SnackbarHostState,
    onAskDelete: (String) -> Unit,
) {
    EditorSheet(
        title = shownLabel(entry),
        onClose = actions.onCloseEntry,
        actionLabel = localizedText("Edit"),
        onAction = { actions.onEdit(entry.id) },
        modifier = Modifier.reportsInteraction(actions.onInteraction),
        working = busy,
        footer = {
            SheetFooter(snackbarHostState) {
                TextButton(onClick = { onAskDelete(entry.id) }, enabled = !busy) {
                    Text(localizedText("Delete"), color = LifeTheme.colors.danger)
                }
            }
        },
    ) {
        VaultEntryDetails(entry, actions)
    }
}

/** The reading view on wide screens, in the right pane. */
@Composable
private fun VaultEntryPane(entry: VaultEntry, busy: Boolean, actions: VaultActions, onAskDelete: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = Space.xl, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                shownLabel(entry),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Button(onClick = { actions.onEdit(entry.id) }, enabled = !busy, modifier = Modifier.padding(horizontal = Space.xs)) {
                Text(localizedText("Edit"))
            }
            IconButton(onClick = actions.onCloseEntry) { Icon(Icons.Rounded.Close, localizedText("Close")) }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl, vertical = Space.sm)) {
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                VaultEntryDetails(entry, actions)
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(LifeTheme.colors.divider))
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm)) {
            TextButton(onClick = { onAskDelete(entry.id) }, enabled = !busy) {
                Text(localizedText("Delete"), color = LifeTheme.colors.danger)
            }
        }
    }
}

/** What an entry holds: each value with its Copy button, the password as dots until shown. */
@Composable
private fun VaultEntryDetails(entry: VaultEntry, actions: VaultActions) {
    var passwordShown by remember(entry.id) { mutableStateOf(false) }
    val hasValues = listOf(entry.account, entry.password, entry.website, entry.notes).any(String::isNotBlank)
    if (!hasValues) {
        Text(
            localizedText("Only the label is stored for this entry."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.sm),
        )
        return
    }
    if (entry.account.isNotBlank()) {
        VaultValue(localizedText("Account"), entry.account, copyLabel = localizedText("Copy account"), onCopy = { actions.onCopyAccount(entry.id) })
    }
    if (entry.password.isNotBlank()) {
        // Screen readers are told the state only; the password itself is never read out.
        val spoken = localizedText(if (passwordShown) "Password shown on screen" else "Password hidden")
        VaultValue(
            label = localizedText("Password"),
            value = if (passwordShown) entry.password else "•".repeat(entry.password.length.coerceIn(1, 16)),
            copyLabel = localizedText("Copy password"),
            onCopy = { actions.onCopyPassword(entry.id) },
            monospace = true,
            valueModifier = Modifier.clearAndSetSemantics {
                password()
                contentDescription = spoken
            },
            extra = {
                IconButton(onClick = {
                    actions.onInteraction()
                    passwordShown = !passwordShown
                }) {
                    Icon(
                        if (passwordShown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        localizedText(if (passwordShown) "Hide password" else "Show password"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
    if (entry.website.isNotBlank()) {
        VaultValue(localizedText("Website"), entry.website, copyLabel = localizedText("Copy website"), onCopy = { actions.onCopyWebsite(entry.id) })
    }
    if (entry.notes.isNotBlank()) {
        FieldLabel(localizedText("Notes"))
        Text(entry.notes, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.xs, bottom = Space.lg))
    }
}

/** One value of an entry in a soft box, with Copy (and for the password, Show) at its end. */
@Composable
private fun VaultValue(
    label: String,
    value: String,
    copyLabel: String,
    onCopy: () -> Unit,
    monospace: Boolean = false,
    valueModifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Column {
        FieldLabel(label)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.xs)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .heightIn(min = 52.dp)
                .padding(start = Space.lg, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = Space.md).then(valueModifier),
            )
            extra()
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, copyLabel, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VaultEditorSheet(
    editor: VaultEditorState,
    busy: Boolean,
    actions: VaultActions,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onAskDelete: (String) -> Unit,
) {
    val entryId = editor.id
    // Messages must show on top of the sheet, so the footer carries them; a new entry has no
    // Delete, so its footer only appears while there is something to say.
    val footer: (@Composable RowScope.() -> Unit)? = if (entryId != null || snackbarHostState.currentSnackbarData != null) {
        {
            SheetFooter(snackbarHostState) {
                if (entryId != null) {
                    TextButton(onClick = { onAskDelete(entryId) }, enabled = !busy) {
                        Text(localizedText("Delete"), color = LifeTheme.colors.danger)
                    }
                }
            }
        }
    } else {
        null
    }
    EditorSheet(
        title = localizedText(if (entryId == null) "New Vault entry" else "Edit Vault entry"),
        onClose = onClose,
        actionLabel = localizedText("Save"),
        onAction = actions.onSave,
        modifier = Modifier.reportsInteraction(actions.onInteraction),
        actionEnabled = editor.isValid,
        working = busy,
        footer = footer,
    ) {
        FieldLabel(localizedText("Label"))
        LifeTextField(editor.label, actions.onLabelChange, Modifier.fillMaxWidth(), placeholder = localizedText("e.g. Bank, Email"), enabled = !busy)
        FieldLabel(localizedText("Account"))
        LifeTextField(editor.account, actions.onAccountChange, Modifier.fillMaxWidth(), placeholder = localizedText("User name or email"), enabled = !busy)
        FieldLabel(localizedText("Password"))
        VaultPasswordField(
            password = editor.password,
            passwordVisible = editor.passwordVisible,
            enabled = !busy,
            onPasswordChange = actions.onPasswordChange,
            onPasswordVisibilityChange = actions.onPasswordVisibleChange,
        )
        FieldLabel(localizedText("Website"))
        LifeTextField(
            editor.website,
            actions.onWebsiteChange,
            Modifier.fillMaxWidth(),
            placeholder = "https://",
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        FieldLabel(localizedText("Notes"))
        LifeTextField(editor.notes, actions.onNotesChange, Modifier.fillMaxWidth(), placeholder = localizedText("Optional"), minLines = 4, enabled = !busy)
        Text(
            localizedText("Enter at least one field. Passwords stay encrypted on this device."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs, bottom = Space.lg),
        )
    }
}

/**
 * The password box of the editor. While the password is shown it cannot be edited and its
 * semantics are replaced, so the plain text is on screen for a sighted user but never handed to
 * accessibility services. Show/Hide stays outside the cleared part and remains discoverable.
 */
@Composable
internal fun VaultPasswordField(
    password: String,
    passwordVisible: Boolean,
    enabled: Boolean,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: (Boolean) -> Unit,
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
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LifeTextField(
            value = password,
            onValueChange = { if (!passwordVisible) onPasswordChange(it) },
            modifier = Modifier.weight(1f).then(protectedSemantics),
            // Shown means locked: no typing, no selecting, no copying out of the field.
            enabled = enabled && !passwordVisible,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        )
        TextButton(
            onClick = { onPasswordVisibilityChange(!passwordVisible) },
            enabled = enabled,
            modifier = Modifier.padding(start = Space.xs),
        ) {
            Icon(if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(localizedText(if (passwordVisible) "Hide" else "Show"))
        }
    }
}

internal const val VAULT_PASSWORD_VISIBLE_ACCESSIBILITY_DESCRIPTION =
    "Password visible; hide to edit"

/** The bottom of a sheet: messages (such as "copied") above the sheet's rare actions. */
@Composable
private fun RowScope.SheetFooter(snackbarHostState: SnackbarHostState, actions: @Composable RowScope.() -> Unit) {
    Column(Modifier.weight(1f)) {
        SnackbarHost(snackbarHostState)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** The name an entry is listed under; an entry without any usable text is "Untitled". */
@Composable
private fun shownLabel(entry: VaultEntry): String {
    val label = entry.displayLabel()
    return if (entry.label.isBlank() && label == "Untitled") localizedText("Untitled") else label
}

/** True when closing the editor would lose something that was typed. */
private fun VaultEditorState.differsFrom(entry: VaultEntry?): Boolean = if (entry == null) {
    isValid
} else {
    label != entry.label || account != entry.account || password != entry.password || website != entry.website || notes != entry.notes
}

/** Tells the Vault about every touch and key press, so it only locks when left alone. */
private fun Modifier.reportsInteraction(onInteraction: () -> Unit): Modifier = this
    .pointerInput(onInteraction) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.any { it.pressed || it.previousPressed }) onInteraction()
            }
        }
    }
    .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) onInteraction()
        false
    }
