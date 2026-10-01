package com.ced2711.lifetracker.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.ced2711.lifetracker.TaskLedgerApplication
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WorkScheduler {
    internal const val KEY_TODO_ID = "todo_id"
    internal const val KEY_OFFSET_MINUTES = "offset_minutes"
    internal const val KEY_TRIGGER_AT_MILLIS = "trigger_at_millis"
    internal const val KEY_TODO_MATERIALIZE_THROUGH = "todo_materialize_through"
    internal const val KEY_RECURRENCE_CURSOR = "recurrence_cursor"
    internal const val KEY_REMINDER_RESCHEDULE_POLICY = "reminder_reschedule_policy"
    internal const val KEY_FORCE_REMINDER_RESET = "force_reminder_reset"

    private const val REMINDER_TAG = "taskledger_reminder"
    private const val BACKGROUND_TAG = "taskledger_background"
    internal const val CATCH_UP_NOW_WORK = "taskledger_catch_up_now"
    internal const val TODO_MATERIALIZATION_WORK = "taskledger_todo_materialization"
    private const val CATCH_UP_PERIODIC_WORK = "taskledger_catch_up_periodic"
    private const val MAINTENANCE_PERIODIC_WORK = "taskledger_maintenance_periodic"
    private val reminderReconciliationMutex = Mutex()

    fun schedulePeriodicMaintenance(context: Context) {
        scheduleBackgroundWork(
            context = context,
            immediateCatchUpPolicy = ExistingWorkPolicy.KEEP,
            periodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
        )
    }

    /**
     * Invalidates wall-clock reminders before queuing their catch-up replacement.
     *
     * Waiting for cancellation here prevents an old reminder from firing while the replacement
     * catch-up work is still waiting for a scheduler slot.
     */
    suspend fun rescheduleAfterSystemTimeChange(context: Context) {
        reminderReconciliationMutex.withLock {
            cancelRemindersAfterSystemTimeChangeLocked(context)
            enqueueAfterSystemTimeChange(context)
        }
    }

    /** Safe to run before startup recovery because it never reads or writes app data. */
    suspend fun cancelRemindersAfterSystemTimeChange(context: Context) {
        reminderReconciliationMutex.withLock {
            cancelRemindersAfterSystemTimeChangeLocked(context)
        }
    }

    private suspend fun cancelRemindersAfterSystemTimeChangeLocked(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelAllWorkByTag(REMINDER_TAG)
            .await()
        NotificationHelper.cancelAll(context.applicationContext)
    }

    private fun enqueueAfterSystemTimeChange(context: Context) {
        scheduleBackgroundWork(
            context = context,
            immediateCatchUpPolicy = ExistingWorkPolicy.REPLACE,
            periodicWorkPolicy = ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            forceReminderReset = true,
        )
    }

    private fun scheduleBackgroundWork(
        context: Context,
        immediateCatchUpPolicy: ExistingWorkPolicy,
        periodicWorkPolicy: ExistingPeriodicWorkPolicy,
        forceReminderReset: Boolean = false,
    ) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val nextMidnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val nextMaintenance = LocalDate.now(zone).plusDays(1)
            .atTime(3, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        workManager.enqueueUniqueWork(
            CATCH_UP_NOW_WORK,
            immediateCatchUpPolicy,
            OneTimeWorkRequestBuilder<CatchUpWorker>()
                .setInputData(workDataOf(KEY_FORCE_REMINDER_RESET to forceReminderReset))
                .addTag(BACKGROUND_TAG)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            CATCH_UP_PERIODIC_WORK,
            periodicWorkPolicy,
            PeriodicWorkRequestBuilder<CatchUpWorker>(1, TimeUnit.DAYS)
                .setInitialDelay((nextMidnight - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .addTag(BACKGROUND_TAG)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            MAINTENANCE_PERIODIC_WORK,
            periodicWorkPolicy,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .setInitialDelay((nextMaintenance - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .addTag(BACKGROUND_TAG)
                .build(),
        )
    }

    suspend fun rescheduleReminders(
        context: Context,
        policy: ReminderReschedulePolicy = ReminderReschedulePolicy.FUTURE_ONLY,
        forceReset: Boolean = false,
    ) = reconcileReminders(
        context = context,
        policy = policy,
        includeDueTodoId = null,
        forceReset = forceReset,
    )

    suspend fun rescheduleRemindersAfterTodoSave(context: Context, todoId: Long) {
        require(todoId > 0L) { "A reminder requires a persisted todo" }
        reconcileReminders(
            context = context,
            policy = ReminderReschedulePolicy.FUTURE_ONLY,
            includeDueTodoId = todoId,
            forceReset = false,
        )
    }

    private suspend fun reconcileReminders(
        context: Context,
        policy: ReminderReschedulePolicy,
        includeDueTodoId: Long?,
        forceReset: Boolean,
    ) = reminderReconciliationMutex.withLock {
        val appContext = context.applicationContext
        val application = appContext as TaskLedgerApplication
        val container = application.container
        val workManager = WorkManager.getInstance(appContext)

        val settings = container.settingsRepository.settings.first()
        if (!settings.notificationsEnabled) {
            workManager.cancelAllWorkByTag(REMINDER_TAG).await()
            NotificationHelper.cancelAll(appContext)
            return@withLock
        }
        if (forceReset) {
            workManager.cancelAllWorkByTag(REMINDER_TAG).await()
        }

        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val validDeliveryKeys = mutableSetOf<ReminderDeliveryKey>()
        val eligibleDeliveryKeys = mutableListOf<ReminderDeliveryKey>()
        container.database.dao().getAllReminders().forEach { reminder ->
            val todo = container.repository.getTodo(reminder.todoId) ?: return@forEach
            if (todo.completedAt != null || todo.deletedAt != null) return@forEach
            val window = calculateReminderWindow(
                deadlineEpochDay = todo.deadlineEpochDay,
                deadlineMinute = todo.deadlineMinute,
                defaultAllDayMinute = settings.defaultAllDayReminderMinute,
                offsetMinutes = reminder.offsetMinutes,
                zone = zone,
            ) ?: return@forEach
            val state = window.stateAt(now)
            if (state == ReminderWindowState.EXPIRED) return@forEach
            val deliveryKey = ReminderDeliveryKey(
                todoId = todo.id,
                offsetMinutes = reminder.offsetMinutes,
                triggerAtMillis = window.triggerAtMillis,
            )
            validDeliveryKeys += deliveryKey
            val effectivePolicy = reminderReschedulePolicyForTodo(
                todoId = todo.id,
                defaultPolicy = policy,
                includeDueTodoId = includeDueTodoId,
            )
            if (!shouldScheduleReminder(state, effectivePolicy)) return@forEach

            eligibleDeliveryKeys += deliveryKey
        }

        val validDeliveryTags = validDeliveryKeys.mapTo(mutableSetOf()) { it.workTag }
        val activeDeliveryTags = mutableSetOf<String>()
        workManager.getWorkInfosByTagFlow(REMINDER_TAG)
            .first()
            .filterNot { it.state.isFinished }
            .forEach { workInfo ->
                when (classifyReminderWorkTags(workInfo.tags, validDeliveryTags)) {
                    // Pre-key builds are finite; ReminderWorker safely self-invalidates their payloads.
                    ReminderWorkTagStatus.LEGACY -> Unit
                    ReminderWorkTagStatus.INVALID -> workManager.cancelWorkById(workInfo.id).await()
                    ReminderWorkTagStatus.VALID -> {
                        val deliveryTag = workInfo.tags.single(ReminderDeliveryKey::isWorkTag)
                        if (!activeDeliveryTags.add(deliveryTag)) {
                            workManager.cancelWorkById(workInfo.id).await()
                        }
                    }
                }
            }

        eligibleDeliveryKeys
            .filterNot { it.workTag in activeDeliveryTags }
            .forEach { deliveryKey ->
                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(
                        (deliveryKey.triggerAtMillis - now).coerceAtLeast(0L),
                        TimeUnit.MILLISECONDS,
                    )
                    .setInputData(
                        workDataOf(
                            KEY_TODO_ID to deliveryKey.todoId,
                            KEY_OFFSET_MINUTES to deliveryKey.offsetMinutes,
                            KEY_TRIGGER_AT_MILLIS to deliveryKey.triggerAtMillis,
                        ),
                    )
                    .addTag(REMINDER_TAG)
                    .addTag(deliveryKey.workTag)
                    .build()
                workManager.enqueueUniqueWork(
                    deliveryKey.workName,
                    ExistingWorkPolicy.KEEP,
                    request,
                ).await()
            }
        NotificationHelper.cancelPostedRemindersExcept(
            context = appContext,
            validDeliveryKeys = validDeliveryKeys,
        )
    }

    /** Appends another bounded catch-up page without applying retry backoff. */
    fun enqueueCatchUpContinuation(
        context: Context,
        reminderPolicy: ReminderReschedulePolicy,
        afterCursor: String? = null,
    ) {
        val appContext = context.applicationContext
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            CATCH_UP_NOW_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<CatchUpWorker>()
                .setInputData(
                    workDataOf(
                        KEY_RECURRENCE_CURSOR to afterCursor.orEmpty(),
                        KEY_REMINDER_RESCHEDULE_POLICY to reminderPolicy.name,
                    ),
                )
                .addTag(BACKGROUND_TAG)
                .build(),
        )
    }

    /** Continues a calendar expansion without materializing future ledger entries. */
    fun enqueueTodoMaterializationContinuation(
        context: Context,
        throughEpochDay: Long,
        reminderPolicy: ReminderReschedulePolicy,
        afterCursor: String? = null,
    ) {
        val appContext = context.applicationContext
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            TODO_MATERIALIZATION_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<CatchUpWorker>()
                .setInputData(
                    workDataOf(
                        KEY_TODO_MATERIALIZE_THROUGH to throughEpochDay,
                        KEY_RECURRENCE_CURSOR to afterCursor.orEmpty(),
                        KEY_REMINDER_RESCHEDULE_POLICY to reminderPolicy.name,
                    ),
                )
                .addTag(BACKGROUND_TAG)
                .build(),
        )
    }
}
