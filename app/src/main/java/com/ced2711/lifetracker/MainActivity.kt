package com.ced2711.lifetracker

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.adaptive.AdaptiveTaskLedgerScaffold
import com.ced2711.lifetracker.ui.adaptive.LocalSafePaneLayout
import com.ced2711.lifetracker.ui.adaptive.collectFoldingFeature
import com.ced2711.lifetracker.ui.adaptive.usesWideFeatureLayout
import com.ced2711.lifetracker.ui.backup.BackupAuthenticationStatus
import com.ced2711.lifetracker.ui.backup.BackupRestoreScreen
import com.ced2711.lifetracker.ui.backup.BackupRestoreTask
import com.ced2711.lifetracker.ui.backup.BackupRestoreViewModel
import com.ced2711.lifetracker.ui.calendar.CalendarScreen
import com.ced2711.lifetracker.ui.ledger.LedgerScreen
import com.ced2711.lifetracker.ui.settings.SettingsScreen
import com.ced2711.lifetracker.ui.theme.TaskLedgerTheme
import com.ced2711.lifetracker.ui.theme.isTaskLedgerDarkTheme
import com.ced2711.lifetracker.ui.todo.TodoScreen
import com.ced2711.lifetracker.ui.vault.VaultScreen
import com.ced2711.lifetracker.ui.vault.VaultAccessState
import com.ced2711.lifetracker.ui.vault.VaultAuthenticationCoordinator
import com.ced2711.lifetracker.ui.vault.VaultViewModel
import com.ced2711.lifetracker.ui.vault.canUseStrongBiometric
import com.ced2711.lifetracker.ui.vault.usesExternalDeviceCredentialPrompt
import com.ced2711.lifetracker.widget.WidgetNavigation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class MainActivity : FragmentActivity() {
    private val viewModel: TaskLedgerViewModel by viewModels {
        TaskLedgerViewModel.Factory((application as TaskLedgerApplication).container)
    }
    private val vaultViewModel: VaultViewModel by viewModels {
        (application as TaskLedgerApplication).container.let { container ->
            VaultViewModel.Factory(
                repository = container.vaultRepository,
                keyManager = container.vaultKeyManager,
                clipboard = container.vaultClipboard,
            )
        }
    }
    private val backupRestoreViewModel: BackupRestoreViewModel by viewModels {
        BackupRestoreViewModel.Factory((application as TaskLedgerApplication).container)
    }
    private var pendingReminderTodoId by mutableStateOf<Long?>(null)
    private var pendingWidgetQuickAddAction by mutableStateOf<String?>(null)
    private var pendingWidgetQuickAddToken by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingReminderTodoId = when {
            savedInstanceState?.containsKey(STATE_PENDING_TODO_ID) == true ->
                savedInstanceState.getLong(STATE_PENDING_TODO_ID).takeIf { it > 0L }
            savedInstanceState == null -> reminderTodoId(intent)
            else -> null
        }
        pendingWidgetQuickAddAction = when {
            savedInstanceState?.containsKey(STATE_PENDING_WIDGET_ACTION) == true ->
                savedInstanceState.getString(STATE_PENDING_WIDGET_ACTION)
            savedInstanceState == null -> widgetQuickAddAction(intent)
            else -> null
        }
        pendingWidgetQuickAddToken = when {
            savedInstanceState?.containsKey(STATE_PENDING_WIDGET_TOKEN) == true ->
                savedInstanceState.getString(STATE_PENDING_WIDGET_TOKEN)
            pendingWidgetQuickAddAction != null -> newWidgetRequestToken()
            else -> null
        }
        applyEdgeToEdgeStyle(darkTheme = true)
        setContent {
            val startupRecoveryState by
                (application as TaskLedgerApplication).startupRecoveryCoordinator.state
                    .collectAsStateWithLifecycle()
            if (startupRecoveryState !is StartupRecoveryState.Ready) {
                TaskLedgerTheme {
                    StartupRecoveryGate(
                        state = startupRecoveryState,
                        onRetry = {
                            (application as TaskLedgerApplication)
                                .startupRecoveryCoordinator
                                .retry()
                        },
                    )
                }
                return@setContent
            }

            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val backupUiState by backupRestoreViewModel.uiState.collectAsStateWithLifecycle()
            val backupAuthentication by
                backupRestoreViewModel.authenticationRequest.collectAsStateWithLifecycle()
            val vaultUiState by vaultViewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val darkTheme = isTaskLedgerDarkTheme(settings.themeMode)
            val reminderTodoId = pendingReminderTodoId
            val widgetQuickAddAction = pendingWidgetQuickAddAction.takeIf { reminderTodoId == null }
            val widgetQuickAddToken = pendingWidgetQuickAddToken.takeIf {
                widgetQuickAddAction != null
            }
            var selectedOverride by rememberSaveable { mutableStateOf<String?>(null) }
            var auxiliaryName by rememberSaveable {
                mutableStateOf(
                    if (BuildConfig.HAS_INCLUDED_PERSONAL_BACKUP) {
                        AuxiliaryScreen.BACKUP.name
                    } else {
                        null
                    },
                )
            }
            val requestedAuxiliary = auxiliaryName
                ?.let { name -> AuxiliaryScreen.entries.firstOrNull { it.name == name } }
            val auxiliary = requestedAuxiliary.takeIf {
                (it == AuxiliaryScreen.BACKUP &&
                    backupUiState.task != BackupRestoreTask.NONE) ||
                    (reminderTodoId == null && widgetQuickAddAction == null)
            }
            val showVault = auxiliary == AuxiliaryScreen.VAULT
            val showBackup = auxiliary == AuxiliaryScreen.BACKUP
            var backupSensitive by remember { mutableStateOf(false) }
            val selected = when {
                reminderTodoId != null -> TopLevelDestination.TODO
                widgetQuickAddAction == WidgetNavigation.ACTION_OPEN_TODO_LIST ->
                    TopLevelDestination.TODO
                widgetQuickAddAction == WidgetNavigation.ACTION_OPEN_LEDGER ->
                    TopLevelDestination.LEDGER
                else -> selectedOverride
                    ?.let { value -> TopLevelDestination.entries.firstOrNull { it.name == value } }
                    ?: settings.lastDestination
            }
            val foldingFeature by collectFoldingFeature(this)

            BackHandler(enabled = auxiliary != null) {
                if (showBackup && backupUiState.task != BackupRestoreTask.NONE) {
                    return@BackHandler
                }
                auxiliaryName = when (auxiliary) {
                    AuxiliaryScreen.VAULT -> {
                        vaultViewModel.lock()
                        AuxiliaryScreen.SETTINGS.name
                    }
                    AuxiliaryScreen.BACKUP -> {
                        backupRestoreViewModel.leaveBackupScreen()
                        vaultViewModel.lock()
                        AuxiliaryScreen.SETTINGS.name
                    }
                    AuxiliaryScreen.SETTINGS, null -> null
                }
            }

            val protectWindow = showVault ||
                (showBackup && (backupSensitive || backupAuthentication != null))
            DisposableEffect(protectWindow) {
                if (protectWindow) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                onDispose {
                    if (protectWindow) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            LaunchedEffect(showVault, showBackup) {
                if (!showBackup) {
                    backupSensitive = false
                    backupRestoreViewModel.leaveBackupScreen()
                }
                if (!showVault && !showBackup) {
                    vaultViewModel.lock(clearOwnedClipboard = false)
                }
            }

            if (showBackup) {
                val deviceSecure = context.getSystemService(KeyguardManager::class.java)
                    ?.isDeviceSecure == true
                VaultAuthenticationCoordinator(
                    viewModel = vaultViewModel,
                    request = vaultUiState.authenticationRequest,
                    hasVault = vaultUiState.hasVault,
                    deviceSecure = deviceSecure,
                )
                LaunchedEffect(backupAuthentication?.nonce) {
                    val request = backupAuthentication ?: return@LaunchedEffect
                    if (backupRestoreViewModel.markAuthenticationDispatched(request.nonce)) {
                        vaultViewModel.requestFreshBackupAuthentication(
                            context.canUseStrongBiometric(),
                        )
                        backupRestoreViewModel.markAuthenticationInProgress(request.nonce)
                    }
                }
                LaunchedEffect(
                    backupAuthentication?.nonce,
                    backupAuthentication?.status,
                    vaultUiState.access,
                    vaultUiState.authenticationRequest?.id,
                ) {
                    val request = backupAuthentication ?: return@LaunchedEffect
                    when (vaultUiState.access) {
                        VaultAccessState.Unlocking ->
                            backupRestoreViewModel.markAuthenticationInProgress(request.nonce)
                        VaultAccessState.Unlocked -> {
                            val lease = vaultViewModel.acquireBackupSessionLease()
                            if (lease != null) {
                                backupRestoreViewModel.provideAuthenticatedLease(request.nonce, lease)
                                vaultViewModel.lock(clearOwnedClipboard = false)
                            }
                        }
                        VaultAccessState.Locked -> if (
                            request.status == BackupAuthenticationStatus.AUTHENTICATING
                        ) {
                            backupRestoreViewModel.authenticationReturnedLocked(request.nonce)
                        }
                        is VaultAccessState.Error -> {
                            backupRestoreViewModel.authenticationCancelled(request.nonce)
                            vaultViewModel.lock(clearOwnedClipboard = false)
                        }
                    }
                }
            }

            SideEffect {
                applyEdgeToEdgeStyle(darkTheme)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            TaskLedgerTheme(settings.themeMode, settings.accentColor) {
                AdaptiveTaskLedgerScaffold(
                    selected = selected,
                    onSelected = { destination ->
                        if (!showBackup || backupUiState.task == BackupRestoreTask.NONE) {
                            if (showVault) vaultViewModel.lock()
                            selectedOverride = destination.name
                            auxiliaryName = null
                            viewModel.setLastDestination(destination)
                        }
                    },
                    onSettings = {
                        if (!showBackup || backupUiState.task == BackupRestoreTask.NONE) {
                            if (showVault) vaultViewModel.lock()
                            auxiliaryName = if (auxiliary == AuxiliaryScreen.SETTINGS) {
                                null
                            } else {
                                AuxiliaryScreen.SETTINGS.name
                            }
                        }
                    },
                    isSettings = auxiliary != null,
                    auxiliaryTitle = when (auxiliary) {
                        AuxiliaryScreen.SETTINGS -> "Settings"
                        AuxiliaryScreen.VAULT -> "Vault"
                        AuxiliaryScreen.BACKUP -> "Backup & restore"
                        null -> null
                    },
                    foldingFeature = foldingFeature,
                ) { contentPadding ->
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    ) {
                        val isWide = usesWideFeatureLayout(
                            measuredWidth = maxWidth,
                            safePaneLayout = LocalSafePaneLayout.current,
                        )
                        when (auxiliary) {
                            AuxiliaryScreen.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                onOpenVault = { auxiliaryName = AuxiliaryScreen.VAULT.name },
                                onOpenBackup = { auxiliaryName = AuxiliaryScreen.BACKUP.name },
                                onDefaultReminderOffsetsChange =
                                    viewModel::setDefaultReminderOffsetsMinutes,
                                modifier = Modifier.fillMaxSize(),
                                isWide = isWide,
                            )

                            AuxiliaryScreen.VAULT -> VaultScreen(
                                viewModel = vaultViewModel,
                                onBack = {
                                    vaultViewModel.lock()
                                    auxiliaryName = AuxiliaryScreen.SETTINGS.name
                                },
                                isWide = isWide,
                                modifier = Modifier.fillMaxSize(),
                            )

                            AuxiliaryScreen.BACKUP -> BackupRestoreScreen(
                                uiState = backupUiState,
                                actions = backupRestoreViewModel,
                                onSensitiveContentChanged = { backupSensitive = it },
                                modifier = Modifier.fillMaxSize(),
                                defaultExportFileName = defaultBackupFileName(),
                                hasIncludedPersonalBackup =
                                    BuildConfig.HAS_INCLUDED_PERSONAL_BACKUP,
                            )

                            null -> {
                                when (selected) {
                                    TopLevelDestination.TODO -> TodoScreen(
                                        viewModel = viewModel,
                                        modifier = Modifier.fillMaxSize(),
                                        isWide = isWide,
                                        requestedTodoId = reminderTodoId,
                                        quickAddRequestToken = widgetQuickAddToken.takeIf {
                                            widgetQuickAddAction == WidgetNavigation.ACTION_OPEN_TODO_LIST
                                        },
                                        onQuickAddRequestHandled = { handledToken ->
                                            if (pendingWidgetQuickAddToken == handledToken) {
                                                pendingWidgetQuickAddAction = null
                                                pendingWidgetQuickAddToken = null
                                            }
                                            selectedOverride = TopLevelDestination.TODO.name
                                            auxiliaryName = null
                                        },
                                        onRequestedTodoHandled = { handledTodoId ->
                                            if (pendingReminderTodoId == handledTodoId) {
                                                pendingReminderTodoId = null
                                            }
                                            selectedOverride = TopLevelDestination.TODO.name
                                            auxiliaryName = null
                                        },
                                    )
                                    TopLevelDestination.LEDGER -> LedgerScreen(
                                        viewModel = viewModel,
                                        modifier = Modifier.fillMaxSize(),
                                        isWide = isWide,
                                        quickAddRequestToken = widgetQuickAddToken.takeIf {
                                            widgetQuickAddAction == WidgetNavigation.ACTION_OPEN_LEDGER
                                        },
                                        onQuickAddRequestHandled = { handledToken ->
                                            if (pendingWidgetQuickAddToken == handledToken) {
                                                pendingWidgetQuickAddAction = null
                                                pendingWidgetQuickAddToken = null
                                            }
                                            selectedOverride = TopLevelDestination.LEDGER.name
                                            auxiliaryName = null
                                        },
                                    )
                                    TopLevelDestination.CALENDAR -> CalendarScreen(
                                        viewModel = viewModel,
                                        onOpenTodo = { todoId ->
                                            pendingReminderTodoId = todoId
                                            selectedOverride = TopLevelDestination.TODO.name
                                            auxiliaryName = null
                                            viewModel.setLastDestination(TopLevelDestination.TODO)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        isWide = isWide,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val reminderId = reminderTodoId(intent)
        val widgetAction = widgetQuickAddAction(intent)
        when {
            reminderId != null -> {
                pendingReminderTodoId = reminderId
                pendingWidgetQuickAddAction = null
                pendingWidgetQuickAddToken = null
            }
            widgetAction != null -> {
                pendingReminderTodoId = null
                pendingWidgetQuickAddAction = widgetAction
                pendingWidgetQuickAddToken = newWidgetRequestToken()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        (application as TaskLedgerApplication).container.vaultClipboard
            .onHostWindowFocusChanged(hasFocus)
    }

    override fun onStop() {
        (application as TaskLedgerApplication).container.vaultClipboard
            .onHostWindowFocusChanged(false)
        if (
            (application as TaskLedgerApplication).startupRecoveryCoordinator.state.value
                is StartupRecoveryState.Ready
        ) {
            val showingExternalCredentialPrompt = vaultViewModel.uiState.value
                .authenticationRequest
                ?.usesExternalDeviceCredentialPrompt(Build.VERSION.SDK_INT) == true
            if (!isChangingConfigurations && !showingExternalCredentialPrompt) {
                vaultViewModel.lock(clearOwnedClipboard = false)
            }
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingReminderTodoId?.let { outState.putLong(STATE_PENDING_TODO_ID, it) }
        pendingWidgetQuickAddAction?.let { outState.putString(STATE_PENDING_WIDGET_ACTION, it) }
        pendingWidgetQuickAddToken?.let { outState.putString(STATE_PENDING_WIDGET_TOKEN, it) }
        super.onSaveInstanceState(outState)
    }

    private fun applyEdgeToEdgeStyle(darkTheme: Boolean) {
        val background = if (darkTheme) DARK_SYSTEM_BAR_COLOR else LIGHT_SYSTEM_BAR_COLOR
        val style = if (darkTheme) {
            SystemBarStyle.dark(background)
        } else {
            SystemBarStyle.light(background, DARK_SYSTEM_BAR_COLOR)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    companion object {
        internal const val ACTION_OPEN_TODO = "com.ced2711.lifetracker.action.OPEN_TODO"
        internal const val EXTRA_TODO_ID = "com.ced2711.lifetracker.extra.TODO_ID"
        private const val STATE_PENDING_TODO_ID = "pending_reminder_todo_id"
        private const val STATE_PENDING_WIDGET_ACTION = "pending_widget_quick_add_action"
        private const val STATE_PENDING_WIDGET_TOKEN = "pending_widget_quick_add_token"
        private val DARK_SYSTEM_BAR_COLOR = 0xFF0D1514.toInt()
        private val LIGHT_SYSTEM_BAR_COLOR = 0xFFF5FBF8.toInt()

        internal fun todoReminderIntent(context: Context, todoId: Long): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_TODO
                putExtra(EXTRA_TODO_ID, todoId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        private fun reminderTodoId(intent: Intent): Long? =
            intent.takeIf { it.action == ACTION_OPEN_TODO && it.hasExtra(EXTRA_TODO_ID) }
                ?.getLongExtra(EXTRA_TODO_ID, 0L)
                ?.takeIf { it > 0L }

        private fun widgetQuickAddAction(intent: Intent): String? = intent.action?.takeIf { action ->
            action == WidgetNavigation.ACTION_OPEN_TODO_LIST ||
                action == WidgetNavigation.ACTION_OPEN_LEDGER
        }

        private fun newWidgetRequestToken(): String = UUID.randomUUID().toString()
    }
}

@Composable
private fun StartupRecoveryGate(
    state: StartupRecoveryState,
    onRetry: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            when (state) {
                StartupRecoveryState.Recovering -> {
                    CircularProgressIndicator()
                    Text(
                        text = "Finishing data recovery…",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Your tasks, ledger, and vault will open when it is safe.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                is StartupRecoveryState.Failed -> {
                    Text(
                        text = "Data recovery couldn't finish",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Life Tracker has kept your data closed to avoid conflicting changes. " +
                            "Try again before using the app.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }

                StartupRecoveryState.Ready -> Unit
            }
        }
    }
}

private enum class AuxiliaryScreen {
    SETTINGS,
    VAULT,
    BACKUP,
}

private fun defaultBackupFileName(): String =
    "LifeTracker-backup-${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}.tlb"
