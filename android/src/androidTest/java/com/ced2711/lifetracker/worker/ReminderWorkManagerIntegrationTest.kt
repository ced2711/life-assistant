package com.ced2711.lifetracker.worker

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ced2711.lifetracker.TaskLedgerApplication
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.TodoDraft
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderWorkManagerIntegrationTest {
    private lateinit var application: TaskLedgerApplication
    private lateinit var workManager: WorkManager
    private val createdTodoIds = mutableListOf<Long>()

    @Before
    fun resetReminderWork() = runBlocking {
        application = ApplicationProvider.getApplicationContext()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            application.container
        }
        if (!application.startupRecoveryCoordinator.awaitReady()) {
            application.startupRecoveryCoordinator.retry()
            assertTrue(
                "startup recovery did not become ready",
                application.startupRecoveryCoordinator.awaitReady(),
            )
        }
        workManager = WorkManager.getInstance(application)
        application.container.settingsRepository.setNotificationsEnabled(false)
        WorkScheduler.rescheduleReminders(application)
        awaitCondition("existing reminder work was not cancelled") {
            activeReminderWork().isEmpty()
        }
    }

    @After
    fun cleanUp() = runBlocking {
        application.container.settingsRepository.setNotificationsEnabled(false)
        WorkScheduler.rescheduleReminders(application)
        createdTodoIds.forEach { todoId ->
            val todo = application.container.repository.getTodo(todoId)
            if (todo?.deletedAt == null) {
                application.container.repository.softDeleteTodo(todoId, undoWindowMillis = 0)
            }
        }
    }

    @Test
    fun notificationsDisabledDoesNotScheduleReminderWork() = runBlocking {
        val todoId = saveTodo(
            deadlineEpochDay = LocalDate.now().plusDays(10).toEpochDay(),
            reminderOffsets = listOf(0),
        )
        val key = deliveryKey(todoId, offsetMinutes = 0)

        WorkScheduler.rescheduleReminders(application)

        awaitCondition("disabled notifications left active reminder work") {
            activeWork(key).isEmpty()
        }
        assertTrue(activeReminderWork().isEmpty())
    }

    @Test
    fun savedTodoSchedulesItsDueWindowOnceWithoutBackfillingOtherDueTodos() = runBlocking {
        application.container.settingsRepository.setNotificationsEnabled(true)
        val now = ZonedDateTime.now()
        val dueAt = now.plusMinutes(15)
        val expiredAt = now.minusMinutes(15)
        val unrelatedDueTodoId = saveTodo(
            deadlineEpochDay = dueAt.toLocalDate().toEpochDay(),
            reminderOffsets = listOf(30),
            deadlineMinute = dueAt.hour * 60 + dueAt.minute,
        )
        val savedDueTodoId = saveTodo(
            deadlineEpochDay = dueAt.toLocalDate().toEpochDay(),
            reminderOffsets = listOf(30),
            deadlineMinute = dueAt.hour * 60 + dueAt.minute,
        )
        val expiredTodoId = saveTodo(
            deadlineEpochDay = expiredAt.toLocalDate().toEpochDay(),
            reminderOffsets = listOf(30),
            deadlineMinute = expiredAt.hour * 60 + expiredAt.minute,
        )
        val unrelatedDueKey = deliveryKey(unrelatedDueTodoId, offsetMinutes = 30)
        val savedDueKey = deliveryKey(savedDueTodoId, offsetMinutes = 30)
        val expiredKey = deliveryKey(expiredTodoId, offsetMinutes = 30)

        WorkScheduler.rescheduleRemindersAfterTodoSave(application, savedDueTodoId)

        awaitSingleRecordedWork(savedDueKey)
        assertEquals(1, recordedWork(savedDueKey).size)
        assertTrue(recordedWork(unrelatedDueKey).isEmpty())
        assertTrue(recordedWork(expiredKey).isEmpty())
    }

    @Test
    fun todoSavePreservesExistingTaggedAndLegacyUnrelatedDueWork() = runBlocking {
        application.container.settingsRepository.setNotificationsEnabled(true)
        val dueAt = ZonedDateTime.now().plusMinutes(15)
        val dueMinute = dueAt.hour * 60 + dueAt.minute
        val taggedTodoId = saveTodo(
            deadlineEpochDay = dueAt.toLocalDate().toEpochDay(),
            reminderOffsets = listOf(30),
            deadlineMinute = dueMinute,
        )
        val legacyTodoId = saveTodo(
            deadlineEpochDay = dueAt.toLocalDate().toEpochDay(),
            reminderOffsets = listOf(30),
            deadlineMinute = dueMinute,
        )
        val taggedKey = deliveryKey(taggedTodoId, offsetMinutes = 30)
        val legacyKey = deliveryKey(legacyTodoId, offsetMinutes = 30)
        // A long delay models work enqueued while EARLY that is still pending after becoming DUE.
        val taggedWork = enqueuePendingReminderWork(taggedKey)
        val legacyWork = enqueuePendingReminderWork(legacyKey, includeDeliveryTag = false)
        val savedTodoId = saveTodo(
            deadlineEpochDay = LocalDate.now().plusDays(10).toEpochDay(),
            reminderOffsets = listOf(0),
        )

        WorkScheduler.rescheduleRemindersAfterTodoSave(application, savedTodoId)

        assertEquals(taggedWork.id, awaitSingleActiveWork(taggedKey).id)
        assertEquals(legacyWork.id, awaitSingleActiveWork(legacyKey).id)
    }

    @Test
    fun recurringSavedDueWorkSurvivesSubsequentFutureOnlyCatchUp() = runBlocking {
        application.container.settingsRepository.setNotificationsEnabled(true)
        val dueAt = ZonedDateTime.now().plusMinutes(15)
        val todoId = saveTodo(
            deadlineEpochDay = dueAt.toLocalDate().toEpochDay(),
            reminderOffsets = listOf(30),
            deadlineMinute = dueAt.hour * 60 + dueAt.minute,
            recurrence = RecurrenceRule(
                RecurrenceUnit.DAY,
                endEpochDay = dueAt.toLocalDate().toEpochDay(),
            ),
        )
        val key = deliveryKey(todoId, offsetMinutes = 30)
        val savedDueWork = enqueuePendingReminderWork(key)

        application.container.repository.catchUpRecurring()
        WorkScheduler.rescheduleReminders(application, ReminderReschedulePolicy.FUTURE_ONLY)

        assertEquals(savedDueWork.id, awaitSingleActiveWork(key).id)
    }

    @Test
    fun futureOnlyCancelsTaggedExpiredWork() = runBlocking {
        application.container.settingsRepository.setNotificationsEnabled(true)
        val expiredAt = ZonedDateTime.now().minusMinutes(15)
        val todoId = saveTodo(
            deadlineEpochDay = expiredAt.toLocalDate().toEpochDay(),
            reminderOffsets = listOf(30),
            deadlineMinute = expiredAt.hour * 60 + expiredAt.minute,
        )
        val key = deliveryKey(todoId, offsetMinutes = 30)
        val expiredWork = enqueuePendingReminderWork(key)

        WorkScheduler.rescheduleReminders(application, ReminderReschedulePolicy.FUTURE_ONLY)

        awaitWorkInactive(expiredWork.id)
        assertTrue(activeWork(key).isEmpty())
    }

    @Test
    fun concurrentOldDeadlineAndSavedDuePassesConvergeOnNewestWork() = runBlocking {
        application.container.settingsRepository.setNotificationsEnabled(true)
        val originalDay = LocalDate.now().plusDays(10).toEpochDay()
        val todoId = saveTodo(originalDay, reminderOffsets = listOf(0))
        val originalKey = deliveryKey(todoId, offsetMinutes = 0)
        WorkScheduler.rescheduleReminders(application)
        val originalWork = awaitSingleActiveWork(originalKey)

        val dueAt = ZonedDateTime.now().plusMinutes(15)
        saveTodo(
            deadlineEpochDay = dueAt.toLocalDate().toEpochDay(),
            reminderOffsets = listOf(30),
            todoId = todoId,
            deadlineMinute = dueAt.hour * 60 + dueAt.minute,
        )
        val newestKey = deliveryKey(todoId, offsetMinutes = 30)

        coroutineScope {
            val futureOnlyPass = async(start = CoroutineStart.UNDISPATCHED) {
                WorkScheduler.rescheduleReminders(
                    application,
                    ReminderReschedulePolicy.FUTURE_ONLY,
                )
            }
            val savedTodoPass = async(start = CoroutineStart.UNDISPATCHED) {
                WorkScheduler.rescheduleRemindersAfterTodoSave(application, todoId)
            }
            futureOnlyPass.await()
            savedTodoPass.await()
        }

        awaitWorkInactive(originalWork.id)
        awaitSingleRecordedWork(newestKey)
        assertEquals(1, recordedWork(newestKey).size)
    }

    @Test
    fun rapidNotificationDisableEnableFinishesEnabledWithOneFreshWorker() = runBlocking {
        application.container.settingsRepository.setNotificationsEnabled(true)
        val todoId = saveTodo(
            deadlineEpochDay = LocalDate.now().plusDays(10).toEpochDay(),
            reminderOffsets = listOf(0),
        )
        val key = deliveryKey(todoId, offsetMinutes = 0)
        WorkScheduler.rescheduleReminders(application)
        val originalWork = awaitSingleActiveWork(key)

        application.container.settingsRepository.setNotificationsEnabled(false)
        coroutineScope {
            val disablePass = async(start = CoroutineStart.UNDISPATCHED) {
                WorkScheduler.rescheduleReminders(application)
            }
            application.container.settingsRepository.setNotificationsEnabled(true)
            val enablePass = async(start = CoroutineStart.UNDISPATCHED) {
                WorkScheduler.rescheduleReminders(application)
            }
            disablePass.await()
            enablePass.await()
        }

        assertTrue(application.container.settingsRepository.settings.first().notificationsEnabled)
        val enabledWork = awaitSingleActiveWork(key)
        assertNotEquals(originalWork.id, enabledWork.id)
        assertEquals(1, activeWork(key).size)
        awaitWorkInactive(originalWork.id)
    }

    @Test
    fun editingTodoReplacesOldScheduleWithoutDuplicateActiveWork() = runBlocking {
        application.container.settingsRepository.setNotificationsEnabled(true)
        val originalDay = LocalDate.now().plusDays(10).toEpochDay()
        val todoId = saveTodo(originalDay, reminderOffsets = listOf(15))
        val originalKey = deliveryKey(todoId, offsetMinutes = 15)

        WorkScheduler.rescheduleReminders(application)
        val originalWorkId = awaitSingleActiveWork(originalKey).id

        saveTodo(
            deadlineEpochDay = originalDay + 1,
            reminderOffsets = listOf(15),
            todoId = todoId,
        )
        val editedKey = deliveryKey(todoId, offsetMinutes = 15)
        WorkScheduler.rescheduleReminders(application)

        assertNotEquals(originalKey.workName, editedKey.workName)
        awaitWorkInactive(originalWorkId)
        val editedWork = awaitSingleActiveWork(editedKey)
        assertNotEquals(originalWorkId, editedWork.id)
        assertEquals(1, activeWork(editedKey).size)
    }

    @Test
    fun timezoneChangeCancelsOldWorkerBeforeCatchUpReplacement() = runBlocking {
        application.container.settingsRepository.setNotificationsEnabled(true)
        val todoId = saveTodo(
            deadlineEpochDay = LocalDate.now().plusDays(10).toEpochDay(),
            reminderOffsets = listOf(30),
        )
        val key = deliveryKey(todoId, offsetMinutes = 30)
        WorkScheduler.rescheduleReminders(application)
        val oldWorkId = awaitSingleActiveWork(key).id

        SystemTimeChangeReceiver().onReceive(
            application,
            Intent(Intent.ACTION_TIMEZONE_CHANGED),
        )

        awaitWorkInactive(oldWorkId)
        val replacement = awaitSingleActiveWork(key)
        assertNotEquals(oldWorkId, replacement.id)
        assertEquals(1, activeWork(key).size)
    }

    @Test
    fun multipleOffsetsKeepStableIndependentIdentitiesWhenReminderRowsAreReplaced() = runBlocking {
        application.container.settingsRepository.setNotificationsEnabled(true)
        val deadline = LocalDate.now().plusDays(10).toEpochDay()
        val todoId = saveTodo(deadline, reminderOffsets = listOf(0, 30, 0))
        val dueKey = deliveryKey(todoId, offsetMinutes = 0)
        val earlyKey = deliveryKey(todoId, offsetMinutes = 30)

        WorkScheduler.rescheduleReminders(application)
        awaitSingleActiveWork(dueKey)
        awaitSingleActiveWork(earlyKey)

        saveTodo(
            deadlineEpochDay = deadline,
            reminderOffsets = listOf(30, 0),
            todoId = todoId,
        )
        WorkScheduler.rescheduleReminders(application)

        assertNotEquals(dueKey.workName, earlyKey.workName)
        assertEquals(dueKey, deliveryKey(todoId, offsetMinutes = 0))
        assertEquals(earlyKey, deliveryKey(todoId, offsetMinutes = 30))
        awaitSingleActiveWork(dueKey)
        awaitSingleActiveWork(earlyKey)
        assertEquals(1, activeWork(dueKey).size)
        assertEquals(1, activeWork(earlyKey).size)
    }

    @Test
    fun backgroundReplacementDoesNotCancelCalendarContinuationChain() = runBlocking {
        assertNotEquals(
            WorkScheduler.CATCH_UP_NOW_WORK,
            WorkScheduler.TODO_MATERIALIZATION_WORK,
        )
        WorkScheduler.enqueueTodoMaterializationContinuation(
            application,
            LocalDate.now().toEpochDay(),
            ReminderReschedulePolicy.FUTURE_ONLY,
            afterCursor = "42:0",
        )
        var calendarWork = emptyList<WorkInfo>()
        awaitCondition("calendar continuation was not enqueued") {
            calendarWork = workManager
                .getWorkInfosForUniqueWork(WorkScheduler.TODO_MATERIALIZATION_WORK)
                .get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            calendarWork.isNotEmpty()
        }

        WorkScheduler.rescheduleAfterSystemTimeChange(application)

        calendarWork.forEach { original ->
            val state = workManager.getWorkInfoById(original.id)
                .get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                ?.state
            assertFalse("calendar continuation ${original.id} was cancelled", state == WorkInfo.State.CANCELLED)
        }
        workManager.cancelUniqueWork(WorkScheduler.TODO_MATERIALIZATION_WORK)
        Unit
    }

    private suspend fun saveTodo(
        deadlineEpochDay: Long,
        reminderOffsets: List<Long>,
        todoId: Long? = null,
        deadlineMinute: Int = 12 * 60,
        recurrence: RecurrenceRule? = null,
    ): Long {
        val savedId = application.container.repository.saveTodo(
            TodoDraft(
                id = todoId,
                title = "Reminder integration ${UUID.randomUUID()}",
                description = "Reminder integration test",
                deadlineEpochDay = deadlineEpochDay,
                deadlineMinute = deadlineMinute,
                reminderOffsetsMinutes = reminderOffsets,
                recurrence = recurrence,
            ),
        )
        if (savedId !in createdTodoIds) createdTodoIds += savedId
        return savedId
    }

    private suspend fun deliveryKey(todoId: Long, offsetMinutes: Long): ReminderDeliveryKey {
        val todo = requireNotNull(application.container.repository.getTodo(todoId))
        val settings = application.container.settingsRepository.settings.first()
        val window = requireNotNull(
            calculateReminderWindow(
                deadlineEpochDay = todo.deadlineEpochDay,
                deadlineMinute = todo.deadlineMinute,
                defaultAllDayMinute = settings.defaultAllDayReminderMinute,
                offsetMinutes = offsetMinutes,
                zone = ZoneId.systemDefault(),
            ),
        )
        return ReminderDeliveryKey(todoId, offsetMinutes, window.triggerAtMillis)
    }

    private fun awaitSingleActiveWork(key: ReminderDeliveryKey): WorkInfo {
        var active = emptyList<WorkInfo>()
        awaitCondition("expected exactly one active work item for ${key.workName}") {
            active = activeWork(key)
            active.size == 1
        }
        return active.single()
    }

    private fun enqueuePendingReminderWork(
        key: ReminderDeliveryKey,
        includeDeliveryTag: Boolean = true,
    ): WorkInfo {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(1, TimeUnit.DAYS)
            .setInputData(
                workDataOf(
                    WorkScheduler.KEY_TODO_ID to key.todoId,
                    WorkScheduler.KEY_OFFSET_MINUTES to key.offsetMinutes,
                    WorkScheduler.KEY_TRIGGER_AT_MILLIS to key.triggerAtMillis,
                ),
            )
            .addTag(REMINDER_TAG)
            .apply {
                if (includeDeliveryTag) addTag(key.workTag)
            }
            .build()
        workManager.enqueueUniqueWork(key.workName, ExistingWorkPolicy.REPLACE, request)
            .result
            .get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return awaitSingleActiveWork(key)
    }

    private fun awaitSingleRecordedWork(key: ReminderDeliveryKey): WorkInfo {
        var recorded = emptyList<WorkInfo>()
        awaitCondition("expected exactly one work item for ${key.workName}") {
            recorded = recordedWork(key)
            recorded.size == 1
        }
        return recorded.single()
    }

    private fun awaitWorkInactive(workId: UUID) {
        awaitCondition("old reminder worker $workId remained active") {
            workManager.getWorkInfoById(workId).get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                ?.state
                ?.isFinished != false
        }
    }

    private fun activeWork(key: ReminderDeliveryKey): List<WorkInfo> =
        recordedWork(key)
            .filterNot { it.state.isFinished }

    private fun recordedWork(key: ReminderDeliveryKey): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(key.workName)
            .get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private fun activeReminderWork(): List<WorkInfo> =
        workManager.getWorkInfosByTag(REMINDER_TAG)
            .get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .filterNot { it.state.isFinished }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val timeoutAt = SystemClock.elapsedRealtime() + CONDITION_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < timeoutAt) {
            if (condition()) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        assertTrue(message, condition())
    }

    private companion object {
        const val REMINDER_TAG = "taskledger_reminder"
        const val FUTURE_TIMEOUT_SECONDS = 5L
        const val CONDITION_TIMEOUT_MILLIS = 15_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
