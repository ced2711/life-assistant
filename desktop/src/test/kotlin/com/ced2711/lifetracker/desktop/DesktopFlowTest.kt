package com.ced2711.lifetracker.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.io.File
import java.nio.file.Files
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uses the whole desktop app the way a person does, without showing a window: starts it on an
 * empty folder, clicks, types and checks what is on screen and what was stored. With
 * -Dflow.shots=<folder> every step is also saved as a picture for review.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class DesktopFlowTest {
    private val root: File = Files.createTempDirectory("life-assistant-flow").toFile()
    private val shots: File? = System.getProperty("flow.shots")?.let(::File)?.apply { mkdirs() }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun app(width: Int = 1280, height: Int = 800, block: DesktopComposeUiTest.() -> Unit) = runDesktopComposeUiTest(width, height) {
        val shortcuts = DesktopShortcuts()
        setContent {
            CompositionLocalProvider(LocalDesktopShortcuts provides shortcuts) {
                LifeTrackerDesktopApp(appDirectory = root, protection = PlainProtection)
            }
        }
        // The data opens by itself; no password screen on a new PC.
        waitUntil(timeoutMillis = 20_000) { onAllNodes(hasContentDescription("Todo", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        block()
    }

    private fun DesktopComposeUiTest.shot(name: String) {
        val directory = shots ?: return
        waitForIdle()
        val data = Image.makeFromBitmap(captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG) ?: return
        File(directory, "$name.png").writeBytes(data.bytes)
    }

    private fun DesktopComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun DesktopComposeUiTest.open(module: String) {
        onAllNodes(hasContentDescription(module, substring = true)).onFirst().performClick()
        waitForIdle()
    }

    private fun store(): DesktopDataStore = DesktopDataStore(root)

    @Test
    fun aNewPcOpensOnTodayWithoutAPasswordAndTodosCanBeAddedAndTicked() = app {
        assertFalse("no password is asked on a new PC", onAllNodesWithText("Data password").fetchSemanticsNodes().isNotEmpty())
        waitForText("Add a todo for today, then press Enter")
        shot("01-today-empty")
        val field = onNodeWithText("Add a todo for today, then press Enter")
        field.performClick()
        field.performTextInput("Buy milk")
        onNode(hasText("Buy milk")).performKeyInput { pressKey(Key.Enter) }
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasContentDescription("Buy milk")).fetchSemanticsNodes().isNotEmpty() }
        shot("02-today-one-todo")
        onNode(hasContentDescription("Buy milk")).performClick()
        waitForText("Done today")
        shot("03-today-done")
    }

    @Test
    fun dailyChecklistItemsAreAddedOnTodayAndTickedForToday() = app {
        waitForText("Add to the checklist, then press Enter")
        val field = onNodeWithText("Add to the checklist, then press Enter")
        field.performClick()
        field.performTextInput("Brush teeth")
        onNode(hasText("Brush teeth")).performKeyInput { pressKey(Key.Enter) }
        waitForText("Finish")
        field.performTextInput("Shower")
        onNode(hasText("Shower")).performKeyInput { pressKey(Key.Enter) }
        waitForText("0 / 2")
        shot("03a-checklist-edit")
        onNodeWithText("Finish").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasContentDescription("Brush teeth")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("checklist items are not todos", onAllNodesWithText("Nothing due today", substring = true).fetchSemanticsNodes().isNotEmpty())
        onNode(hasContentDescription("Brush teeth")).performClick()
        waitForText("1 / 2")
        shot("03b-checklist-ticked")
        onNode(hasContentDescription("Shower")).performClick()
        waitForText("All done for today. It starts fresh tomorrow.")
        shot("03c-checklist-all-done")
        onNodeWithText("Show").performClick()
        onNode(hasContentDescription("Shower")).performClick()
        waitForText("1 / 2")
    }

    @Test
    fun aTodoWithDetailsIsSavedFromTheEditor() = app {
        open("Todo")
        onNodeWithText("New todo").performClick()
        waitForText("What needs to be done?")
        val description = onNodeWithText("What needs to be done?")
        description.performClick()
        description.performTextInput("Renew passport")
        onNodeWithText("Tomorrow").performClick()
        onNodeWithText("Urgent").performClick()
        shot("04-todo-editor")
        onNodeWithText("Save (Ctrl+S)").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasContentDescription("Renew passport")).fetchSemanticsNodes().isNotEmpty() }
        waitForText("Tomorrow")
        shot("05-todo-list")
    }

    @Test
    fun aLedgerEntryIsAddedFromTheQuickRow() = app {
        open("Ledger")
        val amount = onNode(hasSetTextAction() and hasText("0.00", substring = true))
        amount.performClick()
        amount.performTextInput("12.50")
        val what = onNodeWithText("Where or what, then Enter")
        what.performClick()
        what.performTextInput("Cafe")
        onNode(hasText("Cafe")).performKeyInput { pressKey(Key.Enter) }
        waitForText("$12.50")
        shot("06-ledger")
        onNodeWithText("Statistics").performClick()
        waitForText("Trend")
        shot("07-ledger-statistics")
        onNodeWithText("Recurring").performClick()
        waitForText("No recurring entries")
    }

    @Test
    fun aNoteSavesItselfWhileTyping() = app {
        open("Notes")
        onNodeWithText("New note").performClick()
        val title = onNodeWithText("Title")
        title.performClick()
        title.performTextInput("Ideas")
        waitForText("Saved")
        shot("08-note")
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Ideas").fetchSemanticsNodes().size >= 2 }
    }

    @Test
    fun theVaultAsksForAPasswordToBeChosenFirst() = app {
        open("Vault")
        waitForText("Choose a password")
        shot("09-vault-no-password")
        onNodeWithText("Choose a password").performClick()
        val first = onNodeWithText("New password (at least 8 characters)")
        first.performClick()
        first.performTextInput("correct horse")
        val second = onNodeWithText("Repeat the password")
        second.performClick()
        second.performTextInput("correct horse")
        shot("10-choose-password")
        onNodeWithText("Set password").performClick()
        waitForText("No Vault entries yet")
        shot("11-vault-open")
    }

    @Test
    fun everyPageShowsInASmallWindow() = app(width = 640, height = 480) {
        listOf("Today", "Todo", "Ledger", "Calendar", "Notes", "Vault", "Settings").forEach { module ->
            open(module)
            shot("small-$module")
        }
    }

    @Test
    fun settingsTopicsOpen() = app {
        open("Settings")
        listOf("Appearance" to "Accent color", "Reminders" to "Remind me", "Security" to "No password yet", "AI assistants" to "Allow changes", "About" to "Private and offline", "Sync & backup" to "Daily backups").forEach { (topic, expected) ->
            onAllNodesWithText(topic).onFirst().performClick()
            waitForText(expected)
            shot("settings-$topic")
        }
    }

    /** Stands in for DPAPI or the Linux keyring. */
    private object PlainProtection : DeviceProtection {
        override fun protect(bytes: ByteArray) = ByteArray(bytes.size) { (bytes[it].toInt() xor 0x5A).toByte() }
        override fun unprotect(bytes: ByteArray) = protect(bytes)
    }
}
