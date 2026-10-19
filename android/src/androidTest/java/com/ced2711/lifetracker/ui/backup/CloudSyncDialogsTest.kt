package com.ced2711.lifetracker.ui.backup

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the real connect dialogs on a device. A nested vertical scroll inside the hinge-safe
 * dialog once crashed both "Connect" buttons at first layout; JVM tests cannot catch that.
 */
@RunWith(AndroidJUnit4::class)
class CloudSyncDialogsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun googleDriveDialogOpens() {
        composeRule.setContent {
            MaterialTheme { CloudSyncPasswordDialog(onDismiss = {}, onConnect = { it.fill('\u0000') }) }
        }
        composeRule.onNodeWithText("Connect Google Drive").assertIsDisplayed()
        composeRule.onNodeWithText("Sync password").assertIsDisplayed()
    }

    @Test
    fun gitHubDialogOpensWithItsExtraFields() {
        composeRule.setContent {
            MaterialTheme {
                CloudSyncPasswordDialog(
                    title = "Connect GitHub",
                    showGitHubFields = true,
                    initialClientId = "Iv23liExample",
                    onDismiss = {},
                    onConnect = { it.fill('\u0000') },
                )
            }
        }
        composeRule.onNodeWithText("Connect GitHub").assertIsDisplayed()
        composeRule.onNodeWithText("GitHub Client ID").assertIsDisplayed()
        composeRule.onNodeWithText("Private repository (owner/name, optional)").assertIsDisplayed()
    }
}
