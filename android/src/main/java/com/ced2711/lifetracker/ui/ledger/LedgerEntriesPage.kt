package com.ced2711.lifetracker.ui.ledger

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.MoneyTotals
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.parseTags
import com.ced2711.lifetracker.ui.components.DatePickerButton
import com.ced2711.lifetracker.ui.components.SearchField
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.MoneyText
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.RowDivider
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Segmented
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.design.Stat
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import java.time.LocalDateTime

/** What the quick-add row needs to know about its last save. */
internal data class LedgerQuickAddUi(
    val isSaving: Boolean = false,
    val saveSucceeded: Boolean = false,
    val failureMessage: String? = null,
)

/** Week, Month, Year… as a segmented control, with a jump back to the current period. */
@Composable
internal fun LedgerPeriodSwitch(
    period: LedgerPeriod,
    periods: List<LedgerPeriod>,
    onPeriod: (LedgerPeriod) -> Unit,
    showToday: Boolean,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        // Five periods do not fit a small phone, so the control scrolls sideways when it has to.
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Segmented(periods, period, onPeriod, { localizedText(it.label) })
        }
        if (showToday) {
            TextButton(onClick = onToday, modifier = Modifier.padding(start = Space.xs)) { Text(localizedText("Today")) }
        }
    }
}

/**
 * The period at one glance: its name with previous and next, then income, expense and what is
 * left of them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LedgerTotalsPanel(
    title: String,
    canStep: Boolean,
    onShift: (Long) -> Unit,
    totals: MoneyTotals,
    modifier: Modifier = Modifier,
) {
    Panel(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.xs).padding(top = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canStep) {
                IconButton(onClick = { onShift(-1) }) { Icon(Icons.Rounded.ChevronLeft, localizedText("Previous")) }
            } else {
                Spacer(Modifier.size(width = Space.md, height = 48.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = if (canStep) TextAlign.Center else TextAlign.Start,
                maxLines = 2,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (canStep) {
                IconButton(onClick = { onShift(1) }) { Icon(Icons.Rounded.ChevronRight, localizedText("Next")) }
            }
        }
        val income = formatMoney(totals.incomeCents)
        val expense = formatMoney(totals.expenseCents)
        val valueStyle = MaterialTheme.typography.headlineSmall
        BoxWithConstraints(Modifier.padding(start = Space.lg, end = Space.lg, top = Space.xs, bottom = Space.md)) {
            // Side by side while both amounts fit in full; very large amounts and large fonts stack instead.
            val needed = 12.5.dp * maxOf(income.length, expense.length) * LocalDensity.current.fontScale
            val incomeStat: @Composable (Modifier) -> Unit = {
                Stat(localizedText("Income"), income, it, valueColor = LifeTheme.colors.income, valueStyle = valueStyle)
            }
            val expenseStat: @Composable (Modifier) -> Unit = {
                Stat(localizedText("Expense"), expense, it, valueColor = LifeTheme.colors.expense, valueStyle = valueStyle)
            }
            if (needed * 2 + Space.lg <= maxWidth) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                    incomeStat(Modifier.weight(1f))
                    expenseStat(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    incomeStat(Modifier.fillMaxWidth())
                    expenseStat(Modifier.fillMaxWidth())
                }
            }
        }
        RowDivider(inset = Space.lg, modifier = Modifier.padding(end = Space.lg))
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(localizedText("Net"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                signedMoney(totals.netCents),
                style = valueStyle.copy(fontFeatureSettings = "tnum"),
                color = if (totals.netCents < 0) LifeTheme.colors.expense else LifeTheme.colors.income,
                maxLines = 1,
            )
        }
    }
}

/** Start and end of a custom period: typed in the user's date format or picked from a calendar. */
@Composable
internal fun LedgerCustomRangeFields(
    startText: String,
    endText: String,
    startDate: LocalDate?,
    endDate: LocalDate?,
    onStart: (String) -> Unit,
    onEnd: (String) -> Unit,
    onStartPicked: (LocalDate) -> Unit,
    onEndPicked: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        AdaptivePair(
            first = {
                LifeTextField(
                    startText, onStart,
                    placeholder = localizedText("Start date"),
                    isError = startDate == null,
                    trailing = { DatePickerButton(startDate, onStartPicked) },
                    modifier = it,
                )
            },
            second = {
                LifeTextField(
                    endText, onEnd,
                    placeholder = localizedText("End date"),
                    isError = endDate == null,
                    trailing = { DatePickerButton(endDate, onEndPicked) },
                    modifier = it,
                )
            },
        )
        if (startDate == null || endDate == null) {
            Text(
                localizedText("Enter a valid day, month/day, or month/day/year"),
                style = MaterialTheme.typography.bodySmall,
                color = LifeTheme.colors.danger,
            )
        }
    }
}

/** Two fields side by side when there is room for both, otherwise one under the other. */
@Composable
internal fun AdaptivePair(
    modifier: Modifier = Modifier,
    firstWeight: Float = 1f,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 330.dp * LocalDensity.current.fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                first(Modifier.weight(firstWeight))
                second(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Adds an entry for today, now, from an amount and a few words. Details opens the full editor
 * with what was typed so far. A request from the home-screen widget puts the cursor in the
 * amount field and shows the keyboard.
 */
@Composable
internal fun LedgerQuickAdd(
    state: LedgerQuickAddUi,
    onSave: (LedgerDraft) -> Unit,
    onConsumeSuccess: () -> Unit,
    onDetails: (LedgerDraft) -> Unit,
    requestToken: String?,
    onRequestHandled: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var what by rememberSaveable { mutableStateOf("") }
    var typeName by rememberSaveable { mutableStateOf(LedgerType.EXPENSE.name) }
    val type = LedgerType.entries.firstOrNull { it.name == typeName } ?: LedgerType.EXPENSE
    val cents = parseAmountCents(amount)
    val amountFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val handled by rememberUpdatedState(onRequestHandled)

    LaunchedEffect(requestToken) {
        val token = requestToken ?: return@LaunchedEffect
        runCatching { amountFocus.requestFocus() }
        keyboard?.show()
        handled(token)
    }
    LaunchedEffect(state.saveSucceeded) {
        if (state.saveSucceeded) {
            amount = ""
            what = ""
            typeName = LedgerType.EXPENSE.name
            onConsumeSuccess()
        }
    }

    fun draft(amountCents: Long) = newLedgerDraft().copy(type = type, amountCents = amountCents, merchant = what.trim())
    fun save() {
        if (state.isSaving) return
        cents?.let { onSave(draft(it)) }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Segmented(
                listOf(LedgerType.EXPENSE, LedgerType.INCOME), type, { typeName = it.name },
                { localizedText(it.displayName()) },
            )
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !state.isSaving, onClick = { onDetails(draft(cents ?: 0)) }) { Text(localizedText("Details")) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val amountWidth = (if (maxWidth < 340.dp) 96.dp else 120.dp) * fontScale
            val amountField: @Composable (Modifier) -> Unit = { fieldModifier ->
                LifeTextField(
                    amount, { candidate -> sanitizeAmountInput(candidate)?.let { amount = it } },
                    placeholder = "0.00",
                    prefix = "$",
                    isError = (amount.isNotEmpty() && cents == null) || state.failureMessage != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    modifier = fieldModifier.focusRequester(amountFocus),
                )
            }
            val whatField: @Composable (Modifier) -> Unit = { fieldModifier ->
                LifeTextField(
                    what, { what = it },
                    placeholder = localizedText("Where or what"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = fieldModifier,
                )
            }
            val saveButton: @Composable () -> Unit = {
                FilledIconButton(onClick = ::save, enabled = cents != null && !state.isSaving, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Check, localizedText("Save"))
                }
            }
            if (maxWidth < 280.dp * fontScale) {
                // Large fonts: the amount and Save share a line, the words get a line of their own.
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        amountField(Modifier.weight(1f))
                        saveButton()
                    }
                    whatField(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    amountField(Modifier.width(amountWidth))
                    whatField(Modifier.weight(1f))
                    saveButton()
                }
            }
        }
        val message = state.failureMessage
            ?: "Up to $999,999,999.99 · max 2 decimal places".takeIf { amount.isNotEmpty() && cents == null }
        if (message != null) {
            Text(localizedText(message), style = MaterialTheme.typography.bodySmall, color = LifeTheme.colors.danger)
        }
    }
}

/**
 * The Entries list: [header] (the period and its totals) and [quickAdd] scroll away with the
 * entries, so small screens keep room for them. Entries are grouped by day, newest first, with
 * each day's net.
 */
@Composable
internal fun LedgerEntriesList(
    shown: List<LedgerEntryEntity>,
    query: String,
    onQuery: (String) -> Unit,
    formatting: LedgerDisplayFormatting,
    entriesWithAttachments: Set<Long>,
    selectedEntryId: Long?,
    listState: LazyListState,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    quickAdd: @Composable () -> Unit,
) {
    val language = LocalUiLanguage.current
    val today = LocalDate.now()
    val days = remember(shown) { shown.groupBy { it.epochDay }.toList() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        // The last rows stay clear of the floating button.
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.xs, bottom = 96.dp),
    ) {
        item(key = "period") { header() }
        item(key = "quick") { Box(Modifier.padding(top = Space.lg)) { quickAdd() } }
        item(key = "search") {
            SearchField(query, onQuery, Modifier.fillMaxWidth().padding(top = Space.sm, bottom = Space.xs))
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = localizedText(if (query.isBlank()) "No entries in this period" else "No entries match"),
                    icon = Icons.Rounded.ReceiptLong,
                    body = localizedText(
                        if (query.isBlank()) "Type an amount above and save it, or tap + for an entry with all details."
                        else "Try other words, or pick a longer period.",
                    ),
                )
            }
        }
        days.forEach { (day, rows) ->
            item(key = "day-$day") {
                val net = rows.sumOf { if (it.type == LedgerType.INCOME) it.amountCents else -it.amountCents }
                SectionLabel(
                    ledgerDayHeading(day, today, formatting, language),
                    // In line with the rows' text and amounts.
                    modifier = Modifier.padding(top = Space.sm).padding(horizontal = Space.md),
                    trailing = {
                        Text(
                            signedMoney(net),
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            items(rows, key = { it.id }) { entry ->
                LedgerEntryRow(
                    entry = entry,
                    formatting = formatting,
                    hasAttachments = entry.id in entriesWithAttachments,
                    selected = entry.id == selectedEntryId,
                    onOpen = { onOpen(entry.id) },
                    onDelete = { onDelete(entry.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * One entry: what it was, a quiet line with the time, note and tags, marks for repeating and
 * attached files, and the signed amount. Tap opens it; swipe left deletes it (with Undo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerEntryRow(
    entry: LedgerEntryEntity,
    formatting: LedgerDisplayFormatting,
    hasAttachments: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalUiLanguage.current
    val delete by rememberUpdatedState(onDelete)
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) delete()
            // The row leaves when the entry does; until then it slides back.
            false
        },
    )
    val label = ledgerLabel(entry.merchant, entry.note, entry.type, language)
    val detail = listOf(
        formatting.time(entry.minuteOfDay),
        entry.note.takeIf { entry.merchant.isNotBlank() }.orEmpty(),
        parseTags(entry.tagsCsv).joinToString(" ") { "#$it" },
    ).filter(String::isNotBlank).joinToString(" · ")
    val deleteLabel = ledgerDeleteActionLabel(entry, formatting.date(entry.epochDay), language)
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Only while the row is being swiped, so nothing red shows around a resting row.
            if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(LifeTheme.colors.dangerContainer)
                        .padding(horizontal = Space.lg),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = LifeTheme.colors.danger)
                }
            }
        },
    ) {
        ListRow(
            title = label,
            supporting = detail,
            maxTitleLines = 1,
            selected = selected,
            onClick = onOpen,
            onClickLabel = localizedText("Edit entry"),
            // Opaque, so the red behind the row only shows while it is being swiped.
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.medium)
                .semantics { customActions = listOf(CustomAccessibilityAction(deleteLabel) { delete(); true }) },
            trailing = {
                if (entry.seriesId != null) {
                    Icon(Icons.Rounded.Repeat, localizedText("Repeats"), Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hasAttachments) {
                    Icon(Icons.Rounded.AttachFile, localizedText("Attachments"), Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MoneyText(entry.amountCents, entry.type, formatMoney(entry.amountCents), modifier = Modifier.padding(start = Space.xs))
            },
        )
    }
}

/** What a screen reader calls the delete action of a row. */
private fun ledgerDeleteActionLabel(entry: LedgerEntryEntity, formattedDate: String, language: UiLanguage): String =
    when (language) {
        UiLanguage.ENGLISH -> ledgerEntryActionDescription("Delete", entry, formattedDate)
        UiLanguage.SIMPLIFIED_CHINESE ->
            "删除 ${ledgerLabel(entry.merchant, entry.note, entry.type, language)}，${formatMoney(entry.amountCents)}，$formattedDate"
    }

internal fun newLedgerDraft(now: LocalDateTime = LocalDateTime.now()): LedgerDraft {
    return LedgerDraft(
        amountCents = 0,
        epochDay = now.toLocalDate().toEpochDay(),
        minuteOfDay = now.hour * 60 + now.minute,
    )
}

internal fun ledgerEntryActionDescription(
    action: String,
    entry: LedgerEntryEntity,
    formattedDate: String,
): String {
    val label = entry.merchant.ifBlank { entry.note.ifBlank { entry.type.displayName() } }
    val signedAmount = if (entry.type == LedgerType.INCOME) entry.amountCents else -entry.amountCents
    return "$action $label, ${entry.type.displayName().lowercase()} " +
        "${formatSignedMoney(signedAmount)}, $formattedDate"
}

internal fun receiptCopyFailureMessage(reason: String): String {
    val detail = reason.ifBlank { "Unknown receipt copy error" }
    return "Entry saved, but receipts failed to copy: $detail. " +
        "Retry Save to copy them; the entry will not be duplicated."
}

/** Bundle-backed editor state keeps an open editor and its seed data through rotation/process restore. */
internal fun LedgerDraft.toEditorState(): Bundle = Bundle().apply {
    putBoolean("has_id", id != null)
    id?.let { putLong("id", it) }
    putString("type", type.name)
    putLong("amount", amountCents)
    putLong("date", epochDay)
    putInt("time", minuteOfDay)
    putString("note", note)
    putString("merchant", merchant)
    putStringArrayList("tags", ArrayList(tags))
    putBoolean("has_recurrence", recurrence != null)
    recurrence?.let { rule ->
        putString("recurrence_unit", rule.unit.name)
        putInt("recurrence_interval", rule.interval)
        putBoolean("has_recurrence_end", rule.endEpochDay != null)
        rule.endEpochDay?.let { putLong("recurrence_end", it) }
    }
}

internal fun Bundle.toLedgerDraft(): LedgerDraft? = runCatching {
    val recurrence = if (getBoolean("has_recurrence")) {
        RecurrenceRule(
            unit = RecurrenceUnit.valueOf(getString("recurrence_unit") ?: return null),
            interval = getInt("recurrence_interval", 1),
            endEpochDay = if (getBoolean("has_recurrence_end")) getLong("recurrence_end") else null,
        )
    } else {
        null
    }
    LedgerDraft(
        id = if (getBoolean("has_id")) getLong("id") else null,
        type = LedgerType.valueOf(getString("type") ?: LedgerType.EXPENSE.name),
        amountCents = getLong("amount"),
        epochDay = getLong("date"),
        minuteOfDay = getInt("time"),
        note = getString("note").orEmpty(),
        merchant = getString("merchant").orEmpty(),
        tags = getStringArrayList("tags").orEmpty(),
        recurrence = recurrence,
    )
}.getOrNull()
