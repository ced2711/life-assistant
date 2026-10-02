package com.ced2711.lifetracker.ui.components

import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.Space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog
import com.ced2711.lifetracker.ui.adaptive.HingeSafeDialog
import com.ced2711.lifetracker.ui.adaptive.hingeSafeDialogSurface
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate

/*
 * Android-only building blocks on top of LifeComponents: the full-height editor, confirmations,
 * and date and time pickers. All of them stay clear of a fold's hinge. See docs/DESIGN.md.
 */

/**
 * The phone's editor for one item: a full-height sheet with Close on the left, the title, and the
 * main action (Save) on the right; the fields scroll and stay above the keyboard. [footer] holds
 * rare actions such as Delete.
 */
@Composable
fun EditorSheet(
    title: String,
    onClose: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    working: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    HingeSafeDialog(onDismissRequest = { if (!working) onClose() }) {
        Surface(
            modifier = modifier.hingeSafeDialogSurface(maxWidth = 720.dp, widthFraction = 1f, heightFraction = 1f),
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            EditorFrame(title, onClose, actionLabel, onAction, Modifier.imePadding(), actionEnabled, working, snackbarHostState, footer, content)
        }
    }
}

/**
 * The same editor as [EditorSheet], but placed in the page instead of over it: the right pane of a
 * two-pane layout on tablets and unfolded foldables.
 */
@Composable
fun EditorPane(
    title: String,
    onClose: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    working: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        EditorFrame(title, onClose, actionLabel, onAction, Modifier, actionEnabled, working, snackbarHostState, footer, content)
    }
}

@Composable
private fun EditorFrame(
    title: String,
    onClose: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier,
    actionEnabled: Boolean,
    working: Boolean,
    snackbarHostState: SnackbarHostState?,
    footer: (@Composable RowScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, enabled = !working) { Icon(Icons.Rounded.Close, localizedText("Close")) }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = Space.xs).semantics { heading() },
                )
                Button(onClick = onAction, enabled = actionEnabled && !working, modifier = Modifier.padding(end = Space.sm)) {
                    if (working) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(actionLabel)
                    }
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                content = content,
            )
            if (footer != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(LifeTheme.colors.divider))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    content = footer,
                )
            }
        }
        if (snackbarHostState != null) {
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = if (footer != null) 64.dp else Space.sm))
        }
    }
}

/** A small label above a field in an editor. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * Asks before something that cannot be undone. [destructive] colours the confirm action red.
 * [title], [text] and the labels are already translated.
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    destructive: Boolean = false,
    dismissLabel: String = localizedText("Cancel"),
) {
    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) LifeTheme.colors.danger else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/** A search box with a clear button. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = localizedText("Search")) {
    LifeTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingIcon = Icons.Rounded.Search,
        trailing = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.Close, localizedText("Clear"), Modifier.size(18.dp)) } }
        } else {
            null
        },
        modifier = modifier,
    )
}

/** A calendar button that opens the system date picker; put it in a date field's trailing slot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerButton(initial: LocalDate?, onPicked: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(32.dp)) {
        Icon(Icons.Rounded.CalendarMonth, localizedText("Pick a date"), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: LocalDate.now()).toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            dismissButton = { TextButton(onClick = { open = false }) { Text(localizedText("Cancel")) } },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) }
                    open = false
                }) { Text(localizedText("OK")) }
            },
        ) { DatePicker(state = state) }
    }
}

/** A clock button that opens the time picker; [onPicked] gets minutes after midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerButton(initialMinute: Int?, is24Hour: Boolean, onPicked: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(32.dp)) {
        Icon(Icons.Rounded.Schedule, localizedText("Pick a time"), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        val start = initialMinute ?: (9 * 60)
        val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = is24Hour)
        HingeSafeAlertDialog(
            onDismissRequest = { open = false },
            text = { TimePicker(state) },
            dismissButton = { TextButton(onClick = { open = false }) { Text(localizedText("Cancel")) } },
            confirmButton = {
                TextButton(onClick = {
                    onPicked(state.hour * 60 + state.minute)
                    open = false
                }) { Text(localizedText("OK")) }
            },
        )
    }
}
