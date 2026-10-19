package com.ced2711.lifetracker.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.parseTags
import com.ced2711.lifetracker.ui.components.DatePickerButton
import com.ced2711.lifetracker.ui.components.FieldLabel
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.Pill
import com.ced2711.lifetracker.ui.design.Segmented
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import java.time.LocalDate

/** An open schedule editor: which schedule and how its last save went. */
internal data class LedgerRuleEditorUi(
    val seriesId: Long,
    val isSaving: Boolean = false,
    val failureMessage: String? = null,
)

/**
 * Changes a schedule from a day on. The old schedule and its entries stay; the replacement
 * starts tomorrow or later, and the entries it creates use 12:00 AM.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LedgerRuleEditor(
    series: LedgerSeriesEntity,
    inPane: Boolean,
    formatting: LedgerDisplayFormatting,
    knownTags: List<String>,
    isSaving: Boolean,
    failureMessage: String?,
    onDismiss: () -> Unit,
    onSave: (effectiveEpochDay: Long, draft: LedgerDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalUiLanguage.current
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    var typeName by rememberSaveable(series.id) { mutableStateOf(series.type.name) }
    val type = LedgerType.entries.firstOrNull { it.name == typeName } ?: series.type
    var amount by rememberSaveable(series.id) { mutableStateOf(amountInputText(series.amountCents)) }
    var effectiveDateInput by rememberSaveable(series.id) { mutableStateOf(formatting.dateInput(tomorrow.toEpochDay())) }
    val effectiveDate = formatting.parseDate(effectiveDateInput, today)
    var merchant by rememberSaveable(series.id) { mutableStateOf(series.merchant) }
    var note by rememberSaveable(series.id) { mutableStateOf(series.note) }
    var tags by rememberSaveable(series.id) { mutableStateOf(parseTags(series.tagsCsv).joinToString(", ")) }
    var unitName by rememberSaveable(series.id) { mutableStateOf(series.recurrenceUnit.name) }
    val unit = RecurrenceUnit.entries.firstOrNull { it.name == unitName } ?: series.recurrenceUnit
    var interval by rememberSaveable(series.id) { mutableStateOf(series.intervalCount.toString()) }
    var endInput by rememberSaveable(series.id) {
        mutableStateOf(series.endEpochDay?.coerceAtLeast(tomorrow.toEpochDay())?.let(formatting::dateInput).orEmpty())
    }
    val endDate = endInput.takeIf(String::isNotBlank)?.let { formatting.parseDate(it, today) }
    val amountCents = parseAmountCents(amount)
    val intervalValue = interval.toIntOrNull()
    val startsTooEarly = effectiveDate != null && effectiveDate.isBefore(tomorrow)
    val endInvalid = endInput.isNotBlank() && endDate == null
    val endBeforeStart = endDate != null && effectiveDate != null && endDate.isBefore(effectiveDate)
    val valid = amountCents != null && effectiveDate != null && !startsTooEarly &&
        intervalValue != null && intervalValue > 0 && !endInvalid && !endBeforeStart

    fun save() {
        val selectedAmount = amountCents ?: return
        val selectedDate = effectiveDate ?: return
        val selectedInterval = intervalValue ?: return
        if (!valid || isSaving) return
        onSave(
            selectedDate.toEpochDay(),
            LedgerDraft(
                type = type,
                amountCents = selectedAmount,
                epochDay = selectedDate.toEpochDay(),
                minuteOfDay = 0,
                merchant = merchant,
                note = note,
                tags = parseTags(tags),
                recurrence = RecurrenceRule(unit = unit, interval = selectedInterval, endEpochDay = endDate?.toEpochDay()),
            ),
        )
    }

    LedgerEditorFrame(
        inPane = inPane,
        title = localizedText("Edit recurring rule"),
        onClose = onDismiss,
        onSave = ::save,
        saveEnabled = valid,
        working = isSaving,
        modifier = modifier,
    ) {
        failureMessage?.let { ErrorLine(localizedText(it)) }
        QuietNote(localizedText("The old schedule and every existing entry are preserved. The replacement starts tomorrow or later."))
        Segmented(
            listOf(LedgerType.EXPENSE, LedgerType.INCOME), type, { typeName = it.name },
            { localizedText(it.displayName()) },
            fill = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AmountField(amount, { amount = it }, amountCents, required = false)

        FieldLabel(localizedText("Apply changes from"))
        LifeTextField(
            effectiveDateInput, { effectiveDateInput = it },
            placeholder = localizedText("Date"),
            isError = effectiveDate == null || startsTooEarly,
            trailing = {
                DatePickerButton(effectiveDate?.coerceAtLeast(tomorrow) ?: tomorrow, { effectiveDateInput = formatting.dateInput(it.toEpochDay()) })
            },
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            effectiveDate == null -> ErrorLine(localizedText("Enter a valid day, month/day, or month/day/year"))
            startsTooEarly -> ErrorLine(localizedText("Replacement schedules must start tomorrow or later"))
            else -> Text(
                ledgerDayHeading(effectiveDate.toEpochDay(), today, formatting, language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        FieldLabel(localizedText("Repeat"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RecurrenceUnit.entries.forEach { option ->
                Pill(localizedText(ledgerRepeatLabel(option)), option == unit, { unitName = option.name }, exclusive = true)
            }
        }
        RepeatDetails(
            unit = unit,
            interval = interval,
            onInterval = { interval = it },
            endInput = endInput,
            onEndInput = { endInput = it },
            endDate = endDate,
            endPickerStart = (effectiveDate ?: tomorrow).plusMonths(1),
            endError = when {
                endInvalid -> "Enter a valid day, month/day, or month/day/year"
                endBeforeStart -> "End date cannot be before the effective date"
                else -> null
            },
            formatting = formatting,
            enabled = true,
        )
        QuietNote(localizedText("Generated entries use 12:00 AM (00:00)."))

        FieldLabel(localizedText("Merchant"))
        LifeTextField(merchant, { merchant = it }, placeholder = localizedText("Where or who"), modifier = Modifier.fillMaxWidth())

        FieldLabel(localizedText("Tags"))
        TagsField(tags, { tags = it }, knownTags)

        FieldLabel(localizedText("Note"))
        LifeTextField(note, { note = it }, placeholder = localizedText("Optional"), minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.lg))
    }
}
