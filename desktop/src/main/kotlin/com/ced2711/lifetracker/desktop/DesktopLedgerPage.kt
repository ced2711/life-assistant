package com.ced2711.lifetracker.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.MoneyTotals
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.parseTags
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.MoneyText
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.Pill
import com.ced2711.lifetracker.ui.design.Segmented
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.design.Stat
import com.ced2711.lifetracker.ui.design.Tag
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.launch

/** The span of time the ledger page summarises. */
private enum class LedgerPeriod(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All"), CUSTOM("Custom") }

private enum class LedgerTab(val label: String) { ENTRIES("Entries"), STATISTICS("Statistics"), RECURRING("Recurring") }

private data class PeriodRange(val start: Long, val end: Long)

private fun LedgerPeriod.range(anchor: LocalDate, firstDayOfWeek: DayOfWeek, entries: List<LedgerEntryEntity>, custom: PeriodRange?): PeriodRange = when (this) {
    LedgerPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).let { PeriodRange(it.toEpochDay(), it.plusDays(6).toEpochDay()) }
    LedgerPeriod.MONTH -> YearMonth.from(anchor).let { PeriodRange(it.atDay(1).toEpochDay(), it.atEndOfMonth().toEpochDay()) }
    LedgerPeriod.YEAR -> PeriodRange(anchor.withDayOfYear(1).toEpochDay(), anchor.withDayOfYear(anchor.lengthOfYear()).toEpochDay())
    LedgerPeriod.ALL -> PeriodRange(entries.minOfOrNull { it.epochDay } ?: anchor.toEpochDay(), maxOf(entries.maxOfOrNull { it.epochDay } ?: anchor.toEpochDay(), anchor.toEpochDay()))
    LedgerPeriod.CUSTOM -> custom ?: YearMonth.from(anchor).let { PeriodRange(it.atDay(1).toEpochDay(), it.atEndOfMonth().toEpochDay()) }
}

private fun LedgerPeriod.shift(anchor: LocalDate, steps: Long): LocalDate = when (this) {
    LedgerPeriod.WEEK -> anchor.plusWeeks(steps)
    LedgerPeriod.MONTH -> anchor.plusMonths(steps)
    LedgerPeriod.YEAR -> anchor.plusYears(steps)
    LedgerPeriod.ALL, LedgerPeriod.CUSTOM -> anchor
}

private fun periodTitle(period: LedgerPeriod, range: PeriodRange, snapshot: BackupSnapshot, language: UiLanguage): String {
    val locale = uiLocale(language)
    val start = LocalDate.ofEpochDay(range.start)
    fun span() = UserFormatting.formatDate(start, snapshot.settings.dateFormat, locale) + " – " +
        UserFormatting.formatDate(LocalDate.ofEpochDay(range.end), snapshot.settings.dateFormat, locale)
    return when (period) {
        LedgerPeriod.WEEK, LedgerPeriod.CUSTOM -> span()
        LedgerPeriod.MONTH -> monthTitle(YearMonth.from(start), language)
        LedgerPeriod.YEAR -> start.year.toString()
        LedgerPeriod.ALL -> desktopText("All time", language)
    }
}

internal fun monthTitle(month: YearMonth, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "${month.year}年${month.monthValue}月"
    UiLanguage.ENGLISH -> month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
}

private sealed interface LedgerEditing {
    data class New(val epochDay: Long) : LedgerEditing
    data class Entry(val id: Long) : LedgerEditing
    data class Schedule(val seriesId: Long) : LedgerEditing
}

internal fun signedMoney(cents: Long): String = (if (cents < 0) "−" else "+") + formatMoney(kotlin.math.abs(cents))

/**
 * Money in and out. Entries: a period you can step through, its totals, quick add, search and
 * the entries by day, with the editor (or a small chart) beside them. Statistics: the period's
 * trend, ratio and where the money went. Recurring: the schedules.
 */
@Composable
internal fun LedgerPage(snapshot: BackupSnapshot, store: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    val snackbar = remember { SnackbarHostState() }
    var period by remember { mutableStateOf(LedgerPeriod.MONTH) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var customStart by remember { mutableStateOf("") }
    var customEnd by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(LedgerTab.ENTRIES) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<LedgerEditing?>(null) }
    var deleteScopeFor by remember { mutableStateOf<LedgerEntryEntity?>(null) }
    var quickAmount by remember { mutableStateOf("") }
    var quickWhat by remember { mutableStateOf("") }
    var quickType by remember { mutableStateOf(LedgerType.EXPENSE) }
    val quickFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    RegisterPageShortcuts(
        onNew = { editing = LedgerEditing.New(LocalDate.now().toEpochDay()) },
        onFind = {
            tab = LedgerTab.ENTRIES
            runCatching { searchFocus.requestFocus() }
        },
    )

    val live = snapshot.ledgerEntries.filter { it.deletedAt == null }
    val firstDay = UserFormatting.firstDayOfWeek(snapshot.settings.weekStart, locale)
    val customRange = run {
        val start = parseUserDate(customStart, snapshot, language)?.toEpochDay()
        val end = parseUserDate(customEnd, snapshot, language)?.toEpochDay()
        if (start != null && end != null && end >= start) PeriodRange(start, end) else null
    }
    val range = period.range(anchor, firstDay, live, customRange)
    val inPeriod = live.filter { it.epochDay in range.start..range.end }
    val shown = inPeriod.filter { entry ->
        query.isBlank() || entry.merchant.contains(query, true) || entry.note.contains(query, true) ||
            parseTags(entry.tagsCsv).any { it.contains(query.trim().removePrefix("#"), true) }
    }.sortedWith(compareByDescending<LedgerEntryEntity> { it.epochDay }.thenByDescending { it.minuteOfDay }.thenByDescending { it.id })
    val totals = MoneyTotals.of(inPeriod)

    fun deleteEntry(entry: LedgerEntryEntity, deleteScope: SeriesEditScope) {
        scope.launch {
            val deletion = store.deleteLedgerWithUndo(entry.id, deleteScope) ?: return@launch
            if (editing == LedgerEditing.Entry(entry.id)) editing = null
            val result = snackbar.showSnackbar(desktopText("Entry deleted", language), desktopText("Undo", language), duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) store.undoDeleteLedger(deletion) else store.runMaintenance()
        }
    }
    fun requestDelete(entry: LedgerEntryEntity) {
        if (entry.seriesId != null) deleteScopeFor = entry else deleteEntry(entry, SeriesEditScope.ONLY_THIS_OCCURRENCE)
    }
    fun addQuick() {
        val cents = parseAmountCents(quickAmount) ?: return
        val what = quickWhat.trim()
        val now = LocalTime.now()
        scope.launch {
            store.saveLedger(
                LedgerDraft(type = quickType, amountCents = cents, epochDay = LocalDate.now().toEpochDay(), minuteOfDay = now.hour * 60 + now.minute, merchant = what),
                SeriesEditScope.ONLY_THIS_OCCURRENCE,
            )
        }
        quickAmount = ""
        quickWhat = ""
        runCatching { quickFocus.requestFocus() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val sidePanel = maxWidth >= 1_000.dp
            val current = editing
            if (current != null && !sidePanel) {
                LedgerEditor(snapshot, store, current, onClose = { editing = null }, onDelete = ::requestDelete, modifier = Modifier.fillMaxSize())
                return@BoxWithConstraints
            }
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    PageHeader("Ledger") {
                        Segmented(LedgerTab.entries, tab, { tab = it }, { desktopText(it.label) })
                        Spacer(Modifier.width(Space.sm))
                        Button(onClick = { editing = LedgerEditing.New(LocalDate.now().toEpochDay()) }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(desktopText("New entry"))
                        }
                    }
                    if (tab == LedgerTab.RECURRING) {
                        LedgerSchedules(snapshot, store, onEdit = { editing = LedgerEditing.Schedule(it) }, modifier = Modifier.weight(1f).fillMaxWidth())
                        return@Column
                    }
                    PeriodBar(
                        period = period,
                        title = periodTitle(period, range, snapshot, language),
                        onPeriod = { period = it },
                        onShift = { anchor = period.shift(anchor, it) },
                        onToday = { anchor = LocalDate.now() },
                        showToday = period != LedgerPeriod.ALL && period != LedgerPeriod.CUSTOM && LocalDate.now().toEpochDay() !in range.start..range.end,
                    )
                    if (period == LedgerPeriod.CUSTOM) {
                        Row(Modifier.padding(horizontal = PagePadding).padding(bottom = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                            LifeTextField(
                                customStart, { customStart = it }, placeholder = desktopText("From"),
                                isError = customStart.isNotBlank() && parseUserDate(customStart, snapshot, language) == null,
                                trailing = { DatePickerButton(parseUserDate(customStart, snapshot, language)) { customStart = UserFormatting.formatDate(it, snapshot.settings.dateFormat, locale) } },
                                modifier = Modifier.width(200.dp),
                            )
                            Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LifeTextField(
                                customEnd, { customEnd = it }, placeholder = desktopText("To"),
                                isError = customEnd.isNotBlank() && customRange == null,
                                trailing = { DatePickerButton(parseUserDate(customEnd, snapshot, language)) { customEnd = UserFormatting.formatDate(it, snapshot.settings.dateFormat, locale) } },
                                modifier = Modifier.width(200.dp),
                            )
                        }
                    }
                    TotalsPanel(totals, inPeriod, range, Modifier.padding(horizontal = PagePadding))
                    if (tab == LedgerTab.STATISTICS) {
                        LedgerStatistics(snapshot, range, inPeriod, totals, Modifier.weight(1f).fillMaxWidth())
                        return@Column
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = PagePadding, end = PagePadding, top = Space.lg),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Segmented(listOf(LedgerType.EXPENSE, LedgerType.INCOME), quickType, { quickType = it }, { desktopText(if (it == LedgerType.EXPENSE) "Expense" else "Income") })
                        LifeTextField(
                            quickAmount, { if (isValidDesktopAmountInput(it)) quickAmount = it },
                            placeholder = "0.00", prefix = "$",
                            modifier = Modifier.width(120.dp).focusRequester(quickFocus).onEnter(::addQuick),
                        )
                        LifeTextField(
                            quickWhat, { quickWhat = it },
                            placeholder = desktopText("Where or what, then Enter"),
                            modifier = Modifier.weight(1f).onEnter(::addQuick),
                        )
                        LifeTextField(
                            query, { query = it },
                            placeholder = desktopText("Search (Ctrl+F)"),
                            leadingIcon = Icons.Rounded.Search,
                            trailing = if (query.isNotEmpty()) {
                                { IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, desktopText("Clear"), Modifier.size(16.dp)) } }
                            } else {
                                null
                            },
                            modifier = Modifier.width(220.dp).focusRequester(searchFocus),
                        )
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PagePadding - Space.md)) {
                        if (shown.isEmpty()) item {
                            EmptyState(
                                title = desktopText(if (query.isBlank()) "No entries in this period" else "No entries match"),
                                icon = Icons.Rounded.ReceiptLong,
                                body = desktopText(if (query.isBlank()) "Type an amount above and press Enter, or press Ctrl+N for an entry with all details." else "Try other words, or pick a longer period."),
                            )
                        }
                        shown.groupBy { it.epochDay }.forEach { (day, rows) ->
                            item(key = "day-$day") {
                                val net = rows.sumOf { if (it.type == LedgerType.INCOME) it.amountCents else -it.amountCents }
                                Row(Modifier.fillMaxWidth().padding(start = Space.md, end = Space.md, top = Space.lg, bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatDeadline(day, null, snapshot, language), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text(signedMoney(net), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(rows, key = { it.id }) { entry ->
                                LedgerRow(entry, snapshot, selected = editing == LedgerEditing.Entry(entry.id), onDelete = { requestDelete(entry) }) { editing = LedgerEditing.Entry(entry.id) }
                            }
                        }
                        item { Spacer(Modifier.height(48.dp)) }
                    }
                }
                if (current != null) {
                    ColumnDivider()
                    LedgerEditor(snapshot, store, current, onClose = { editing = null }, onDelete = ::requestDelete, modifier = Modifier.width(440.dp).fillMaxHeight())
                }
            }
        }
    }
    deleteScopeFor?.let { entry ->
        SeriesScopeDialog("Delete repeating entry", { deleteScopeFor = null }) { chosen ->
            deleteScopeFor = null
            deleteEntry(entry, chosen)
        }
    }
}

@Composable
private fun PeriodBar(period: LedgerPeriod, title: String, onPeriod: (LedgerPeriod) -> Unit, onShift: (Long) -> Unit, onToday: () -> Unit, showToday: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = PagePadding - 12.dp, end = PagePadding, bottom = Space.md), verticalAlignment = Alignment.CenterVertically) {
        val steps = period != LedgerPeriod.ALL && period != LedgerPeriod.CUSTOM
        if (steps) IconButton(onClick = { onShift(-1) }) { Icon(Icons.Rounded.ChevronLeft, desktopText("Previous")) } else Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.widthIn(min = 150.dp))
        if (steps) IconButton(onClick = { onShift(1) }) { Icon(Icons.Rounded.ChevronRight, desktopText("Next")) }
        if (showToday) TextButton(onClick = onToday) { Text(desktopText("Today")) }
        Spacer(Modifier.weight(1f))
        Segmented(LedgerPeriod.entries, period, onPeriod, { desktopText(it.label) })
    }
}

/** The period in three numbers, with the average and the largest expense as quiet extras. */
@Composable
private fun TotalsPanel(totals: MoneyTotals, entries: List<LedgerEntryEntity>, range: PeriodRange, modifier: Modifier = Modifier) {
    val days = (minOf(range.end, LocalDate.now().toEpochDay()) - range.start + 1).coerceAtLeast(1)
    val largest = entries.filter { it.type == LedgerType.EXPENSE }.maxByOrNull { it.amountCents }
    Panel(modifier.fillMaxWidth(), padding = PaddingValues(horizontal = Space.xxl, vertical = Space.xl)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl), verticalAlignment = Alignment.Top) {
            Stat(desktopText("Income"), formatMoney(totals.incomeCents), Modifier.weight(1f), valueColor = LifeTheme.colors.income)
            Stat(desktopText("Expense"), formatMoney(totals.expenseCents), Modifier.weight(1f), valueColor = LifeTheme.colors.expense)
            Stat(desktopText("Net"), signedMoney(totals.netCents), Modifier.weight(1f), valueColor = if (totals.netCents < 0) LifeTheme.colors.expense else LifeTheme.colors.income)
            Stat(desktopText("Average daily spending"), formatMoney(totals.expenseCents / days), Modifier.weight(1f), valueStyle = MaterialTheme.typography.titleLarge)
            Stat(
                desktopText("Largest expense"),
                largest?.let { formatMoney(it.amountCents) } ?: "—",
                Modifier.weight(1f),
                caption = largest?.let { desktopText(entryLabel(it)) },
                valueStyle = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

private fun entryLabel(entry: LedgerEntryEntity): String =
    entry.merchant.ifBlank { entry.note.ifBlank { if (entry.type == LedgerType.INCOME) "Income" else "Expense" } }

@Composable
private fun LedgerRow(entry: LedgerEntryEntity, snapshot: BackupSnapshot, selected: Boolean, onDelete: () -> Unit, onClick: () -> Unit) {
    val language = LocalUiLanguage.current
    val attachments = snapshot.attachments.count { it.ownerType == AttachmentOwnerType.LEDGER && it.ownerId == entry.id && it.pendingDeleteAt == null }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> LifeTheme.colors.accentSoft
            hovered -> MaterialTheme.colorScheme.surfaceContainer
            else -> Color.Transparent
        },
        tween(120),
        label = "ledger-row",
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(background).hoverable(interaction).clickable(onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = Space.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            UserFormatting.formatMinuteOfDay(entry.minuteOfDay, snapshot.settings.timeFormat, false, uiLocale(language)),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(desktopText(entryLabel(entry)), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.seriesId != null) Icon(Icons.Rounded.Repeat, desktopText("Repeats"), Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (attachments > 0) Icon(Icons.Rounded.AttachFile, desktopText("Attachments"), Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val detail = listOf(entry.note.takeIf { entry.merchant.isNotBlank() }.orEmpty(), parseTags(entry.tagsCsv).joinToString(" ") { "#$it" }).filter(String::isNotBlank).joinToString("  ")
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (hovered || selected) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.DeleteOutline, desktopText("Delete"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        MoneyText(entry.amountCents, entry.type, formatMoney(entry.amountCents), modifier = Modifier.padding(start = Space.sm))
    }
}

private data class TrendBucket(val label: String, val income: Long, val expense: Long)

/** Per day for up to 46 days, per month for up to about two years, otherwise per year. */
private fun trendBuckets(range: PeriodRange, entries: List<LedgerEntryEntity>, language: UiLanguage): List<TrendBucket> {
    val days = range.end - range.start + 1
    fun totals(rows: List<LedgerEntryEntity>) = MoneyTotals.of(rows)
    return when {
        days <= 46 -> {
            val byDay = entries.groupBy { it.epochDay }
            (range.start..range.end).map { day ->
                val date = LocalDate.ofEpochDay(day)
                val label = if (days <= 7) date.dayOfWeek.getDisplayName(TextStyle.SHORT, uiLocale(language)) else date.dayOfMonth.toString()
                totals(byDay[day].orEmpty()).let { TrendBucket(label, it.incomeCents, it.expenseCents) }
            }
        }
        days <= 800 -> {
            val byMonth = entries.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }
            var month = YearMonth.from(LocalDate.ofEpochDay(range.start))
            val last = YearMonth.from(LocalDate.ofEpochDay(range.end))
            buildList {
                while (month <= last) {
                    val label = if (days <= 366) month.monthValue.toString() else "${month.year % 100}/${month.monthValue}"
                    totals(byMonth[month].orEmpty()).let { add(TrendBucket(label, it.incomeCents, it.expenseCents)) }
                    month = month.plusMonths(1)
                }
            }
        }
        else -> entries.groupBy { LocalDate.ofEpochDay(it.epochDay).year }.toSortedMap().map { (year, rows) ->
            totals(rows).let { TrendBucket(year.toString(), it.incomeCents, it.expenseCents) }
        }
    }
}

/** The period as a picture: income and expense over time, their ratio, and spending by tag. */
@Composable
private fun LedgerStatistics(snapshot: BackupSnapshot, range: PeriodRange, entries: List<LedgerEntryEntity>, totals: MoneyTotals, modifier: Modifier) {
    val language = LocalUiLanguage.current
    val buckets = remember(range, entries, language) { trendBuckets(range, entries, language) }
    var selected by remember(range) { mutableStateOf<Int?>(null) }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = PagePadding, end = PagePadding, top = Space.lg, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Panel(padding = PaddingValues(Space.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(desktopText("Trend"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                LegendDot(LifeTheme.colors.income, desktopText("Income"))
                Spacer(Modifier.width(Space.md))
                LegendDot(LifeTheme.colors.expense, desktopText("Expense"))
            }
            val picked = selected?.let(buckets::getOrNull)
            Text(
                picked?.let { "${it.label} · ${desktopText("Income")} ${formatMoney(it.income)} · ${desktopText("Expense")} ${formatMoney(it.expense)}" }
                    ?: desktopText("Click a bar for its numbers"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = Space.md),
            )
            TrendBars(buckets, selected, { selected = if (selected == it) null else it }, Modifier.fillMaxWidth().height(220.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            Panel(Modifier.weight(1f), padding = PaddingValues(Space.xl)) {
                Text(desktopText("Income and expense"), style = MaterialTheme.typography.titleSmall)
                val sum = totals.incomeCents + totals.expenseCents
                if (sum == 0L) {
                    Text(desktopText("No activity in this period"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Space.md))
                } else {
                    val share = totals.incomeCents.toFloat() / sum
                    Row(Modifier.fillMaxWidth().padding(top = Space.md).height(10.dp).clip(CircleShape)) {
                        if (share > 0f) Box(Modifier.weight(share.coerceAtLeast(0.01f)).fillMaxHeight().background(LifeTheme.colors.income))
                        if (share < 1f) Box(Modifier.weight((1f - share).coerceAtLeast(0.01f)).fillMaxHeight().background(LifeTheme.colors.expense))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = Space.sm)) {
                        Text("${(share * 100).toInt()}% " + desktopText("Income"), style = MaterialTheme.typography.bodySmall, color = LifeTheme.colors.income, modifier = Modifier.weight(1f))
                        Text("${100 - (share * 100).toInt()}% " + desktopText("Expense"), style = MaterialTheme.typography.bodySmall, color = LifeTheme.colors.expense)
                    }
                }
            }
            Panel(Modifier.weight(1f), padding = PaddingValues(Space.xl)) {
                Text(desktopText("Spending by tag"), style = MaterialTheme.typography.titleSmall)
                val expenses = entries.filter { it.type == LedgerType.EXPENSE }
                val total = expenses.sumOf { it.amountCents }
                if (total == 0L) {
                    Text(desktopText("No expenses in this period"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Space.md))
                } else {
                    val byTag = expenses.flatMap { entry -> parseTags(entry.tagsCsv).ifEmpty { listOf("") }.map { it to entry.amountCents } }
                        .groupBy({ it.first.lowercase() }, { it.second }).mapValues { it.value.sum() }
                        .entries.sortedByDescending { it.value }.take(8)
                    byTag.forEach { (tag, cents) ->
                        Column(Modifier.padding(top = Space.md)) {
                            Row {
                                Text(if (tag.isEmpty()) desktopText("Untagged") else "#$tag", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(formatMoney(cents), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                                Box(Modifier.fillMaxWidth((cents.toFloat() / total).coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(LifeTheme.colors.expense))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.ced2711.lifetracker.ui.design.Dot(color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Income and expense side by side per bucket; click a bucket to select it. */
@Composable
private fun TrendBars(buckets: List<TrendBucket>, selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier) {
    if (buckets.isEmpty() || buckets.all { it.income == 0L && it.expense == 0L }) {
        Box(modifier, contentAlignment = Alignment.Center) { Text(desktopText("No activity in this period"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    val maximum = buckets.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1L)
    val income = LifeTheme.colors.income
    val expense = LifeTheme.colors.expense
    val grid = LifeTheme.colors.divider
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val summary = buckets.joinToString { "${it.label}: +${formatMoney(it.income)} −${formatMoney(it.expense)}" }
    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = summary }
                .pointerInput(buckets.size) { detectTapGestures { offset -> onSelect((offset.x / (size.width.toFloat() / buckets.size)).toInt().coerceIn(0, buckets.lastIndex)) } },
        ) {
            val slot = size.width / buckets.size
            listOf(0f, 0.5f, 1f).forEach { level -> drawLine(grid, Offset(0f, size.height * level), Offset(size.width, size.height * level)) }
            buckets.forEachIndexed { index, bucket ->
                if (index == selected) drawRoundRect(highlight, Offset(index * slot, 0f), Size(slot, size.height), CornerRadius(6f))
                val bar = (slot * 0.3f).coerceAtMost(18f)
                val gap = (slot * 0.06f).coerceAtMost(3f)
                val left = index * slot + (slot - (bar * 2 + gap)) / 2f
                fun draw(value: Long, x: Float, color: Color) {
                    if (value <= 0L) return
                    val height = (value.toFloat() / maximum * (size.height - 4f)).coerceAtLeast(2f)
                    drawRoundRect(color, Offset(x, size.height - height), Size(bar, height), CornerRadius(bar / 3f))
                }
                draw(bucket.income, left, income)
                draw(bucket.expense, left + bar + gap, expense)
            }
        }
        // Label only every few bars so a month stays readable.
        val every = ((buckets.size + 11) / 12).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            buckets.chunked(every).forEach { group ->
                Text(group.first().label, modifier = Modifier.weight(group.size.toFloat()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Recurring schedules: what repeats, how often, and Change / Stop / Remove. */
@Composable
private fun LedgerSchedules(snapshot: BackupSnapshot, store: DesktopDataStore, onEdit: (Long) -> Unit, modifier: Modifier) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    var confirmStop by remember { mutableStateOf<LedgerSeriesEntity?>(null) }
    var confirmRemove by remember { mutableStateOf<LedgerSeriesEntity?>(null) }
    val series = snapshot.ledgerSeries.sortedWith(compareByDescending<LedgerSeriesEntity> { it.active }.thenBy { it.startEpochDay })
    LazyColumn(modifier.padding(horizontal = PagePadding), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        item {
            Text(
                desktopText("Entries are added on schedule. Stopping a schedule keeps the entries it already created."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.sm),
            )
        }
        if (series.isEmpty()) item {
            EmptyState(title = desktopText("No recurring entries"), icon = Icons.Rounded.Repeat, body = desktopText("Choose Repeat when adding an entry, for rent, salary or subscriptions."))
        }
        items(series, key = { it.id }) { item ->
            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = Space.xl, vertical = Space.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Text(desktopText(item.merchant.ifBlank { item.note.ifBlank { if (item.type == LedgerType.INCOME) "Income" else "Expense" } }), style = MaterialTheme.typography.titleMedium)
                            if (!item.active) Tag(desktopText("Stopped"), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(scheduleText(item, snapshot, language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MoneyText(item.amountCents, item.type, formatMoney(item.amountCents), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Space.lg))
                    if (item.active) {
                        TextButton(onClick = { onEdit(item.id) }) { Text(desktopText("Change")) }
                        TextButton(onClick = { confirmStop = item }) { Text(desktopText("Stop")) }
                    } else {
                        TextButton(onClick = { confirmRemove = item }) { Text(desktopText("Remove"), color = LifeTheme.colors.danger) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
    confirmStop?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmStop = null },
            title = { Text(desktopText("Stop this schedule?")) },
            text = { Text(desktopText("No new entries will be added. Entries it already created stay.")) },
            dismissButton = { TextButton(onClick = { confirmStop = null }) { Text(desktopText("Cancel")) } },
            confirmButton = { Button(onClick = { scope.launch { store.stopLedgerSeries(item.id) }; confirmStop = null }) { Text(desktopText("Stop")) } },
        )
    }
    confirmRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(desktopText("Remove this schedule?")) },
            text = { Text(desktopText("The entries it created stay as ordinary entries.")) },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(desktopText("Cancel")) } },
            confirmButton = { Button(onClick = { scope.launch { store.deleteStoppedLedgerSeries(item.id) }; confirmRemove = null }) { Text(desktopText("Remove")) } },
        )
    }
}

private fun scheduleText(series: LedgerSeriesEntity, snapshot: BackupSnapshot, language: UiLanguage): String {
    val locale = uiLocale(language)
    val start = UserFormatting.formatDate(LocalDate.ofEpochDay(series.startEpochDay), snapshot.settings.dateFormat, locale)
    val end = series.endEpochDay?.let { UserFormatting.formatDate(LocalDate.ofEpochDay(it), snapshot.settings.dateFormat, locale) }
    return desktopRepeatSummary(series.recurrenceUnit, series.intervalCount, start, end, language)
}

/** The ledger editor, in the side column or (for the calendar) in its own window. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedgerEditor(
    snapshot: BackupSnapshot,
    store: DesktopDataStore,
    editing: LedgerEditing,
    onClose: () -> Unit,
    onDelete: (LedgerEntryEntity) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    val entry = (editing as? LedgerEditing.Entry)?.let { e -> snapshot.ledgerEntries.firstOrNull { it.id == e.id && it.deletedAt == null } }
    if (editing is LedgerEditing.Entry && entry == null) {
        LaunchedEffect(editing) { onClose() }
        return
    }
    val scheduleOnly = (editing as? LedgerEditing.Schedule)?.let { e -> snapshot.ledgerSeries.firstOrNull { it.id == e.seriesId } }
    val series = scheduleOnly ?: entry?.seriesId?.let { id -> snapshot.ledgerSeries.firstOrNull { it.id == id } }
    val today = LocalDate.now()
    fun format(day: Long) = UserFormatting.formatDate(LocalDate.ofEpochDay(day), snapshot.settings.dateFormat, locale)
    val startDay = when (editing) {
        is LedgerEditing.New -> editing.epochDay
        is LedgerEditing.Entry -> entry!!.epochDay
        // A schedule changes from tomorrow (or its later start) on.
        is LedgerEditing.Schedule -> maxOf(today.plusDays(1).toEpochDay(), scheduleOnly?.startEpochDay ?: 0L)
    }
    var type by remember(editing) { mutableStateOf(entry?.type ?: series?.type ?: LedgerType.EXPENSE) }
    var amount by remember(editing) { mutableStateOf((entry?.amountCents ?: series?.amountCents)?.let { "%.2f".format(Locale.US, it / 100.0) }.orEmpty()) }
    var dateText by remember(editing) { mutableStateOf(format(startDay)) }
    var timeText by remember(editing) {
        val minute = entry?.minuteOfDay ?: LocalTime.now().let { it.hour * 60 + it.minute }
        mutableStateOf("%d:%02d".format(minute / 60, minute % 60))
    }
    var merchant by remember(editing) { mutableStateOf(entry?.merchant ?: series?.merchant.orEmpty()) }
    var note by remember(editing) { mutableStateOf(entry?.note ?: series?.note.orEmpty()) }
    var tags by remember(editing) { mutableStateOf((entry?.tagsCsv ?: series?.tagsCsv.orEmpty()).replace(",", ", ")) }
    var repeatUnit by remember(editing) { mutableStateOf(series?.recurrenceUnit) }
    var repeatInterval by remember(editing) { mutableStateOf((series?.intervalCount ?: 1).toString()) }
    var repeatEnd by remember(editing) { mutableStateOf(series?.endEpochDay?.let(::format).orEmpty()) }
    var askScope by remember(editing) { mutableStateOf<LedgerDraft?>(null) }
    var error by remember(editing) { mutableStateOf<String?>(null) }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(editing) { runCatching { amountFocus.requestFocus() } }

    val cents = parseAmountCents(amount)
    val day = parseUserDate(dateText, snapshot, language)?.toEpochDay()
    val minute = parseTimeOfDay(timeText)
    val interval = repeatInterval.toIntOrNull()
    val endDay = repeatEnd.takeIf(String::isNotBlank)?.let { parseUserDate(it, snapshot, language)?.toEpochDay() }
    val repeatInvalid = repeatUnit != null && (interval == null || interval !in 1..10_000 || (repeatEnd.isNotBlank() && (endDay == null || day == null || endDay < day)))
    val scheduleStartInvalid = scheduleOnly != null && (day == null || day <= today.toEpochDay())
    val canSave = cents != null && day != null && minute != null && !repeatInvalid && !scheduleStartInvalid && (scheduleOnly == null || repeatUnit != null)

    fun draft() = LedgerDraft(
        id = entry?.id,
        type = type,
        amountCents = requireNotNull(cents),
        epochDay = requireNotNull(day),
        minuteOfDay = requireNotNull(minute),
        note = note,
        merchant = merchant,
        tags = tags.split(','),
        recurrence = repeatUnit?.let { RecurrenceRule(it, interval ?: 1, endDay) },
    )
    fun commit(value: LedgerDraft, editScope: SeriesEditScope) {
        scope.launch {
            try {
                if (scheduleOnly != null) store.editLedgerSeriesForFuture(scheduleOnly.id, value) else store.saveLedger(value, editScope)
                onClose()
            } catch (failure: IllegalArgumentException) {
                error = failure.message
            }
        }
    }
    fun save() {
        if (!canSave) return
        val value = draft()
        if (entry?.seriesId != null) askScope = value else commit(value, SeriesEditScope.ONLY_THIS_OCCURRENCE)
    }

    EditorPane(
        title = desktopText(when { scheduleOnly != null -> "Change schedule"; entry == null -> "New entry"; else -> "Edit entry" }),
        onClose = onClose,
        modifier = modifier.editorKeys(onSave = ::save, onCancel = onClose),
        footer = {
            if (entry != null) TextButton(onClick = { onDelete(entry) }) { Text(desktopText("Delete"), color = LifeTheme.colors.danger) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text(desktopText("Cancel")) }
            Button(enabled = canSave, onClick = ::save) { Text(desktopText("Save (Ctrl+S)")) }
        },
    ) {
        if (scheduleOnly != null) {
            Text(desktopText("Entries already created stay as they are. The changed schedule starts on the date below."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Segmented(
            listOf(LedgerType.EXPENSE, LedgerType.INCOME), type, { type = it },
            { desktopText(if (it == LedgerType.EXPENSE) "Expense" else "Income") },
            fill = true, modifier = Modifier.fillMaxWidth(),
        )
        LifeTextField(
            amount, { if (isValidDesktopAmountInput(it)) amount = it },
            placeholder = "0.00", prefix = "$",
            textStyle = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            isError = amount.isNotBlank() && cents == null,
            modifier = Modifier.fillMaxWidth().focusRequester(amountFocus),
        )
        FieldLabel(if (scheduleOnly != null) "Starts" else "When")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            LifeTextField(
                dateText, { dateText = it },
                placeholder = desktopText("Date"),
                isError = day == null || scheduleStartInvalid,
                trailing = { DatePickerButton(day?.let(LocalDate::ofEpochDay)) { dateText = format(it.toEpochDay()) } },
                modifier = Modifier.weight(1.5f),
            )
            if (scheduleOnly == null) LifeTextField(timeText, { timeText = it }, placeholder = desktopText("Time, e.g. 9:30"), isError = minute == null, modifier = Modifier.weight(1f))
        }
        day?.let { Text(formatDeadline(it, minute.takeIf { scheduleOnly == null }, snapshot, language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        FieldLabel("Merchant / payer")
        LifeTextField(merchant, { merchant = it }, placeholder = desktopText("Where or who"), modifier = Modifier.fillMaxWidth())
        FieldLabel("Note")
        LifeTextField(note, { note = it }, placeholder = desktopText("Optional"), minLines = 2, modifier = Modifier.fillMaxWidth())
        FieldLabel("Tags")
        LifeTextField(tags, { tags = it }, placeholder = desktopText("Comma separated, e.g. food, travel"), modifier = Modifier.fillMaxWidth())
        val typing = tags.substringAfterLast(',').trim().removePrefix("#")
        val chosen = parseTags(tags).map { it.lowercase() }.toSet()
        val suggestions = snapshot.ledgerEntries.flatMap { parseTags(it.tagsCsv) }.distinctBy { it.lowercase() }
            .filter { it.lowercase() !in chosen && (typing.isEmpty() || it.startsWith(typing, ignoreCase = true)) }
            .sortedBy { it.lowercase() }.take(8)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { suggestion ->
                    Pill("#$suggestion", false, {
                        val kept = tags.split(',').dropLast(1).map(String::trim).filter(String::isNotEmpty)
                        tags = (kept + suggestion).joinToString(", ") + ", "
                    })
                }
            }
        }
        FieldLabel("Repeat")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (scheduleOnly == null) Pill(desktopText("Never"), repeatUnit == null, { repeatUnit = null }, exclusive = true)
            RecurrenceUnit.entries.forEach { unit -> Pill(desktopText(recurrenceLabel(unit)), repeatUnit == unit, { repeatUnit = unit }, exclusive = true) }
        }
        if (repeatUnit != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(desktopText("Every"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LifeTextField(repeatInterval, { repeatInterval = it.filter(Char::isDigit).take(5) }, isError = interval == null || interval < 1, modifier = Modifier.width(72.dp))
                LifeTextField(
                    repeatEnd, { repeatEnd = it },
                    placeholder = desktopText("Until (optional)"),
                    isError = repeatEnd.isNotBlank() && (endDay == null || (day != null && endDay < day)),
                    trailing = { DatePickerButton(endDay?.let(LocalDate::ofEpochDay)) { repeatEnd = format(it.toEpochDay()) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (entry != null) {
            FieldLabel("Attachments") {
                TextButton(onClick = { chooseAndAttach(scope, store, AttachmentOwnerType.LEDGER, entry.id) }) {
                    Icon(Icons.Rounded.AttachFile, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(desktopText("Attach"))
                }
            }
            AttachmentList(snapshot, AttachmentOwnerType.LEDGER, entry.id, store)
        } else if (scheduleOnly == null) {
            Text(desktopText("Files can be attached after the first save."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(desktopText(it), color = LifeTheme.colors.danger) }
        Spacer(Modifier.height(Space.lg))
    }
    askScope?.let { value ->
        SeriesScopeDialog("Edit repeating entry", { askScope = null }) { chosen ->
            askScope = null
            commit(value, chosen)
        }
    }
}

/** The ledger editor in its own window, for the calendar. [entryId] null adds an entry on [epochDay]. */
@Composable
internal fun LedgerEditorWindow(snapshot: BackupSnapshot, store: DesktopDataStore, entryId: Long?, epochDay: Long, onClose: () -> Unit) {
    val scope = rememberSafeCoroutineScope()
    var deleteScopeFor by remember { mutableStateOf<LedgerEntryEntity?>(null) }
    androidx.compose.ui.window.DialogWindow(
        onCloseRequest = onClose,
        title = desktopText(if (entryId == null) "New entry" else "Edit entry"),
        state = androidx.compose.ui.window.rememberDialogState(size = androidx.compose.ui.unit.DpSize(520.dp, 760.dp)),
    ) {
        LedgerEditor(
            snapshot, store,
            entryId?.let { LedgerEditing.Entry(it) } ?: LedgerEditing.New(epochDay),
            onClose = onClose,
            onDelete = { entry ->
                if (entry.seriesId != null) deleteScopeFor = entry else {
                    scope.launch { store.deleteLedgerWithUndo(entry.id) }
                    onClose()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        deleteScopeFor?.let { entry ->
            SeriesScopeDialog("Delete repeating entry", { deleteScopeFor = null }) { chosen ->
                deleteScopeFor = null
                scope.launch { store.deleteLedgerWithUndo(entry.id, chosen) }
                onClose()
            }
        }
    }
}
