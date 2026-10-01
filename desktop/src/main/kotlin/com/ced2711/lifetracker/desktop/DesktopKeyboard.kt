package com.ced2711.lifetracker.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Window-wide keyboard shortcuts. The window forwards every key press to [handle]; the current
 * page registers what "new" and "find" mean for it with [RegisterPageShortcuts].
 *
 * - Ctrl+N new item, Ctrl+F search, Ctrl+1…9 switch module, Ctrl+R sync, Ctrl+, settings,
 *   Ctrl+B fold or unfold the sidebar.
 */
class DesktopShortcuts {
    var onNew: (() -> Unit)? = null
    var onFind: (() -> Unit)? = null
    var onNavigate: ((Int) -> Unit)? = null
    var onSync: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onToggleSidebar: (() -> Unit)? = null

    fun handle(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val command = event.isCtrlPressed || event.isMetaPressed
        if (!command || event.isAltPressed) return false
        return when (event.key) {
            Key.N -> onNew?.invoke() != null
            Key.F -> onFind?.invoke() != null
            Key.R -> onSync?.invoke() != null
            Key.Comma -> onSettings?.invoke() != null
            Key.B -> onToggleSidebar?.invoke() != null
            Key.One, Key.NumPad1 -> navigate(0)
            Key.Two, Key.NumPad2 -> navigate(1)
            Key.Three, Key.NumPad3 -> navigate(2)
            Key.Four, Key.NumPad4 -> navigate(3)
            Key.Five, Key.NumPad5 -> navigate(4)
            Key.Six, Key.NumPad6 -> navigate(5)
            Key.Seven, Key.NumPad7 -> navigate(6)
            Key.Eight, Key.NumPad8 -> navigate(7)
            Key.Nine, Key.NumPad9 -> navigate(8)
            else -> false
        }
    }

    private fun navigate(index: Int): Boolean = onNavigate?.invoke(index) != null
}

val LocalDesktopShortcuts = staticCompositionLocalOf { DesktopShortcuts() }

/** Lets the visible page answer Ctrl+N and Ctrl+F while it is shown. */
@Composable
fun RegisterPageShortcuts(onNew: (() -> Unit)? = null, onFind: (() -> Unit)? = null) {
    val shortcuts = LocalDesktopShortcuts.current
    val currentNew = rememberUpdatedState(onNew)
    val currentFind = rememberUpdatedState(onFind)
    DisposableEffect(shortcuts) {
        val newAction: () -> Unit = { currentNew.value?.invoke() }
        val findAction: () -> Unit = { currentFind.value?.invoke() }
        if (onNew != null) shortcuts.onNew = newAction
        if (onFind != null) shortcuts.onFind = findAction
        onDispose {
            if (shortcuts.onNew === newAction) shortcuts.onNew = null
            if (shortcuts.onFind === findAction) shortcuts.onFind = null
        }
    }
}

/** Enter (without Shift) runs [action]; Shift+Enter still types a new line in multi-line fields. */
fun Modifier.onEnter(action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
        !event.isShiftPressed && !event.isCtrlPressed && !event.isAltPressed
    ) {
        action()
        true
    } else {
        false
    }
}

/** Editor keys: Ctrl+S or Ctrl+Enter saves, Esc cancels. */
fun Modifier.editorKeys(onSave: () -> Unit, onCancel: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val command = event.isCtrlPressed || event.isMetaPressed
    when {
        command && (event.key == Key.S || event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
            onSave()
            true
        }
        event.key == Key.Escape -> {
            onCancel()
            true
        }
        else -> false
    }
}

/** List keys: arrows move, Enter opens, Space toggles, Delete removes. */
fun Modifier.listKeys(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onOpen: () -> Unit,
    onToggle: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionUp -> { onUp(); true }
        Key.DirectionDown -> { onDown(); true }
        Key.Enter, Key.NumPadEnter -> { onOpen(); true }
        Key.Spacebar -> onToggle?.let { it(); true } ?: false
        Key.Delete -> onDelete?.let { it(); true } ?: false
        else -> false
    }
}
