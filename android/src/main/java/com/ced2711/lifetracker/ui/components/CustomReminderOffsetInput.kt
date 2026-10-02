package com.ced2711.lifetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.Pill
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.theme.LifeTheme

internal const val MAX_REMINDER_OFFSET_MINUTES = 366L * 24L * 60L

internal enum class ReminderOffsetUnit(
    val label: String,
    val minutes: Long,
) {
    HOURS("Hours", 60L),
    DAYS("Days", 24L * 60L),
    WEEKS("Weeks", 7L * 24L * 60L),
}

/**
 * Adds a reminder of one's own, up to 366 days before the deadline. It starts as a pill next to
 * the other reminders and opens into a number, a unit and Add.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomReminderOffsetInput(
    existingOffsets: Collection<Long>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onAdd: (Long) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var valueText by rememberSaveable { mutableStateOf("") }
    var unitName by rememberSaveable { mutableStateOf(ReminderOffsetUnit.HOURS.name) }
    val unit = ReminderOffsetUnit.entries.firstOrNull { it.name == unitName }
        ?: ReminderOffsetUnit.HOURS
    val offset = customReminderOffsetMinutes(valueText, unit)
    val isDuplicate = offset != null && offset in existingOffsets
    val isInvalid = valueText.isNotBlank() && offset == null

    if (!expanded) {
        Pill(
            text = localizedText("Custom reminder"),
            selected = false,
            enabled = enabled,
            onClick = { expanded = true },
            leading = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) },
            modifier = modifier,
        )
        return
    }

    fun add() {
        if (!enabled || offset == null || isDuplicate) return
        onAdd(offset)
        valueText = ""
        expanded = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            LifeTextField(
                value = valueText,
                onValueChange = { candidate -> valueText = candidate.filter(Char::isDigit).take(6) },
                placeholder = localizedText("Reminder value"),
                isError = isInvalid || isDuplicate,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                background = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.width(140.dp),
            )
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                ReminderOffsetUnit.entries.forEach { option ->
                    Pill(
                        text = localizedText(option.label),
                        selected = option == unit,
                        exclusive = true,
                        onClick = { unitName = option.name },
                    )
                }
            }
        }
        val maxValue = MAX_REMINDER_OFFSET_MINUTES / unit.minutes
        Text(
            localizedText(when {
                isDuplicate -> "This reminder is already selected."
                isInvalid -> "Enter a whole number from 1 to $maxValue ${unit.label.lowercase()}."
                else -> "Maximum: $maxValue ${unit.label.lowercase()} before the deadline."
            }),
            style = MaterialTheme.typography.bodySmall,
            color = if (isInvalid || isDuplicate) LifeTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    expanded = false
                    valueText = ""
                },
            ) { Text(localizedText("Cancel")) }
            Button(enabled = enabled && offset != null && !isDuplicate, onClick = ::add) { Text(localizedText("Add")) }
        }
    }
}

internal fun customReminderOffsetMinutes(
    valueText: String,
    unit: ReminderOffsetUnit,
): Long? {
    val value = valueText.toLongOrNull() ?: return null
    if (value <= 0L || value > MAX_REMINDER_OFFSET_MINUTES / unit.minutes) return null
    return value * unit.minutes
}

internal fun formatReminderOffset(minutes: Long): String = when {
    minutes == 0L -> "At due time"
    minutes % ReminderOffsetUnit.WEEKS.minutes == 0L ->
        reminderUnitLabel(minutes / ReminderOffsetUnit.WEEKS.minutes, "week")
    minutes % ReminderOffsetUnit.DAYS.minutes == 0L ->
        reminderUnitLabel(minutes / ReminderOffsetUnit.DAYS.minutes, "day")
    minutes % ReminderOffsetUnit.HOURS.minutes == 0L ->
        reminderUnitLabel(minutes / ReminderOffsetUnit.HOURS.minutes, "hour")
    else -> reminderUnitLabel(minutes, "minute")
}

private fun reminderUnitLabel(value: Long, unit: String): String =
    "$value $unit${if (value == 1L) "" else "s"} before"
