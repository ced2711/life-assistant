package com.ced2711.lifetracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ced2711.lifetracker.TaskLedgerApplication
import com.ced2711.lifetracker.data.repository.TaskLedgerRepository
import kotlinx.coroutines.CancellationException

class MaintenanceWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        return try {
            val application = applicationContext as TaskLedgerApplication
            if (!application.startupRecoveryCoordinator.awaitReady()) return Result.retry()
            val container = application.container
            val now = System.currentTimeMillis()
            container.attachmentStore.purgeReadyAttachments(now)
            container.repository.purgeSoftDeleted(now - TaskLedgerRepository.DEFAULT_UNDO_WINDOW)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
