package com.ced2711.lifetracker.ui.todo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ced2711.lifetracker.MainActivity
import com.ced2711.lifetracker.StartupRecoveryState
import com.ced2711.lifetracker.TaskLedgerApplication
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentPickerLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun todoAttachmentPickerLaunchesFromFragmentActivity() {
        awaitMainNavigation()
        composeRule.onNodeWithContentDescription("Todo").performClick()
        composeRule.onNodeWithText("New task").performScrollTo().performClick()
        composeRule.onNodeWithText("Add files (0/10)").performScrollTo().performClick()

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                resumedActivity(instrumentation).contains("documentsui", ignoreCase = true)
            }
        } finally {
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText("Add files (0/10)").assertExists()
                true
            }.getOrDefault(false)
        }
    }

    private fun awaitMainNavigation() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val application = composeRule.activity.application as TaskLedgerApplication
            application.startupRecoveryCoordinator.state.value is StartupRecoveryState.Ready
        }
        composeRule.waitForIdle()
    }

    private fun resumedActivity(instrumentation: android.app.Instrumentation): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(
            "dumpsys activity activities | grep mResumedActivity",
        )
        return descriptor.use {
            FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
    }
}
