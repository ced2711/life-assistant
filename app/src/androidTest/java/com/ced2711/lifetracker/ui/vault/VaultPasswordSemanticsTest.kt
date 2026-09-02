package com.ced2711.lifetracker.ui.vault

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultPasswordSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun visiblePasswordKeepsPasswordSemanticsWithoutExposingPlaintext() {
        val secret = "Pässword-🔐-never-expose"
        val visible = mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                VaultPasswordField(
                    password = secret,
                    passwordVisible = visible.value,
                    enabled = true,
                    onPasswordChange = {},
                    onPasswordVisibilityChange = { visible.value = it },
                    onCopyPassword = {},
                )
            }
        }

        val passwordSemantics = SemanticsMatcher.expectValue(SemanticsProperties.Password, Unit)
        composeRule
            .onNode(
                hasContentDescription(VAULT_PASSWORD_VISIBLE_ACCESSIBILITY_DESCRIPTION),
                useUnmergedTree = true,
            )
            .assertExists()
            .assert(passwordSemantics)
        composeRule
            .onAllNodes(hasText(secret, substring = true), useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule
            .onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .assertCountEquals(0)

        composeRule.onNodeWithText("Hide").assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertTrue(
            composeRule.onAllNodes(passwordSemantics, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        val maskedEditableText = SemanticsMatcher("editable password text is masked") { node ->
            node.config.getOrNull(SemanticsProperties.EditableText)?.text?.let { value ->
                value.isNotEmpty() && value != secret && value.all { it == '•' }
            } == true
        }
        assertTrue(
            composeRule.onAllNodes(maskedEditableText, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
