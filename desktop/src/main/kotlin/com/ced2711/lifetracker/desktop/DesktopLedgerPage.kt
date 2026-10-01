package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.domain.date.SmartDateParser
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.parseTags
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.launch

internal val IncomeColor = Color(0xFF65D28A)
internal val ExpenseColor = Color(0xFFFF756B)

/** The span of time the ledger page summarises. */
private enum class LedgerPeriod(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All") }

private data class PeriodRange(val start: Long, val end: Long)

private fun LedgerPeriod.range(anchor: LocalDate, firstDayOfWeek: DayOfWeek, entries: List<LedgerEntryEntity>): PeriodRange = when (this) {
    LedgerPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).let { PeriodRange(it.toEpochDay(), it.plusDays(6).toEpochDay()) }
    LedgerPeriod.MONTH -> YearMonth.from(anchor).let { PeriodRange(it.atDay(1).toEpochDay(), it.atEndOfMonth().toEpochDay()) }
    LedgerPeriod.YEAR -> PeriodRange(anchor.withDayOfYear(1).toEpochDay(), anchor.withDayOfYear(anchor.lengthOfYear()).toEpochDay())
    LedgerPeriod.ALL -> PeriodRange(entries.minOfOrNull { it.epochDay } ?: anchor.toEpochDay(), maxOf(entries.maxOfOrNull { it.epochDay } ?: anchor.toEpochDay(), anchor.toEpochDay()))
}

private fun LedgerPeriod.shift(anchor: LocalDate, steps: Long): LocalDate = when (this) {
    LedgerPeriod.WEEK -> anchor.plusWeeks(steps)
    LedgerPeriod.MONTH -> anchor.plusMonths(steps)
    LedgerPeriod.YEAR -> anchor.plusYears(steps)
    LedgerPeriod.ALL -> anchor
}

private fun periodTitle(period: LedgerPeriod, range: PeriodRange, snapshot: BackupSnapshot, language: UiLanguage): String {
    val locale = uiLocale(language)
    val start = LocalDate.ofEpochDay(range.start)
    return when (period) {
        LedgerPeriod.WEEK -> UserFormatting.formatDate(start, snapshot.settings.dateFormat, locale) + " – " +
            UserFormatting.formatDate(LocalDate.ofEpochDay(range.end), snapshot.settings.dateFormat, locale)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LedgerPage(snapshot: BackupSnapshot, store: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    val snackbar = remember { SnackbarHostState() }
    var period by remember { mutableStateOf(LedgerPeriod.MONTH) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var schedulesTab by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<LedgerEditing?>(null) }
    var deleteScopeFor by remember { mutableStateOf<LedgerEntryEntity?>(null) }
    var quickAmount by remember { mutableStateOf("") }
    var quickWhat by remember { mutableStateOf("") }
    var quickIncome by remember { mutableStateOf(false) }
    val quickFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    RegisterPageShortcuts(onNew = { editing = LedgerEditing.New(LocalDate.now().toEpochDay()) }, onFind = { runCatching { searchFocus.requestFocus() } })

    val live = snapshot.ledgerEntries.filter { it.deletedAt == null }
    val firstDay = UserFormatting.firstDayOfWeek(snapshot.settings.weekStart, locale)
    val range = period.range(anchor, firstDay, live)
    val inPeriod = live.filter { it.epochDay in range.start..range.end }
    val shown = inPeriod.filter { entry ->
        query.isBlank() || entry.merchant.contains(query, true) || entry.note.contains(query, true) ||
            parseTags(entry.tagsCsv).any { it.contains(query.trim().removePrefix("#"), true) }
    }.sortedWith(compareByDescending<LedgerEntryEntity> { it.epochDay }.thenByDescending { it.minuteOfDay }.thenByDescending { it.id })
    val income = inPeriod.filter { it.type == LedgerType.INCOME }.sumOf { it.amountCents }
    val expense = inPeriod.filter { it.type == LedgerType.EXPENSE }.sumOf { it.amountCents }
    val days = (minOf(range.end, LocalDate.now().toEpochDay()) - range.start + 1).coerceAtLeast(1)

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
                LedgerDraft(
                    type = if (quickIncome) LedgerType.INCOME else LedgerType.EXPENSE,
                    amountCents = cents,
                    epochDay = LocalDate.now().toEpochDay(),
                    minuteOfDay = now.hour * 60 + now.minute,
                    merchant = what,
                ),
                SeriesEditScope.ONLY_THIS_OCCURRENCE,
            )
        }
        quickAmount = ""
        quickWhat = ""
        runCatching { quickFocus.requestFocus() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val sidePanel = maxWidth >= 1_000.dp
            val current = editing
            if (current != null && !sidePanel) {
                LedgerEditor(snapshot, store, current, onClose = { editing = null }, onDelete = ::requestDelete, modifier = Modifier.fillMaxSize())
                return@BoxWithConstraints
            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(desktopText("Ledger"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(20.dp))
                    FilterChipSimple(desktopText("Entries"), !schedulesTab) { schedulesTab = false }
                    Spacer(Modifier.width(6.dp))
                    FilterChipSimple(desktopText("Recurring"), schedulesTab) { schedulesTab = true }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { editing = LedgerEditing.New(LocalDate.now().toEpochDay()) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(desktopText("New entry")) }
                }
                if (schedulesTab) {
                    Row(Modifier.fillMaxSize()) {
                        LedgerSchedules(snapshot, store, onEdit = { editing = LedgerEditing.Schedule(it) }, modifier = Modifier.weight(1f).fillMaxHeight())
                        if (current != null) {
                            VerticalDivider()
                            LedgerEditor(snapshot, store, current, onClose = { editing = null }, onDelete = ::requestDelete, modifier = Modifier.width(440.dp).fillMaxHeight())
                        }
                    }
                    return@Column
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (period != LedgerPeriod.ALL) IconButton(onClick = { anchor = period.shift(anchor, -1) }) { Icon(Icons.Default.ChevronLeft, desktopText("Previous")) }
                    Text(periodTitle(period, range, snapshot, language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (period != LedgerPeriod.ALL) IconButton(onClick = { anchor = period.shift(anchor, 1) }) { Icon(Icons.Default.ChevronRight, desktopText("Next")) }
                    TextButton(onClick = { anchor = LocalDate.now() }) { Text(desktopText("Today")) }
                    Spacer(Modifier.weight(1f))
                    LedgerPeriod.entries.forEach { item ->
                        FilterChipSimple(desktopText(item.label), period == item) { period = item }
                        Spacer(Modifier.width(6.dp))
                    }
                }
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(desktopText("Income"), formatMoney(income), IncomeColor)
                    StatCard(desktopText("Expense"), formatMoney(expense), ExpenseColor)
                    StatCard(desktopText("Net"), (if (income >= expense) "+" else "−") + formatMoney(kotlin.math.abs(income - expense)), if (income >= expense) IncomeColor else ExpenseColor)
                    StatCard(desktopText("Average daily spending"), formatMoney(expense / days), MaterialTheme.colorScheme.onSurface)
                    inPeriod.filter { it.type == LedgerType.EXPENSE }.maxByOrNull { it.amountCents }?.let { largest ->
                        StatCard(desktopText("Largest expense"), formatMoney(largest.amountCents), MaterialTheme.colorScheme.onSurface, entryLabel(largest))
                    }
                }
                Row(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChipSimple(desktopText(if (quickIncome) "Income" else "Expense"), true) { quickIncome = !quickIncome }
                            OutlinedTextField(
                                quickAmount, { if (isValidDesktopAmountInput(it)) quickAmount = it },
                                placeholder = { Text(desktopText("Amount")) }, singleLine = true,
                                modifier = Modifier.width(140.dp).focusRequester(quickFocus).onEnter(::addQuick),
                            )
                            OutlinedTextField(
                                quickWhat, { quickWhat = it },
                                placeholder = { Text(desktopText("Where or what, then Enter")) }, singleLine = true,
                                modifier = Modifier.weight(1f).onEnter(::addQuick),
                            )
                            OutlinedTextField(
                                query, { query = it },
                                placeholder = { Text(desktopText("Search (Ctrl+F)")) },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true, modifier = Modifier.width(220.dp).focusRequester(searchFocus),
                            )
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (shown.isEmpty()) item { Text(desktopText("No entries in this period."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp)) }
                            shown.groupBy { it.epochDay }.forEach { (day, rows) ->
                                item(key = "day-$day") {
                                    val net = rows.sumOf { if (it.type == LedgerType.INCOME) it.amountCents else -it.amountCents }
                                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(formatDeadline(day, null, snapshot, language), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                        Text((if (net >= 0) "+" else "−") + formatMoney(kotlin.math.abs(net)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                items(rows, key = { it.id }) { entry ->
                                    LedgerRow(entry, snapshot, selected = editing == LedgerEditing.Entry(entry.id)) { editing = LedgerEditing.Entry(entry.id) }
                                }
                            }
                        }
                    }
                    if (sidePanel) {
                        VerticalDivider()
                        if (current != null) {
                            LedgerEditor(snapshot, store, current, onClose = { editing = null }, onDelete = ::requestDelete, modifier = Modifier.width(440.dp).fillMaxHeight())
                        } else {
                            LedgerInsights(snapshot, period, range, inPeriod, Modifier.width(440.dp).fillMaxHeight())
                        }
                    }
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

private fun entryLabel(entry: LedgerEntryEntity): String =
    entry.merchant.ifBlank { entry.note.ifBlank { if (entry.type == LedgerType.INCOME) "Income" else "Expense" } }

@Composable
private fun StatCard(title: String, value: String, color: Color, detail: String? = null) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(210.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            if (detail != null) Text(desktopText(detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LedgerRow(entry: LedgerEntryEntity, snapshot: BackupSnapshot, selected: Boolean, onClick: () -> Unit) {
    val language = LocalUiLanguage.current
    val attachments = snapshot.attachments.count { it.ownerType == AttachmentOwnerType.LEDGER && it.ownerId == entry.id && it.pendingDeleteAt == null }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                UserFormatting.formatMinuteOfDay(entry.minuteOfDay, snapshot.settings.timeFormat, false, uiLocale(language)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(desktopText(entryLabel(entry)), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.seriesId != null) Icon(Icons.Default.Repeat, desktopText("Repeats"), Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (attachments > 0) Icon(Icons.Default.AttachFile, null, Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val detail = listOf(entry.note.takeIf { entry.merchant.isNotBlank() }.orEmpty(), parseTags(entry.tagsCsv).joinToString(" ") { "#$it" }).filter(String::isNotBlank).joinToString("  ")
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                (if (entry.type == LedgerType.INCOME) "+" else "−") + formatMoney(entry.amountCents),
                color = if (entry.type == LedgerType.INCOME) IncomeColor else ExpenseColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Right column when nothing is open: the period's daily (or monthly) net and where money went. */
@Composable
private fun LedgerInsights(snapshot: BackupSnapshot, period: LedgerPeriod, range: PeriodRange, entries: List<LedgerEntryEntity>, modifier: Modifier) {
    val language = LocalUiLanguage.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(desktopText(if (period == LedgerPeriod.WEEK || period == LedgerPeriod.MONTH) "Daily net" else "Monthly net"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val buckets: List<Pair<String, Long>> = if (period == LedgerPeriod.WEEK || period == LedgerPeriod.MONTH) {
            (range.start..range.end).map { day ->
                val date = LocalDate.ofEpochDay(day)
                val label = if (period == LedgerPeriod.WEEK) date.dayOfWeek.getDisplayName(TextStyle.SHORT, uiLocale(language)) else date.dayOfMonth.toString()
                label to entries.filter { it.epochDay == day }.sumOf { if (it.type == LedgerType.INCOME) it.amountCents else -it.amountCents }
            }
        } else {
            entries.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }.toSortedMap().map { (month, rows) ->
                (if (period == LedgerPeriod.YEAR) month.monthValue.toString() else "${month.year % 100}/${month.monthValue}") to
                    rows.sumOf { if (it.type == LedgerType.INCOME) it.amountCents else -it.amountCents }
            }
        }
        NetBars(buckets, Modifier.fillMaxWidth().height(180.dp))

        val expenses = entries.filter { it.type == LedgerType.EXPENSE }
        val total = expenses.sumOf { it.amountCents }
        if (total > 0) {
            Text(desktopText("Spending by tag"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            val byTag = expenses.flatMap { entry -> parseTags(entry.tagsCsv).ifEmpty { listOf("") }.map { it to entry.amountCents } }
                .groupBy({ it.first.lowercase() }, { it.second }).mapValues { it.value.sum() }
                .entries.sortedByDescending { it.value }.take(8)
            byTag.forEach { (tag, cents) ->
                Column {
                    Row {
                        Text(if (tag.isEmpty()) desktopText("Untagged") else "#$tag", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(formatMoney(cents), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth((cents.toFloat() / total).coerceIn(0.02f, 1f)).height(6.dp).background(ExpenseColor, RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun NetBars(buckets: List<Pair<String, Long>>, modifier: Modifier) {
    if (buckets.isEmpty() || buckets.all { it.second == 0L }) {
        Box(modifier, contentAlignment = Alignment.Center) { Text(desktopText("No ledger data yet"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    val maximum = buckets.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(1L)
    val axis = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val slot = size.width / buckets.size
            val center = size.height / 2f
            drawLine(axis, Offset(0f, center), Offset(size.width, center))
            buckets.forEachIndexed { index, (_, value) ->
                if (value == 0L) return@forEachIndexed
                val height = (kotlin.math.abs(value).toFloat() / maximum) * (center - 4f)
                drawRect(
                    if (value > 0) IncomeColor else ExpenseColor,
                    topLeft = Offset(index * slot + slot * 0.18f, if (value > 0) center - height else center),
                    size = Size(slot * 0.64f, height.coerceAtLeast(2f)),
                )
            }
        }
        // Label only every few bars so a month stays readable.
        val every = (buckets.size / 10).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth()) {
            buckets.chunked(every).forEach { group ->
                Text(group.first().first, modifier = Modifier.weight(group.size.toFloat()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Recurring schedules: what repeats, how often, and Stop / Remove / Change. */
@Composable
private fun LedgerSchedules(snapshot: BackupSnapshot, store: DesktopDataStore, onEdit: (Long) -> Unit, modifier: Modifier) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    var confirmStop by remember { mutableStateOf<LedgerSeriesEntity?>(null) }
    var confirmRemove by remember { mutableStateOf<LedgerSeriesEntity?>(null) }
    val series = snapshot.ledgerSeries.sortedWith(compareByDescending<LedgerSeriesEntity> { it.active }.thenBy { it.startEpochDay })
    LazyColumn(modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (series.isEmpty()) item { Text(desktopText("No recurring entries. Choose Repeat when adding an entry."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp)) }
        items(series, key = { it.id }) { item ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(desktopText(item.merchant.ifBlank { item.note.ifBlank { if (item.type == LedgerType.INCOME) "Income" else "Expense" } }), fontWeight = FontWeight.SemiBold)
                        Text(scheduleText(item, snapshot, language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!item.active) Text(desktopText("Stopped"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        (if (item.type == LedgerType.INCOME) "+" else "−") + formatMoney(item.amountCents),
                        color = if (item.type == LedgerType.INCOME) IncomeColor else ExpenseColor, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    if (item.active) {
                        TextButton(onClick = { onEdit(item.id) }) { Text(desktopText("Change")) }
                        TextButton(onClick = { confirmStop = item }) { Text(desktopText("Stop")) }
                    } else {
                        TextButton(onClick = { confirmRemove = item }) { Text(desktopText("Remove"), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
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
    val startDay = when (editing) {
        is LedgerEditing.New -> editing.epochDay
        is LedgerEditing.Entry -> entry!!.epochDay
        // A schedule changes from tomorrow (or its later start) on.
        is LedgerEditing.Schedule -> maxOf(today.plusDays(1).toEpochDay(), scheduleOnly?.startEpochDay ?: 0L)
    }
    var type by remember(editing) { mutableStateOf(entry?.type ?: series?.type ?: LedgerType.EXPENSE) }
    var amount by remember(editing) { mutableStateOf((entry?.amountCents ?: series?.amountCents)?.let { "%.2f".format(Locale.US, it / 100.0) }.orEmpty()) }
    var dateText by remember(editing) { mutableStateOf(UserFormatting.formatDate(LocalDate.ofEpochDay(startDay), snapshot.settings.dateFormat, locale)) }
    var timeText by remember(editing) {
        val minute = entry?.minuteOfDay ?: LocalTime.now().let { it.hour * 60 + it.minute }
        mutableStateOf("%d:%02d".format(minute / 60, minute % 60))
    }
    var merchant by remember(editing) { mutableStateOf(entry?.merchant ?: series?.merchant.orEmpty()) }
    var note by remember(editing) { mutableStateOf(entry?.note ?: series?.note.orEmpty()) }
    var tags by remember(editing) { mutableStateOf((entry?.tagsCsv ?: series?.tagsCsv.orEmpty()).replace(",", ", ")) }
    var repeatUnit by remember(editing) { mutableStateOf(series?.recurrenceUnit) }
    var repeatInterval by remember(editing) { mutableStateOf((series?.intervalCount ?: 1).toString()) }
    var repeatEnd by remember(editing) { mutableStateOf(series?.endEpochDay?.let { UserFormatting.formatDate(LocalDate.ofEpochDay(it), snapshot.settings.dateFormat, locale) }.orEmpty()) }
    var askScope by remember(editing) { mutableStateOf<LedgerDraft?>(null) }
    var error by remember(editing) { mutableStateOf<String?>(null) }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(editing) { runCatching { amountFocus.requestFocus() } }

    val cents = parseAmountCents(amount)
    val day = SmartDateParser.parse(dateText, today)?.toEpochDay()
    val minute = parseTimeOfDay(timeText)
    val interval = repeatInterval.toIntOrNull()
    val endDay = repeatEnd.takeIf(String::isNotBlank)?.let { SmartDateParser.parse(it, today)?.toEpochDay() }
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

    Column(modifier.background(MaterialTheme.colorScheme.surface).editorKeys(onSave = ::save, onCancel = onClose)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                desktopText(when { scheduleOnly != null -> "Change schedule"; entry == null -> "New entry"; else -> "Edit entry" }),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, desktopText("Close (Esc)")) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (scheduleOnly != null) Text(desktopText("Entries already created stay as they are. The changed schedule starts on the date below."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChipSimple(desktopText("Expense"), type == LedgerType.EXPENSE) { type = LedgerType.EXPENSE }
                FilterChipSimple(desktopText("Income"), type == LedgerType.INCOME) { type = LedgerType.INCOME }
            }
            OutlinedTextField(
                amount, { if (isValidDesktopAmountInput(it)) amount = it },
                label = { Text(desktopText("Amount")) }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = if (type == LedgerType.INCOME) IncomeColor else ExpenseColor),
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(dateText, { dateText = it }, label = { Text(desktopText(if (scheduleOnly != null) "Starts" else "Date")) }, isError = day == null || scheduleStartInvalid, singleLine = true, modifier = Modifier.weight(1.4f),
                    supportingText = { day?.let { Text(formatDeadline(it, null, snapshot, language)) } })
                if (scheduleOnly == null) OutlinedTextField(timeText, { timeText = it }, label = { Text(desktopText("Time")) }, isError = minute == null, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(merchant, { merchant = it }, label = { Text(desktopText("Merchant / payer")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text(desktopText("Note")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tags, { tags = it }, label = { Text(desktopText("Tags, comma separated (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(desktopText("Repeat"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (scheduleOnly == null) FilterChipSimple(desktopText("Never"), repeatUnit == null) { repeatUnit = null }
                RecurrenceUnit.entries.forEach { unit -> FilterChipSimple(desktopText(recurrenceLabel(unit)), repeatUnit == unit) { repeatUnit = unit } }
            }
            if (repeatUnit != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(repeatInterval, { repeatInterval = it.filter(Char::isDigit).take(5) }, label = { Text(desktopText("Every")) }, singleLine = true, modifier = Modifier.weight(1f), isError = interval == null || interval < 1)
                OutlinedTextField(repeatEnd, { repeatEnd = it }, label = { Text(desktopText("Until (optional)")) }, singleLine = true, modifier = Modifier.weight(2f), isError = repeatEnd.isNotBlank() && (endDay == null || (day != null && endDay < day)))
            }
            if (entry != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(desktopText("Attachments"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    IconButton({ chooseAndAttach(scope, store, AttachmentOwnerType.LEDGER, entry.id) }) { Icon(Icons.Default.AttachFile, desktopText("Attach")) }
                }
                AttachmentList(snapshot, AttachmentOwnerType.LEDGER, entry.id, store)
            }
            error?.let { Text(desktopText(it), color = MaterialTheme.colorScheme.error) }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry != null) TextButton(onClick = { onDelete(entry) }) { Text(desktopText("Delete"), color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text(desktopText("Cancel")) }
            Button(enabled = canSave, onClick = ::save) { Text(desktopText("Save (Ctrl+S)")) }
        }
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
        state = androidx.compose.ui.window.rememberDialogState(size = androidx.compose.ui.unit.DpSize(520.dp, 720.dp)),
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
