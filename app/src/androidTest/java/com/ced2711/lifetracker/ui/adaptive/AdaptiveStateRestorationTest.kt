package com.ced2711.lifetracker.ui.adaptive

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.MainActivity
import com.ced2711.lifetracker.StartupRecoveryState
import com.ced2711.lifetracker.TaskLedgerApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveStateRestorationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun newTodoDraftSurvivesActivityRecreation() {
        awaitMainNavigation()
        composeRule.onNodeWithContentDescription("Todo").performClick()
        composeRule.onNodeWithText("New task").performScrollTo().performClick()
        composeRule.onNodeWithText("Description *").assertExists().performTextInput(DRAFT_TEXT)
        composeRule.onNode(hasSetTextAction() and hasText(DRAFT_TEXT)).assertExists()

        val originalActivity = composeRule.activity
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.activity !== originalActivity
        }

        composeRule.onNodeWithText("Description *").assertExists()
        composeRule.onNode(hasSetTextAction() and hasText(DRAFT_TEXT)).assertExists()
    }

    @Test
    fun topLevelNavigationItemsExposeAccessibleDestinationNames() {
        awaitMainNavigation()
        composeRule.onNodeWithContentDescription("Todo").assertExists().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Ledger").assertExists().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Calendar").assertExists().assertHasClickAction()
    }

    private fun awaitMainNavigation() {
        // Startup recovery runs outside Compose's test clock, so Compose idleness alone does not
        // guarantee that MainActivity has replaced its recovery gate with the application scaffold.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val application = composeRule.activity.application as TaskLedgerApplication
            application.startupRecoveryCoordinator.state.value is StartupRecoveryState.Ready
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val DRAFT_TEXT = "rotation draft"
    }
}
