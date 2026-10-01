package com.ced2711.lifetracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ced2711.lifetracker.TaskLedgerApplication
import kotlinx.coroutines.CancellationException

class CatchUpWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        return try {
            val application = applicationContext as TaskLedgerApplication
            if (!application.startupRecoveryCoordinator.awaitReady()) return Result.retry()
            val todoMaterializeThrough = inputData.getLong(
                WorkScheduler.KEY_TODO_MATERIALIZE_THROUGH,
                NO_TODO_MATERIALIZATION,
            )
            val afterCursor = inputData.getString(WorkScheduler.KEY_RECURRENCE_CURSOR)
                ?.takeIf(String::isNotEmpty)
            val reminderPolicy = parseReminderReschedulePolicy(
                inputData.getString(WorkScheduler.KEY_REMINDER_RESCHEDULE_POLICY),
            )
            val forceReminderReset = inputData.getBoolean(
                WorkScheduler.KEY_FORCE_REMINDER_RESET,
                false,
            )
            val isCalendarMaterialization = todoMaterializeThrough != NO_TODO_MATERIALIZATION
            val catchUp = if (isCalendarMaterialization) {
                application.container.repository.materializeTodoOccurrencesThrough(
                    todoMaterializeThrough,
                    afterCursor,
                )
            } else {
                application.container.repository.catchUpRecurring(afterCursor = afterCursor)
            }
            WorkScheduler.rescheduleReminders(
                applicationContext,
                reminderPolicy,
                forceReset = forceReminderReset,
            )
            if (catchUp.hasMore) {
                if (isCalendarMaterialization) {
                    WorkScheduler.enqueueTodoMaterializationContinuation(
                        applicationContext,
                        todoMaterializeThrough,
                        reminderPolicy,
                        catchUp.nextCursor,
                    )
                } else {
                    WorkScheduler.enqueueCatchUpContinuation(
                        applicationContext,
                        reminderPolicy,
                        catchUp.nextCursor,
                    )
                }
            }
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    private companion object {
        const val NO_TODO_MATERIALIZATION = Long.MIN_VALUE
    }
}
