package com.ced2711.lifetracker.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import java.util.concurrent.TimeUnit

object CloudSyncScheduler {
    private const val PERIODIC_WORK = "life_tracker_cloud_sync_periodic"
    private const val IMMEDIATE_WORK = "life_tracker_cloud_sync_now"
    private const val FOLLOW_UP_WORK = "life_tracker_cloud_sync_follow_up"
    private const val TAG = "life_tracker_cloud_sync"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun update(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            manager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        manager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(TAG)
                .build(),
        )
    }

    /**
     * Requests a sync soon without ever cancelling one that is running: a request while a sync
     * is waiting for network is merged into it, and a request during a running sync queues exactly
     * one follow-up so changes made meanwhile are not missed. Call it off the main thread.
     */
    fun enqueueNow(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        val running = runCatching { manager.getWorkInfosForUniqueWork(IMMEDIATE_WORK).get() }
            .getOrDefault(emptyList())
            .any { it.state == WorkInfo.State.RUNNING }
        manager.enqueueUniqueWork(
            if (running) FOLLOW_UP_WORK else IMMEDIATE_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .build(),
        )
    }
}
