package com.ced2711.lifetracker.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStateRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reminderDialogAndDraftSurviveRestorationButCancelDiscardsTheDraft() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme { SettingsStateHarness() }
        }

        composeRule.onNodeWithTag(OPEN_REMINDERS).performClick()
        composeRule.onNodeWithTag(ADD_REMINDER).performClick()
        composeRule.onNodeWithTag(REMINDER_DRAFT).assertTextEquals("60")

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(OPEN_DIALOG).assertTextEquals(SettingsDialog.DefaultReminders.name)
        composeRule.onNodeWithTag(REMINDER_DRAFT).assertTextEquals("60")

        composeRule.onNodeWithTag(CANCEL_REMINDERS).performClick()
        composeRule.onNodeWithTag(OPEN_REMINDERS).performClick()
        composeRule.onNodeWithTag(REMINDER_DRAFT).assertTextEquals("None")
    }

    @Test
    fun allDayInputSurvivesRestorationWithoutReusingTheReminderDraft() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme { SettingsStateHarness() }
        }

        composeRule.onNodeWithTag(OPEN_REMINDERS).performClick()
        composeRule.onNodeWithTag(ADD_REMINDER).performClick()
        composeRule.onNodeWithTag(CANCEL_REMINDERS).performClick()
        composeRule.onNodeWithTag(OPEN_ALL_DAY_TIME).performClick()
        composeRule.onNodeWithTag(EDIT_ALL_DAY_TIME).performClick()
        composeRule.onNodeWithTag(ALL_DAY_INPUT).assertTextEquals("9:45 PM")

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(OPEN_DIALOG).assertTextEquals(SettingsDialog.AllDayReminderTime.name)
        composeRule.onNodeWithTag(ALL_DAY_INPUT).assertTextEquals("9:45 PM")

        composeRule.onNodeWithTag(CANCEL_ALL_DAY_TIME).performClick()
        composeRule.onNodeWithTag(OPEN_ALL_DAY_TIME).performClick()
        composeRule.onNodeWithTag(ALL_DAY_INPUT).assertTextEquals("12:00 AM")
    }
}

@Composable
private fun SettingsStateHarness() {
    var dialog by rememberSettingsDialogState()

    Column {
        Text(dialog?.name ?: "None", Modifier.testTag(OPEN_DIALOG))
        Button(
            onClick = { dialog = SettingsDialog.DefaultReminders },
            modifier = Modifier.testTag(OPEN_REMINDERS),
        ) { Text("Open reminders") }
        Button(
            onClick = { dialog = SettingsDialog.AllDayReminderTime },
            modifier = Modifier.testTag(OPEN_ALL_DAY_TIME),
        ) { Text("Open all-day time") }

        when (dialog) {
            SettingsDialog.DefaultReminders -> ReminderDraftHarness(
                onCancel = { dialog = null },
            )
            SettingsDialog.AllDayReminderTime -> AllDayTimeDraftHarness(
                onCancel = { dialog = null },
            )
            else -> Unit
        }
    }
}

@Composable
private fun ReminderDraftHarness(onCancel: () -> Unit) {
    var draft by rememberReminderOffsetsDraft(emptySet())

    Text(
        draft.sorted().joinToString(",").ifEmpty { "None" },
        Modifier.testTag(REMINDER_DRAFT),
    )
    Button(
        onClick = { draft = draft + 60L },
        modifier = Modifier.testTag(ADD_REMINDER),
    ) { Text("Add reminder") }
    Button(
        onClick = onCancel,
        modifier = Modifier.testTag(CANCEL_REMINDERS),
    ) { Text("Cancel") }
}

@Composable
private fun AllDayTimeDraftHarness(onCancel: () -> Unit) {
    var input by rememberAllDayReminderInput(initialMinute = 0, uses24Hour = false)

    Text(input, Modifier.testTag(ALL_DAY_INPUT))
    Button(
        onClick = { input = "9:45 PM" },
        modifier = Modifier.testTag(EDIT_ALL_DAY_TIME),
    ) { Text("Edit all-day time") }
    Button(
        onClick = onCancel,
        modifier = Modifier.testTag(CANCEL_ALL_DAY_TIME),
    ) { Text("Cancel") }
}

private const val OPEN_DIALOG = "open_dialog"
private const val OPEN_REMINDERS = "open_reminders"
private const val REMINDER_DRAFT = "reminder_draft"
private const val ADD_REMINDER = "add_reminder"
private const val CANCEL_REMINDERS = "cancel_reminders"
private const val OPEN_ALL_DAY_TIME = "open_all_day_time"
private const val ALL_DAY_INPUT = "all_day_input"
private const val EDIT_ALL_DAY_TIME = "edit_all_day_time"
private const val CANCEL_ALL_DAY_TIME = "cancel_all_day_time"
