package com.ced2711.lifetracker.ui.adaptive

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import org.junit.Rule
import org.junit.Test

class AdaptiveAuxiliaryTopBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun auxiliaryPageExposesBackActionInsteadOfSettingsAction() {
        composeRule.setContent {
            MaterialTheme {
                AdaptiveTaskLedgerScaffold(
                    selected = TopLevelDestination.TODO,
                    onSelected = {},
                    onSettings = {},
                    isSettings = true,
                    auxiliaryTitle = "Vault",
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text("Auxiliary content")
                }
            }
        }

        composeRule.onNodeWithContentDescription("Back").assertExists()
        composeRule.onNodeWithContentDescription("Settings").assertDoesNotExist()
    }
}
