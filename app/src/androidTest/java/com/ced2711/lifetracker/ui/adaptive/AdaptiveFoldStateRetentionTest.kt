package com.ced2711.lifetracker.ui.adaptive

import android.graphics.Rect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.window.layout.FoldingFeature
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import org.junit.Rule
import org.junit.Test

class AdaptiveFoldStateRetentionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun draftSurvivesSwitchFromSingleWindowToHalfOpenedVerticalFold() {
        lateinit var foldingFeature: MutableState<FoldingFeature?>
        composeRule.setContent {
            foldingFeature = remember { mutableStateOf(null) }
            MaterialTheme {
                AdaptiveTaskLedgerScaffold(
                    selected = TopLevelDestination.TODO,
                    onSelected = {},
                    onSettings = {},
                    isSettings = false,
                    modifier = Modifier.fillMaxSize(),
                    foldingFeature = foldingFeature.value,
                ) {
                    var description by rememberSaveable { mutableStateOf("") }
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description *") },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Description *").performTextInput(DRAFT_TEXT)
        composeRule.runOnIdle {
            foldingFeature.value = HalfOpenedVerticalFold
        }

        composeRule.onNodeWithText("Description *").assertExists()
        composeRule.onNode(hasSetTextAction() and hasText(DRAFT_TEXT)).assertExists()
    }

    private companion object {
        const val DRAFT_TEXT = "fold transition draft"
    }
}

private object HalfOpenedVerticalFold : FoldingFeature {
    override val bounds = Rect(200, 0, 200, 10_000)
    override val state = FoldingFeature.State.HALF_OPENED
    override val orientation = FoldingFeature.Orientation.VERTICAL
    override val occlusionType = FoldingFeature.OcclusionType.NONE
    override val isSeparating = true
}
