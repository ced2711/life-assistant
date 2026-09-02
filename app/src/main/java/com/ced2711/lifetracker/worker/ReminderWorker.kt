package com.ced2711.lifetracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ced2711.lifetracker.TaskLedgerApplication
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val todoId = inputData.getLong(WorkScheduler.KEY_TODO_ID, 0L)
        val offsetMinutes = inputData.getLong(WorkScheduler.KEY_OFFSET_MINUTES, Long.MIN_VALUE)
        val expectedTriggerAtMillis = inputData.getLong(
            WorkScheduler.KEY_TRIGGER_AT_MILLIS,
            Long.MIN_VALUE,
        )
        if (
            todoId <= 0L ||
            offsetMinutes == Long.MIN_VALUE ||
            expectedTriggerAtMillis == Long.MIN_VALUE
        ) {
            return Result.failure()
        }

        return try {
            val application = applicationContext as TaskLedgerApplication
            if (!application.startupRecoveryCoordinator.awaitReady()) return Result.retry()
            val container = application.container
            val settings = container.settingsRepository.settings.first()
            if (!settings.notificationsEnabled) {
                return Result.success()
            }

            val todo = container.repository.getTodo(todoId) ?: return Result.success()
            if (todo.completedAt != null || todo.deletedAt != null || todo.deadlineEpochDay == null) {
                return Result.success()
            }
            val reminderStillExists = container.repository.getReminders(todoId).any {
                it.offsetMinutes == offsetMinutes
            }
            if (!reminderStillExists) return Result.success()

            val window = calculateReminderWindow(
                deadlineEpochDay = todo.deadlineEpochDay,
                deadlineMinute = todo.deadlineMinute,
                defaultAllDayMinute = settings.defaultAllDayReminderMinute,
                offsetMinutes = offsetMinutes,
                zone = ZoneId.systemDefault(),
            ) ?: return Result.success()
            if (window.triggerAtMillis != expectedTriggerAtMillis) return Result.success()
            if (window.stateAt(System.currentTimeMillis()) != ReminderWindowState.DUE) {
                return Result.success()
            }

            val deliveryResult = NotificationHelper.showTodoReminder(
                context = applicationContext,
                todo = todo,
                deliveryKey = ReminderDeliveryKey(
                    todoId = todo.id,
                    offsetMinutes = offsetMinutes,
                    triggerAtMillis = window.triggerAtMillis,
                ),
            )
            when (deliveryResult) {
                NotificationDeliveryResult.DELIVERED,
                NotificationDeliveryResult.ALREADY_DELIVERED,
                NotificationDeliveryResult.BLOCKED_BY_PERMISSION -> Result.success()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
