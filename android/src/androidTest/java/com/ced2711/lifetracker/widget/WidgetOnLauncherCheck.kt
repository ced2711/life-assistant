package com.ced2711.lifetracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ced2711.lifetracker.MainActivity
import com.ced2711.lifetracker.TaskLedgerApplication
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.TodoPriority
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A manual check, not part of the normal test run: puts example todos into the app on an emulator
 * and asks the launcher to pin the widget, so that it can be looked at on a real home screen
 * (rounded corners, the scrolling list, taps). Run it with
 *
 *   -Pandroid.testInstrumentationRunnerArguments.widgetOnLauncher=true
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.ced2711.lifetracker.widget.WidgetOnLauncherCheck
 *
 * and confirm the launcher's "Add to home screen" dialog while it waits.
 */
@RunWith(AndroidJUnit4::class)
class WidgetOnLauncherCheck {
    @Test
    fun pinTheWidgetWithExampleTodos() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("widgetOnLauncher") == "true")
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TaskLedgerApplication
        val today = LocalDate.now().toEpochDay()
        runBlocking {
            assertTrue(application.startupRecoveryCoordinator.awaitReady())
            val repository = application.container.repository
            listOf(
                TodoDraft(description = "Renew passport", deadlineEpochDay = today - 2, priority = TodoPriority.URGENT),
                TodoDraft(description = "Morning walk", deadlineEpochDay = today, deadlineMinute = 7 * 60 + 30, priority = TodoPriority.MEDIUM),
                TodoDraft(description = "Water the plants", deadlineEpochDay = today, deadlineMinute = 18 * 60, recurrence = RecurrenceRule(RecurrenceUnit.WEEK, 1, null)),
                TodoDraft(description = "Send the quarterly report to the client", deadlineEpochDay = today, priority = TodoPriority.HIGH),
                TodoDraft(description = "Call the dentist", deadlineEpochDay = today, priority = TodoPriority.LOW),
                TodoDraft(description = "Book flights", deadlineEpochDay = today),
                TodoDraft(description = "Plan the week", deadlineEpochDay = today),
            ).forEach { repository.saveTodo(it) }
        }
        val manager = AppWidgetManager.getInstance(application)
        val provider = ComponentName(application, TodayTodoWidgetReceiver::class.java)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue("the launcher cannot pin widgets", manager.requestPinAppWidget(provider, null, null)) }
            // Someone (or a script) confirms the launcher's dialog meanwhile.
            val deadline = System.currentTimeMillis() + 90_000
            while (manager.getAppWidgetIds(provider).isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(500)
        }
        assertTrue("the widget was not added", manager.getAppWidgetIds(provider).isNotEmpty())
    }
}
