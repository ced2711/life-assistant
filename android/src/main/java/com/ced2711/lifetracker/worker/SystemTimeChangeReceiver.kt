package com.ced2711.lifetracker.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ced2711.lifetracker.TaskLedgerApplication
import com.ced2711.lifetracker.widget.WidgetRefreshCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives only system wall-clock changes that invalidate locally calculated work delays. */
class SystemTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val application = appContext as TaskLedgerApplication
                WorkScheduler.cancelRemindersAfterSystemTimeChange(appContext)
                if (application.startupRecoveryCoordinator.awaitReady()) {
                    WorkScheduler.rescheduleAfterSystemTimeChange(appContext)
                    WidgetRefreshCoordinator.refresh(appContext)
                    application.launcherIconMoodCoordinator.refresh()
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
