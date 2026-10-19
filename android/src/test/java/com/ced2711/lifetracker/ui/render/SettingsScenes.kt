package com.ced2711.lifetracker.ui.render

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ced2711.lifetracker.data.cloud.CloudProvider
import com.ced2711.lifetracker.data.cloud.CloudSyncAttention
import com.ced2711.lifetracker.data.cloud.CloudSyncIndicator
import com.ced2711.lifetracker.data.cloud.CloudSyncIndicatorState
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.ui.backup.BackupContent
import com.ced2711.lifetracker.ui.backup.BackupRestorePreview
import com.ced2711.lifetracker.ui.backup.CloudSyncUiState
import com.ced2711.lifetracker.ui.backup.DailyBackupCopy
import com.ced2711.lifetracker.ui.backup.DailyBackupsUiState
import com.ced2711.lifetracker.ui.backup.GitHubCodeDialog
import com.ced2711.lifetracker.ui.backup.GitHubCodePrompt
import com.ced2711.lifetracker.ui.backup.RestorePreviewDialog
import com.ced2711.lifetracker.ui.lock.AppLockContent
import com.ced2711.lifetracker.ui.settings.SettingsContent

/*
 * Sample state for the Settings, Backup & sync and app lock pictures.
 */

private val today = RenderSamples.today
private val syncedAt = System.currentTimeMillis() - 4 * 60_000L

private val sampleSettings = AppSettings(
    notificationsEnabled = true,
    defaultAllDayReminderMinute = 9 * 60,
    defaultReminderOffsetsMinutes = setOf(0L, 24L * 60L, 2L * 60L),
    todoQuickAddFields = setOf(TodoQuickAddField.DEADLINE, TodoQuickAddField.PRIORITY),
    visibleDestinations = TopLevelDestination.entries.toSet() - TopLevelDestination.CONFESSIONAL,
    appLockEnabled = true,
)

private val notConnected = CloudSyncUiState(builtInGitHub = true)

private val connected = CloudSyncUiState(
    connected = true,
    automaticSync = true,
    lastSyncAt = syncedAt,
    provider = CloudProvider.GITHUB,
    gitHubRepository = "ced2711/life-assistant-data",
    builtInGitHub = true,
)

private val noCopies = DailyBackupsUiState(dateFormat = DateFormatOption.MONTH_DAY_YEAR)

private val twoCopies = DailyBackupsUiState(
    copies = listOf(
        DailyBackupCopy(today.minusDays(1), 412_300L),
        DailyBackupCopy(today.minusDays(2), 409_870L),
    ),
    canUndo = true,
    dateFormat = DateFormatOption.MONTH_DAY_YEAR,
)

private val samplePreview = BackupRestorePreview(
    createdAtLabel = "09/28/2026, 9:41 PM",
    todoCount = 128,
    ledgerCount = 342,
    vaultCount = 17,
    attachmentCount = 23,
    attachmentBytes = 18_400_000L,
    totalBytes = 19_250_000L,
    noteCount = 46,
    diaryCount = 61,
)

@Composable
private fun Settings(isWide: Boolean, sync: CloudSyncIndicator?, settings: AppSettings = sampleSettings, scroll: Int = 0) {
    SettingsContent(
        settings = settings,
        sync = sync,
        versionName = "2.0.0",
        systemUses24Hour = false,
        isWide = isWide,
        today = today,
        scrollState = remember(scroll) { ScrollState(scroll) },
        onOpenBackup = {},
        onTheme = {},
        onAccent = {},
        onLanguage = {},
        onVisibleDestinations = {},
        onWeekStart = {},
        onTimeFormat = {},
        onDateFormat = {},
        onQuickAddFields = {},
        onNotifications = {},
        onDefaultReminders = {},
        onEditAllDayTime = {},
        onOpenVault = {},
        onAppLock = {},
        onAppLockTimeout = {},
        onOpenLicense = {},
        onOpenSource = {},
    )
}

@Composable
private fun Backup(cloud: CloudSyncUiState, daily: DailyBackupsUiState, scroll: Int = 0) {
    BackupContent(
        cloud = cloud,
        daily = daily,
        today = today,
        scrollState = remember(scroll) { ScrollState(scroll) },
        onConnectGitHub = {},
        onSync = {},
        onReconnect = {},
        onAutomaticSync = {},
        onDisconnect = {},
        onExportRecovery = {},
        onRestoreDaily = {},
        onUndoDailyRestore = {},
        onExport = {},
        onChooseBackupFile = {},
        onRestoreIncluded = {},
    )
}

/** Scenes of the Settings screens for ScreenRenderTest; see RenderScene. */
internal val settingsScenes: List<RenderScene> = listOf(
    RenderScene("settings-top", auxiliaryTitle = "Settings") { isWide ->
        Settings(isWide, CloudSyncIndicator(CloudSyncIndicatorState.UP_TO_DATE, syncedAt))
    },
    // Further down the page: dates, todo and reminders.
    RenderScene("settings-reminders", auxiliaryTitle = "Settings") { isWide ->
        Settings(isWide, sync = null, scroll = 1_660)
    },
    // The end of the page: security and about.
    RenderScene("settings-about", auxiliaryTitle = "Settings") { isWide ->
        Settings(isWide, sync = null, scroll = 100_000)
    },
    RenderScene("backup-not-connected", auxiliaryTitle = "Backup & sync") {
        Backup(notConnected, noCopies)
    },
    RenderScene("backup-connected", auxiliaryTitle = "Backup & sync") {
        Backup(connected, twoCopies.copy(canUndo = false))
    },
    RenderScene("backup-sign-in-expired", auxiliaryTitle = "Backup & sync") {
        Backup(connected.copy(attention = CloudSyncAttention.GITHUB_SIGN_IN), twoCopies.copy(canUndo = false))
    },
    RenderScene("backup-sync-failed", auxiliaryTitle = "Backup & sync") {
        Backup(
            connected.copy(attention = CloudSyncAttention.FAILED, lastError = "Could not reach GitHub (no connection or name lookup failed)."),
            twoCopies.copy(canUndo = false),
        )
    },
    // Scrolled to the copies kept on this device, after a restore (Undo is offered).
    RenderScene("backup-daily-copies", auxiliaryTitle = "Backup & sync") {
        Backup(connected, twoCopies, scroll = 100_000)
    },
    RenderScene("backup-github-code", auxiliaryTitle = "Backup & sync") {
        Backup(notConnected.copy(task = com.ced2711.lifetracker.ui.backup.CloudSyncTask.CONNECTING), noCopies)
        GitHubCodeDialog(
            prompt = GitHubCodePrompt("WDJB-MJHT", "https://github.com/login/device"),
            onCopy = {},
            onOpenGitHub = {},
            onCancel = {},
        )
    },
    RenderScene("backup-restore-review", auxiliaryTitle = "Backup & sync") {
        Backup(connected, twoCopies.copy(canUndo = false))
        RestorePreviewDialog(preview = samplePreview, onCancel = {}, onContinue = {})
    },
    // The lock covers the whole window, so it is drawn in a window of its own.
    RenderScene("app-lock", auxiliaryTitle = "Settings") {
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize()) { AppLockContent(deviceSecure = true, error = null, onUnlock = {}) }
        }
    },
    RenderScene("app-lock-no-screen-lock", auxiliaryTitle = "Settings") {
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize()) { AppLockContent(deviceSecure = false, error = null, onUnlock = {}) }
        }
    },
)
