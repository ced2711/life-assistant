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

/**
 * Default reminders are pills that apply at once since the redesign, so only two things are
 * left to lose on rotation: which dialog is open, and a half-typed all-day reminder time.
 */
@RunWith(AndroidJUnit4::class)
class SettingsStateRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openDialogSurvivesRestorationAndClosingForgetsIt() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme { SettingsStateHarness() }
        }

        composeRule.onNodeWithTag(OPEN_LICENSE).performClick()
        composeRule.onNodeWithTag(OPEN_DIALOG).assertTextEquals(SettingsDialog.License.name)

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(OPEN_DIALOG).assertTextEquals(SettingsDialog.License.name)

        composeRule.onNodeWithTag(CLOSE_DIALOG).performClick()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag(OPEN_DIALOG).assertTextEquals("None")
    }

    @Test
    fun allDayInputSurvivesRestorationButCancelDiscardsIt() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme { SettingsStateHarness() }
        }

        composeRule.onNodeWithTag(OPEN_LICENSE).performClick()
        composeRule.onNodeWithTag(CLOSE_DIALOG).performClick()
        composeRule.onNodeWithTag(OPEN_ALL_DAY_TIME).performClick()
        composeRule.onNodeWithTag(EDIT_ALL_DAY_TIME).performClick()
        composeRule.onNodeWithTag(ALL_DAY_INPUT).assertTextEquals("9:45 PM")

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(OPEN_DIALOG).assertTextEquals(SettingsDialog.AllDayReminderTime.name)
        composeRule.onNodeWithTag(ALL_DAY_INPUT).assertTextEquals("9:45 PM")

        composeRule.onNodeWithTag(CLOSE_DIALOG).performClick()
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
            onClick = { dialog = SettingsDialog.License },
            modifier = Modifier.testTag(OPEN_LICENSE),
        ) { Text("Open license") }
        Button(
            onClick = { dialog = SettingsDialog.AllDayReminderTime },
            modifier = Modifier.testTag(OPEN_ALL_DAY_TIME),
        ) { Text("Open all-day time") }
        Button(
            onClick = { dialog = null },
            modifier = Modifier.testTag(CLOSE_DIALOG),
        ) { Text("Close") }

        if (dialog == SettingsDialog.AllDayReminderTime) AllDayTimeDraftHarness()
    }
}

@Composable
private fun AllDayTimeDraftHarness() {
    var input by rememberAllDayReminderInput(initialMinute = 0, uses24Hour = false)

    Text(input, Modifier.testTag(ALL_DAY_INPUT))
    Button(
        onClick = { input = "9:45 PM" },
        modifier = Modifier.testTag(EDIT_ALL_DAY_TIME),
    ) { Text("Edit all-day time") }
}

private const val OPEN_DIALOG = "open_dialog"
private const val OPEN_LICENSE = "open_license"
private const val CLOSE_DIALOG = "close_dialog"
private const val OPEN_ALL_DAY_TIME = "open_all_day_time"
private const val ALL_DAY_INPUT = "all_day_input"
private const val EDIT_ALL_DAY_TIME = "edit_all_day_time"
