package com.ced2711.lifetracker

import android.app.Application
import com.ced2711.lifetracker.data.attachment.AttachmentStore
import com.ced2711.lifetracker.data.backup.BackupRepository
import com.ced2711.lifetracker.data.cloud.AndroidCloudSyncEngine
import com.ced2711.lifetracker.data.cloud.CloudSyncPreferences
import com.ced2711.lifetracker.data.cloud.CloudSyncSecretStore
import com.ced2711.lifetracker.data.cloud.GoogleDriveAuthorization
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.repository.TaskLedgerRepository
import com.ced2711.lifetracker.data.settings.SettingsRepository
import com.ced2711.lifetracker.data.vault.VaultClipboard
import com.ced2711.lifetracker.data.vault.VaultKeyManager
import com.ced2711.lifetracker.data.vault.VaultRepository
import com.ced2711.lifetracker.launcher.LauncherIconMoodCoordinator
import com.ced2711.lifetracker.worker.WorkScheduler
import com.ced2711.lifetracker.worker.CloudSyncScheduler
import com.ced2711.lifetracker.widget.WidgetRefreshCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TaskLedgerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val launcherIconMoodCoordinator: LauncherIconMoodCoordinator by lazy {
        LauncherIconMoodCoordinator(this, container.repository, processScope)
    }
    val startupRecoveryCoordinator: StartupRecoveryCoordinator by lazy {
        StartupRecoveryCoordinator(
            scope = processScope,
            recoverPendingRestore = {
                container.backupRepository.recoverPendingRestore() != null
            },
            onReady = { recoveredRestore ->
                runCatching { launcherIconMoodCoordinator.start() }
                runCatching {
                    WidgetRefreshCoordinator.observeRoomChanges(this, container.repository)
                }
                if (recoveredRestore) {
                    runCatching {
                        WorkScheduler.rescheduleAfterSystemTimeChange(this@TaskLedgerApplication)
                    }
                    runCatching {
                        WidgetRefreshCoordinator.refresh(this@TaskLedgerApplication)
                    }
                } else {
                    runCatching {
                        WorkScheduler.schedulePeriodicMaintenance(this@TaskLedgerApplication)
                    }
                }
                runCatching {
                    val cloudSettings = container.cloudSyncPreferences.read()
                    CloudSyncScheduler.update(
                        this@TaskLedgerApplication,
                        cloudSettings.enabled && cloudSettings.automaticSync,
                    )
                    if (cloudSettings.enabled && cloudSettings.automaticSync) {
                        CloudSyncScheduler.enqueueNow(this@TaskLedgerApplication)
                    }
                }
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        startupRecoveryCoordinator.start()
    }
}

class AppContainer(application: Application) {
    val appContext = application.applicationContext
    val database: TaskLedgerDatabase = TaskLedgerDatabase.getInstance(application)
    val repository = TaskLedgerRepository(database)
    val settingsRepository = SettingsRepository(application)
    val attachmentStore = AttachmentStore(application, database.dao())
    val vaultKeyManager = VaultKeyManager(application)
    val vaultRepository = VaultRepository(database, vaultKeyManager)
    val backupRepository = BackupRepository(
        context = application,
        backupDao = database.backupDao(),
        settingsRepository = settingsRepository,
        vaultRepository = vaultRepository,
    )
    val cloudSyncPreferences = CloudSyncPreferences(application)
    val cloudSyncSecretStore = CloudSyncSecretStore(application)
    val googleDriveAuthorization = GoogleDriveAuthorization(application)
    val cloudSyncEngine = AndroidCloudSyncEngine(
        context = application,
        backupRepository = backupRepository,
        preferences = cloudSyncPreferences,
        secretStore = cloudSyncSecretStore,
        authorization = googleDriveAuthorization,
        afterRestore = {
            WorkScheduler.rescheduleAfterSystemTimeChange(application)
            WidgetRefreshCoordinator.refresh(application)
        },
    )
    // ClipboardManager construction requires a Looper on API 26. Startup recovery builds the
    // rest of this container on a background dispatcher, while clipboard access is UI-only.
    val vaultClipboard: VaultClipboard by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        VaultClipboard(application)
    }
}
