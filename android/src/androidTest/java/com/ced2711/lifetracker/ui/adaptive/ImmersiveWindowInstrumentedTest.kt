package com.ced2711.lifetracker.ui.adaptive

import android.app.DatePickerDialog
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.MainActivity
import com.ced2711.lifetracker.StartupRecoveryState
import com.ced2711.lifetracker.TaskLedgerApplication
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level checks for the window policy used by the main app and its separate dialog windows.
 *
 * These tests intentionally use the ordinary test activity and do not write app data. They are
 * kept separate from fold geometry tests because status-bar visibility is a platform window
 * concern and must also be checked after an Activity recreation.
 */
@RunWith(AndroidJUnit4::class)
class ImmersiveWindowInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private var platformPicker: DatePickerDialog? = null

    @After
    fun dismissPlatformPicker() {
        platformPicker?.let { picker ->
            composeRule.runOnUiThread {
                if (picker.isShowing) picker.dismiss()
            }
        }
    }

    @Test
    fun mainActivityHidesStatusBarWithoutHidingNavigationBar() {
        awaitMainReady()
        showNavigationBars(composeRule.activity.window)
        waitUntil {
            !isStatusBarVisible(composeRule.activity.window) &&
                isNavigationBarVisible(composeRule.activity.window)
        }

        assertFalse(isStatusBarVisible(composeRule.activity.window))
        assertTrue(isNavigationBarVisible(composeRule.activity.window))
        assertCutoutMode(composeRule.activity.window)
    }

    @Test
    fun mainActivityReappliesHiddenStatusBarAfterRecreation() {
        awaitMainReady()
        val originalActivity = composeRule.activity
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.activity !== originalActivity &&
                (composeRule.activity.application as TaskLedgerApplication)
                    .startupRecoveryCoordinator.state.value is StartupRecoveryState.Ready
        }

        waitUntil {
            !isStatusBarVisible(composeRule.activity.window) &&
                isNavigationBarVisible(composeRule.activity.window)
        }
        assertFalse(isStatusBarVisible(composeRule.activity.window))
        assertTrue(isNavigationBarVisible(composeRule.activity.window))
        assertCutoutMode(composeRule.activity.window)
    }

    @Test
    fun hingeSafeDialogHidesStatusBarAndKeepsInputAboveKeyboard() {
        awaitMainReady()
        val dialogWindow = AtomicReference<Window?>()
        val keyboardBottom = AtomicInteger()
        val editorBottom = AtomicInteger()
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                var value by remember { mutableStateOf("") }
                HingeSafeDialog(onDismissRequest = {}) {
                    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
                    val imeBottom = appImeInsets().getBottom(LocalDensity.current)
                    SideEffect {
                        dialogWindow.set(window)
                        keyboardBottom.set(imeBottom)
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.testTag(DIALOG_INPUT_TAG).onGloballyPositioned {
                            editorBottom.set(it.boundsInWindow().bottom.toInt())
                        },
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            dialogWindow.get()?.let { !isStatusBarVisible(it) } == true
        }
        val window = requireNotNull(dialogWindow.get())
        assertFalse(isStatusBarVisible(window))
        assertTrue(isNavigationBarVisible(window))

        composeRule.onNodeWithTag(DIALOG_INPUT_TAG).performClick()
        // Run device tests with the software keyboard enabled, including with a hardware keyboard.
        waitUntil { keyboardBottom.get() > 0 }
        composeRule.waitForIdle()
        assertTrue(
            "editor must remain above the software keyboard in fullscreen mode",
            editorBottom.get() <= onMain { window.decorView.height } - keyboardBottom.get() + 1,
        )
        composeRule.onNodeWithTag(DIALOG_INPUT_TAG).performTextInput(IME_TEXT)
        composeRule.onNodeWithTag(DIALOG_INPUT_TAG).assertTextEquals(IME_TEXT)
    }

    @Test
    fun hingeSafePlatformPickerHidesStatusBarWithoutChangingCutoutPolicy() {
        awaitMainReady()
        val pickerWindow = AtomicReference<Window?>()
        val initialPickerCutoutMode = AtomicReference<Int?>()
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                val context = LocalContext.current
                val launchPicker = rememberHingeSafePlatformDialogLauncher()
                SideEffect {
                    if (platformPicker == null) {
                        platformPicker = DatePickerDialog(context, null, 2030, 0, 15)
                        // show() applies theme window attributes; compare that actual policy,
                        // not the uninflated dialog's provisional LayoutParams.
                        platformPicker!!.show()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            initialPickerCutoutMode.set(
                                platformPicker!!.window?.attributes?.layoutInDisplayCutoutMode,
                            )
                        }
                        launchPicker(platformPicker!!)
                    }
                    pickerWindow.set(platformPicker?.window)
                }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize())
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            pickerWindow.get()?.let { window ->
                platformPicker?.isShowing == true && !isStatusBarVisible(window)
            } == true
        }
        val window = requireNotNull(pickerWindow.get())
        assertFalse(isStatusBarVisible(window))
        assertTrue(isNavigationBarVisible(window))
        // Platform pickers retain their ordinary layout bounds and are not forced into the cutout.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            assertEquals(initialPickerCutoutMode.get(), window.attributes.layoutInDisplayCutoutMode)
        }
    }

    private fun awaitMainReady() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            (composeRule.activity.application as TaskLedgerApplication)
                .startupRecoveryCoordinator.state.value is StartupRecoveryState.Ready
        }
        composeRule.waitForIdle()
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 10_000, condition = condition)
    }

    private fun isStatusBarVisible(window: Window): Boolean =
        onMain {
            WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets)
                .isVisible(WindowInsetsCompat.Type.statusBars())
        }

    private fun isNavigationBarNotRequestedHidden(window: Window): Boolean =
        onMain {
            val hiddenNavigationFlag = android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            window.decorView.systemUiVisibility and hiddenNavigationFlag == 0
        }

    private fun isNavigationBarVisible(window: Window): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            onMain {
                WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets)
                    .isVisible(WindowInsetsCompat.Type.navigationBars())
            }
        } else {
            isNavigationBarNotRequestedHidden(window)
        }

    private fun showNavigationBars(window: Window) {
        composeRule.runOnUiThread {
            WindowInsetsControllerCompat(window, window.decorView)
                .show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    private fun assertCutoutMode(window: Window) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return
        val expected = when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R ->
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P ->
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            else -> error("cutout mode is unavailable before API 28")
        }
        assertEquals(expected, onMain { window.attributes.layoutInDisplayCutoutMode })
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        composeRule.runOnUiThread { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private companion object {
        const val DIALOG_INPUT_TAG = "immersive-dialog-input"
        const val IME_TEXT = "foldable input"
    }
}
