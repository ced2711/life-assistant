package com.ced2711.lifetracker.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ced2711.lifetracker.MainActivity
import com.ced2711.lifetracker.R
import com.ced2711.lifetracker.TaskLedgerApplication
import com.ced2711.lifetracker.data.cloud.AndroidCloudSyncResult
import com.ced2711.lifetracker.data.cloud.CloudSyncAttention
import com.ced2711.lifetracker.ui.localization.translateUiText
import kotlinx.coroutines.flow.first

class CloudSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as TaskLedgerApplication
        if (!application.startupRecoveryCoordinator.awaitReady()) return Result.retry()
        val container = application.container
        val settings = container.cloudSyncPreferences.read()
        if (!settings.enabled || !settings.automaticSync) return Result.success()
        return when (val result = container.cloudSyncEngine.synchronize()) {
            AndroidCloudSyncResult.Disabled,
            AndroidCloudSyncResult.UpToDate,
            is AndroidCloudSyncResult.Uploaded,
            is AndroidCloudSyncResult.Downloaded,
            -> {
                container.cloudSyncPreferences.setAttention(null)
                Result.success()
            }

            is AndroidCloudSyncResult.Conflict -> {
                container.cloudSyncPreferences.setAttention(CloudSyncAttention.CONFLICT)
                notifyAttention("Google Drive has conflicting changes. Open Backup & sync to choose a version.")
                Result.success()
            }
            is AndroidCloudSyncResult.NeedsVaultUnlock -> {
                container.cloudSyncPreferences.setAttention(CloudSyncAttention.VAULT_UNLOCK)
                notifyAttention("Unlock Vault in Life Assistant to finish the encrypted cloud sync.")
                Result.success()
            }
            AndroidCloudSyncResult.NeedsGoogleConsent -> {
                container.cloudSyncPreferences.setAttention(CloudSyncAttention.GOOGLE_CONSENT)
                notifyAttention("Open Life Assistant to reconnect Google Drive.")
                Result.success()
            }
            is AndroidCloudSyncResult.Failed -> {
                container.cloudSyncPreferences.setAttention(CloudSyncAttention.FAILED)
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    private suspend fun notifyAttention(message: String) {
        val appSettings = (applicationContext as TaskLedgerApplication)
            .container.settingsRepository.settings.first()
        if (!appSettings.notificationsEnabled) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return

        val localizedMessage = translateUiText(message, appSettings.uiLanguage)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                translateUiText("Cloud sync", appSettings.uiLanguage),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = translateUiText(
                    "Google Drive sync needs attention",
                    appSettings.uiLanguage,
                )
            },
        )
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_CLOUD_SYNC
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(translateUiText("Life Assistant cloud sync", appSettings.uiLanguage))
            .setContentText(localizedMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(localizedMessage))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "cloud_sync"
        const val NOTIFICATION_ID = 4_210
    }
}
