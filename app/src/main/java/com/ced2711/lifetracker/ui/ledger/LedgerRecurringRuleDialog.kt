package com.ced2711.lifetracker.ui.ledger

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.domain.date.SmartDateParser
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.ui.adaptive.HingeSafeDialog
import com.ced2711.lifetracker.ui.adaptive.hingeSafeDialogSurface
import com.ced2711.lifetracker.ui.adaptive.rememberHingeSafePlatformDialogLauncher
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun LedgerRecurringRuleDialog(
    series: LedgerSeriesEntity,
    formatting: LedgerDisplayFormatting,
    isSaving: Boolean,
    failureMessage: String?,
    onDismiss: () -> Unit,
    onSave: (effectiveEpochDay: Long, draft: LedgerDraft) -> Unit,
) {
    val context = LocalContext.current
    val platformDialogLauncher = rememberHingeSafePlatformDialogLauncher()
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    var typeName by rememberSaveable(series.id) { mutableStateOf(series.type.name) }
    val type = LedgerType.entries.firstOrNull { it.name == typeName } ?: series.type
    var amount by rememberSaveable(series.id) {
        mutableStateOf(formatMoney(series.amountCents).removePrefix("$").replace(",", ""))
    }
    var effectiveDateInput by rememberSaveable(series.id) {
        mutableStateOf(tomorrow.format(LEDGER_SHORTCUT_DATE_FORMATTER))
    }
    val effectiveDate = SmartDateParser.parse(effectiveDateInput, today)
    var merchant by rememberSaveable(series.id) { mutableStateOf(series.merchant) }
    var note by rememberSaveable(series.id) { mutableStateOf(series.note) }
    var tags by rememberSaveable(series.id) {
        mutableStateOf(series.tagsCsv.split(',').map(String::trim).filter(String::isNotEmpty).joinToString(", "))
    }
    var unitName by rememberSaveable(series.id) { mutableStateOf(series.recurrenceUnit.name) }
    val unit = RecurrenceUnit.entries.firstOrNull { it.name == unitName } ?: series.recurrenceUnit
    var interval by rememberSaveable(series.id) { mutableStateOf(series.intervalCount.toString()) }
    var hasEndDate by rememberSaveable(series.id) { mutableStateOf(series.endEpochDay != null) }
    var endDateInput by rememberSaveable(series.id) {
        mutableStateOf(
            LocalDate.ofEpochDay(
                series.endEpochDay?.coerceAtLeast(tomorrow.toEpochDay())
                    ?: tomorrow.plusMonths(1).toEpochDay(),
            ).format(LEDGER_SHORTCUT_DATE_FORMATTER),
        )
    }
    val endDate = SmartDateParser.parse(endDateInput, today)
    val amountCents = parseAmountCents(amount)
    val intervalValue = interval.toIntOrNull()
    val valid = amountCents != null && effectiveDate != null && !effectiveDate.isBefore(tomorrow) &&
        intervalValue != null &&
        intervalValue > 0 &&
        (!hasEndDate || (endDate != null && !endDate.isBefore(effectiveDate)))

    fun pickDate(current: LocalDate, onSelected: (LocalDate) -> Unit) {
        platformDialogLauncher(
            DatePickerDialog(
                context,
                { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
                current.year,
                current.monthValue - 1,
                current.dayOfMonth,
            ),
        )
    }

    HingeSafeDialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            modifier = Modifier.hingeSafeDialogSurface(
                maxWidth = 760.dp,
                widthFraction = 1f,
                heightFraction = 1f,
            ),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                    Text(
                        text = "Edit recurring rule",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = valid && !isSaving,
                        onClick = {
                            val selectedAmount = amountCents ?: return@Button
                            val selectedDate = effectiveDate ?: return@Button
                            val selectedInterval = intervalValue ?: return@Button
                            onSave(
                                selectedDate.toEpochDay(),
                                LedgerDraft(
                                    type = type,
                                    amountCents = selectedAmount,
                                    epochDay = selectedDate.toEpochDay(),
                                    minuteOfDay = 0,
                                    merchant = merchant,
                                    note = note,
                                    tags = tags.split(',').map(String::trim).filter(String::isNotEmpty),
                                    recurrence = RecurrenceRule(
                                        unit = unit,
                                        interval = selectedInterval,
                                        endEpochDay = if (hasEndDate) endDate?.toEpochDay() else null,
                                    ),
                                ),
                            )
                        },
                    ) { Text(if (isSaving) "Saving…" else "Save") }
                }
                failureMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "The old schedule and every existing entry are preserved. The replacement starts tomorrow or later.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LedgerTypeChooser(type = type, onTypeChanged = { typeName = it.name })
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { candidate -> sanitizeAmountInput(candidate)?.let { amount = it } },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Amount") },
                                prefix = { Text("$") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = amount.isNotBlank() && amountCents == null,
                                supportingText = { Text("Up to $999,999,999.99 · max 2 decimal places") },
                            )
                            OutlinedTextField(
                                value = effectiveDateInput,
                                onValueChange = { effectiveDateInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Apply changes from") },
                                placeholder = { Text("15, 8/15, or 8/15/2026") },
                                singleLine = true,
                                isError = effectiveDate == null || effectiveDate.isBefore(tomorrow),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            pickDate(effectiveDate?.coerceAtLeast(tomorrow) ?: tomorrow) { selected ->
                                                effectiveDateInput = selected.format(LEDGER_SHORTCUT_DATE_FORMATTER)
                                            }
                                        },
                                    ) { Icon(Icons.Outlined.Event, contentDescription = "Choose start date") }
                                },
                                supportingText = {
                                    Text(
                                        when {
                                            effectiveDate == null ->
                                                "Enter a valid day, month/day, or month/day/year"
                                            effectiveDate.isBefore(tomorrow) ->
                                                "Replacement schedules must start tomorrow or later"
                                            else -> "Selected: ${formatting.date(effectiveDate.toEpochDay())}"
                                        },
                                    )
                                },
                            )
                            Text("Repeat interval", style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RecurrenceUnit.entries.forEach { option ->
                                    val selected = option == unit
                                    Surface(
                                        modifier = Modifier.selectable(
                                            selected = selected,
                                            onClick = { unitName = option.name },
                                            role = Role.RadioButton,
                                        ),
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(selected = selected, onClick = null)
                                            Text(option.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) })
                                        }
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = interval,
                                onValueChange = { candidate ->
                                    sanitizeRecurrenceIntervalInput(candidate)?.let { interval = it }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Every N ${unit.name.lowercase()}(s)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = recurrenceIntervalError(interval) != null,
                                supportingText = recurrenceIntervalError(interval)?.let { message ->
                                    { Text(message) }
                                },
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = hasEndDate, onCheckedChange = { hasEndDate = it })
                                Text("End date")
                            }
                            if (hasEndDate) {
                                OutlinedTextField(
                                    value = endDateInput,
                                    onValueChange = { endDateInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("End date") },
                                    placeholder = { Text("15, 8/15, or 8/15/2026") },
                                    singleLine = true,
                                    isError = endDate == null ||
                                        (effectiveDate != null && endDate.isBefore(effectiveDate)),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                pickDate(endDate ?: effectiveDate ?: tomorrow) { selected ->
                                                    endDateInput = selected.format(
                                                        LEDGER_SHORTCUT_DATE_FORMATTER,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                Icons.Outlined.Event,
                                                contentDescription = "Choose end date",
                                            )
                                        }
                                    },
                                    supportingText = {
                                        Text(
                                            when {
                                                endDate == null ->
                                                    "Enter a valid day, month/day, or month/day/year"
                                                effectiveDate != null && endDate.isBefore(effectiveDate) ->
                                                    "End date cannot be before the effective date"
                                                else -> "Selected: ${formatting.date(endDate.toEpochDay())}"
                                            },
                                        )
                                    },
                                )
                            }
                            OutlinedTextField(
                                value = merchant,
                                onValueChange = { merchant = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Merchant") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = tags,
                                onValueChange = { tags = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Tags") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Note") },
                                minLines = 2,
                                maxLines = 5,
                            )
                            Text(
                                "Generated entries use 12:00 AM (00:00).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
