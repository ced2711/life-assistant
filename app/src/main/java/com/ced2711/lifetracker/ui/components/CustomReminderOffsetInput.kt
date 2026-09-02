package com.ced2711.lifetracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal const val MAX_REMINDER_OFFSET_MINUTES = 366L * 24L * 60L

internal enum class ReminderOffsetUnit(
    val label: String,
    val minutes: Long,
) {
    HOURS("Hours", 60L),
    DAYS("Days", 24L * 60L),
    WEEKS("Weeks", 7L * 24L * 60L),
}

/** Compact, optional editor for a positive reminder offset up to 366 days. */
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
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = modifier,
        ) {
            Text("+ Custom reminder")
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = valueText,
            onValueChange = { candidate -> valueText = candidate.filter(Char::isDigit).take(6) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Reminder value") },
            supportingText = {
                val maxValue = MAX_REMINDER_OFFSET_MINUTES / unit.minutes
                Text(
                    when {
                        isDuplicate -> "This reminder is already selected."
                        isInvalid -> "Enter a whole number from 1 to $maxValue ${unit.label.lowercase()}."
                        else -> "Maximum: $maxValue ${unit.label.lowercase()} before the deadline."
                    },
                )
            },
            isError = isInvalid || isDuplicate,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReminderOffsetUnit.entries.forEach { option ->
                FilterChip(
                    selected = option == unit,
                    onClick = { unitName = option.name },
                    label = { Text(option.label) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    expanded = false
                    valueText = ""
                },
            ) { Text("Cancel") }
            Button(
                enabled = enabled && offset != null && !isDuplicate,
                onClick = {
                    offset?.let(onAdd)
                    valueText = ""
                    expanded = false
                },
            ) { Text("Add") }
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
