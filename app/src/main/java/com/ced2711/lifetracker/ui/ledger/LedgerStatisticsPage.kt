package com.ced2711.lifetracker.ui.ledger

import android.app.DatePickerDialog
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.domain.date.SmartDateParser
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.ui.adaptive.rememberHingeSafePlatformDialogLauncher
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max

private enum class StatisticsPeriod(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
    CUSTOM("Custom"),
}

internal const val MAX_LEDGER_TREND_POINTS = 120

internal data class TrendPoint(
    val incomeCents: Long,
    val expenseCents: Long,
    val axisLabel: String,
)

internal data class LedgerTrendResult(
    val points: List<TrendPoint>,
    val notice: String? = null,
)

internal data class TrendSelectionSummary(
    val label: String,
    val incomeText: String,
    val expensesText: String,
) {
    val accessibilityDescription: String
        get() = "$label. $incomeText. $expensesText."
}

@Composable
internal fun LedgerStatisticsPage(
    entries: List<LedgerEntryEntity>,
    isWide: Boolean,
    contentPadding: PaddingValues,
    formatting: LedgerDisplayFormatting,
) {
    val today = LocalDate.now()
    var periodName by rememberSaveable { mutableStateOf(StatisticsPeriod.MONTH.name) }
    val period = StatisticsPeriod.entries.firstOrNull { it.name == periodName } ?: StatisticsPeriod.MONTH
    var customStartEpochDay by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toEpochDay()) }
    var customEndEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var customStartInput by rememberSaveable {
        mutableStateOf(LocalDate.ofEpochDay(customStartEpochDay).format(LEDGER_SHORTCUT_DATE_FORMATTER))
    }
    var customEndInput by rememberSaveable {
        mutableStateOf(LocalDate.ofEpochDay(customEndEpochDay).format(LEDGER_SHORTCUT_DATE_FORMATTER))
    }
    val parsedCustomStart = SmartDateParser.parse(customStartInput, today)
    val parsedCustomEnd = SmartDateParser.parse(customEndInput, today)
    val customStart = LocalDate.ofEpochDay(customStartEpochDay)
    val customEnd = LocalDate.ofEpochDay(customEndEpochDay)
    val range = remember(period, customStart, customEnd, today, formatting.firstDayOfWeek) {
        statisticsRange(period, today, customStart, customEnd, formatting.firstDayOfWeek)
    }
    val filtered = remember(entries, range) {
        entries.filter { it.epochDay in range.first.toEpochDay()..range.second.toEpochDay() }
    }
    val income = filtered.filter { it.type == LedgerType.INCOME }.sumOf { it.amountCents }
    val expenseEntries = filtered.filter { it.type == LedgerType.EXPENSE }
    val expense = expenseEntries.sumOf { it.amountCents }
    val net = income - expense
    val days = ChronoUnit.DAYS.between(range.first, range.second).coerceAtLeast(0) + 1
    val largestExpense = expenseEntries.maxByOrNull { it.amountCents }
    val trend = remember(filtered, range, formatting.settings.dateFormat, formatting.locale) {
        trendPoints(
            entries = filtered,
            range = range,
            dateFormat = formatting.settings.dateFormat,
            locale = formatting.locale,
        )
    }
    val context = LocalContext.current
    val platformDialogLauncher = rememberHingeSafePlatformDialogLauncher()

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 980.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatisticsPeriod.entries.forEach { option ->
                        FilterChip(
                            selected = period == option,
                            onClick = { periodName = option.name },
                            label = { Text(option.label) },
                        )
                    }
                }
                if (period == StatisticsPeriod.CUSTOM) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val onPickStart = {
                            pickDate(customStart) {
                                val selected = minOf(it, customEnd)
                                customStartEpochDay = selected.toEpochDay()
                                customStartInput = selected.format(LEDGER_SHORTCUT_DATE_FORMATTER)
                            }
                        }
                        val onPickEnd = {
                            pickDate(customEnd) {
                                val selected = maxOf(it, customStart)
                                customEndEpochDay = selected.toEpochDay()
                                customEndInput = selected.format(LEDGER_SHORTCUT_DATE_FORMATTER)
                            }
                        }
                        if (maxWidth < 440.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatisticsDateField(
                                    label = "Start date",
                                    value = customStartInput,
                                    selectedDate = parsedCustomStart,
                                    formatting = formatting,
                                    onValueChange = { candidate ->
                                        customStartInput = candidate
                                        SmartDateParser.parse(candidate, today)?.let { selected ->
                                            customStartEpochDay = selected.toEpochDay()
                                        }
                                    },
                                    onOpenPicker = onPickStart,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                StatisticsDateField(
                                    label = "End date",
                                    value = customEndInput,
                                    selectedDate = parsedCustomEnd,
                                    formatting = formatting,
                                    onValueChange = { candidate ->
                                        customEndInput = candidate
                                        SmartDateParser.parse(candidate, today)?.let { selected ->
                                            customEndEpochDay = selected.toEpochDay()
                                        }
                                    },
                                    onOpenPicker = onPickEnd,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatisticsDateField(
                                    label = "Start date",
                                    value = customStartInput,
                                    selectedDate = parsedCustomStart,
                                    formatting = formatting,
                                    onValueChange = { candidate ->
                                        customStartInput = candidate
                                        SmartDateParser.parse(candidate, today)?.let { selected ->
                                            customStartEpochDay = selected.toEpochDay()
                                        }
                                    },
                                    onOpenPicker = onPickStart,
                                    modifier = Modifier.weight(1f),
                                )
                                StatisticsDateField(
                                    label = "End date",
                                    value = customEndInput,
                                    selectedDate = parsedCustomEnd,
                                    formatting = formatting,
                                    onValueChange = { candidate ->
                                        customEndInput = candidate
                                        SmartDateParser.parse(candidate, today)?.let { selected ->
                                            customEndEpochDay = selected.toEpochDay()
                                        }
                                    },
                                    onOpenPicker = onPickEnd,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "${formatting.date(range.first.toEpochDay())} – ${formatting.date(range.second.toEpochDay())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if ((isWide || maxWidth >= 700.dp) && maxWidth >= 620.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SummaryCard("Income", formatMoney(income), incomeColor(), Modifier.weight(1f))
                            SummaryCard("Expenses", formatMoney(expense), expenseColor(), Modifier.weight(1f))
                            SummaryCard(
                                "Net",
                                formatSignedMoney(net),
                                if (net >= 0) incomeColor() else expenseColor(),
                                Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryCard("Income", formatMoney(income), incomeColor())
                            SummaryCard("Expenses", formatMoney(expense), expenseColor())
                            SummaryCard(
                                "Net",
                                formatSignedMoney(net),
                                if (net >= 0) incomeColor() else expenseColor(),
                            )
                        }
                    }
                }
                TrendCard(points = trend.points)
                trend.notice?.let { notice ->
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IncomeExpenseRatio(income = income, expense = expense)
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if ((isWide || maxWidth >= 700.dp) && maxWidth >= 620.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DetailMetric(
                                title = "Largest expense",
                                value = largestExpense?.let { formatMoney(it.amountCents) } ?: "$0.00",
                                detail = largestExpense?.merchant?.ifBlank { largestExpense.note }
                                    ?.ifBlank { "No description" } ?: "No expenses",
                                modifier = Modifier.weight(1f),
                            )
                            DetailMetric(
                                title = "Average daily spending",
                                value = formatMoney(expense / days),
                                detail = "Across $days day${if (days == 1L) "" else "s"}",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailMetric(
                                title = "Largest expense",
                                value = largestExpense?.let { formatMoney(it.amountCents) } ?: "$0.00",
                                detail = largestExpense?.merchant?.ifBlank { largestExpense.note }
                                    ?.ifBlank { "No description" } ?: "No expenses",
                            )
                            DetailMetric(
                                title = "Average daily spending",
                                value = formatMoney(expense / days),
                                detail = "Across $days day${if (days == 1L) "" else "s"}",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsDateField(
    label: String,
    value: String,
    selectedDate: LocalDate?,
    formatting: LedgerDisplayFormatting,
    onValueChange: (String) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        placeholder = { Text("15, 8/15, or 8/15/2026") },
        singleLine = true,
        isError = selectedDate == null,
        trailingIcon = {
            IconButton(onClick = onOpenPicker) {
                Icon(Icons.Outlined.Event, contentDescription = "Choose $label")
            }
        },
        supportingText = {
            Text(
                selectedDate?.let { "Selected: ${formatting.date(it.toEpochDay())}" }
                    ?: "Enter a valid day, month/day, or month/day/year",
            )
        },
    )
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                value,
                color = color,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TrendCard(points: List<TrendPoint>) {
    val incomeColor = incomeColor()
    val expenseColor = expenseColor()
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionColor = MaterialTheme.colorScheme.primary
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val selectedSummary = selectedIndex?.let { trendSelectionSummary(points, it) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Trend", style = MaterialTheme.typography.titleSmall)
            if (points.all { it.incomeCents == 0L && it.expenseCents == 0L }) {
                Text(
                    "No activity in this period",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 48.dp),
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .semantics {
                            contentDescription = "Ledger income and expense trend"
                            stateDescription = selectedSummary?.accessibilityDescription
                                ?: "No period selected. Use the previous or next period action to inspect values."
                            customActions = listOf(
                                CustomAccessibilityAction("Previous period") {
                                    val previous = when (val current = selectedIndex) {
                                        null -> points.lastIndex
                                        0 -> return@CustomAccessibilityAction false
                                        else -> current - 1
                                    }
                                    selectedIndex = previous
                                    true
                                },
                                CustomAccessibilityAction("Next period") {
                                    val next = when (val current = selectedIndex) {
                                        null -> 0
                                        points.lastIndex -> return@CustomAccessibilityAction false
                                        else -> current + 1
                                    }
                                    selectedIndex = next
                                    true
                                },
                            )
                        }
                        .pointerInput(points) {
                            detectTapGestures(
                                onTap = { offset ->
                                    trendPointIndexAtX(
                                        x = offset.x,
                                        chartStartX = 54.dp.toPx().coerceAtMost(size.width * 0.34f),
                                        chartWidth = (
                                            size.width -
                                                54.dp.toPx().coerceAtMost(size.width * 0.34f) -
                                                4.dp.toPx()
                                            ).coerceAtLeast(1f),
                                        pointCount = points.size,
                                    )?.let { selectedIndex = it }
                                },
                                onLongPress = { offset ->
                                    trendPointIndexAtX(
                                        x = offset.x,
                                        chartStartX = 54.dp.toPx().coerceAtMost(size.width * 0.34f),
                                        chartWidth = (
                                            size.width -
                                                54.dp.toPx().coerceAtMost(size.width * 0.34f) -
                                                4.dp.toPx()
                                            ).coerceAtLeast(1f),
                                        pointCount = points.size,
                                    )?.let { selectedIndex = it }
                                },
                            )
                        },
                ) {
                    val rawMaximum = points.maxOf { max(it.incomeCents, it.expenseCents) }
                    val maximum = chartAxisMaximum(rawMaximum)
                    val axisWidth = 54.dp.toPx().coerceAtMost(size.width * 0.34f)
                    val rightPadding = 4.dp.toPx()
                    val topPadding = 8.dp.toPx()
                    val bottomAxisHeight = 24.dp.toPx()
                    val chartWidth = (size.width - axisWidth - rightPadding).coerceAtLeast(1f)
                    val chartHeight = (size.height - topPadding - bottomAxisHeight).coerceAtLeast(1f)
                    val baseline = topPadding + chartHeight
                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = axisTextColor.toArgb()
                        textSize = 10.sp.toPx()
                    }
                    listOf(maximum to 0f, maximum / 2 to 0.5f, 0L to 1f).forEach { (tick, fraction) ->
                        val y = topPadding + chartHeight * fraction
                        drawLine(
                            color = guideColor,
                            start = Offset(axisWidth, y),
                            end = Offset(axisWidth + chartWidth, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                        labelPaint.textAlign = Paint.Align.RIGHT
                        drawContext.canvas.nativeCanvas.drawText(
                            formatCompactAxisMoney(tick),
                            axisWidth - 6.dp.toPx(),
                            y + labelPaint.textSize * 0.35f,
                            labelPaint,
                        )
                    }
                    val groupWidth = chartWidth / points.size.coerceAtLeast(1)
                    val barWidth = (groupWidth * 0.28f).coerceAtMost(12.dp.toPx())
                    points.forEachIndexed { index, point ->
                        val center = axisWidth + groupWidth * index + groupWidth / 2f
                        if (index == selectedIndex) {
                            drawRect(
                                color = selectionColor.copy(alpha = 0.14f),
                                topLeft = Offset(axisWidth + groupWidth * index, topPadding),
                                size = Size(groupWidth, chartHeight),
                            )
                            drawLine(
                                color = selectionColor.copy(alpha = 0.75f),
                                start = Offset(center, topPadding),
                                end = Offset(center, baseline),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                        val incomeHeight = chartHeight * (point.incomeCents.toFloat() / maximum)
                        val expenseHeight = chartHeight * (point.expenseCents.toFloat() / maximum)
                        drawRect(
                            color = incomeColor,
                            topLeft = Offset(center - barWidth - 1.dp.toPx(), baseline - incomeHeight),
                            size = Size(barWidth, incomeHeight),
                        )
                        drawRect(
                            color = expenseColor,
                            topLeft = Offset(center + 1.dp.toPx(), baseline - expenseHeight),
                            size = Size(barWidth, expenseHeight),
                        )
                    }
                    val labelIndices = if (chartWidth < 180.dp.toPx() || points.size < 3) {
                        listOf(0, points.lastIndex)
                    } else {
                        listOf(0, points.lastIndex / 2, points.lastIndex)
                    }.distinct()
                    labelIndices.forEach { index ->
                        val center = axisWidth + groupWidth * index + groupWidth / 2f
                        labelPaint.textAlign = when (index) {
                            0 -> Paint.Align.LEFT
                            points.lastIndex -> Paint.Align.RIGHT
                            else -> Paint.Align.CENTER
                        }
                        val x = when (index) {
                            0 -> axisWidth
                            points.lastIndex -> axisWidth + chartWidth
                            else -> center
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            points[index].axisLabel,
                            x,
                            size.height - 2.dp.toPx(),
                            labelPaint,
                        )
                    }
                }
                selectedSummary?.let { summary ->
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        if (maxWidth < 360.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(summary.label, fontWeight = FontWeight.SemiBold)
                                Text(summary.incomeText, color = incomeColor)
                                Text(summary.expensesText, color = expenseColor)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(summary.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(summary.incomeText, color = incomeColor)
                                Text(summary.expensesText, color = expenseColor)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(incomeColor, "Income")
                    LegendDot(expenseColor, "Expenses")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(modifier = Modifier.padding(top = 5.dp).width(8.dp).height(8.dp)) {
            drawCircle(color = color, style = Stroke(width = size.minDimension / 2))
        }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun IncomeExpenseRatio(income: Long, expense: Long) {
    val total = income + expense
    val expenseRatio = if (total == 0L) 0f else expense.toFloat() / total
    val incomeRatio = if (total == 0L) 0f else income.toFloat() / total
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Income / expense", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${(incomeRatio * 100).toInt()}% / ${(expenseRatio * 100).toInt()}%")
            }
            LinearProgressIndicator(
                progress = { expenseRatio },
                modifier = Modifier.fillMaxWidth(),
                color = expenseColor(),
                trackColor = incomeColor().copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun DetailMetric(
    title: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

private fun statisticsRange(
    period: StatisticsPeriod,
    today: LocalDate,
    customStart: LocalDate,
    customEnd: LocalDate,
    firstDayOfWeek: DayOfWeek,
): Pair<LocalDate, LocalDate> = when (period) {
    StatisticsPeriod.WEEK -> {
        val firstDay = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        firstDay to firstDay.plusDays(6)
    }
    StatisticsPeriod.MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
    StatisticsPeriod.YEAR -> today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())
    StatisticsPeriod.CUSTOM -> minOf(customStart, customEnd) to maxOf(customStart, customEnd)
}

internal fun trendPoints(
    entries: List<LedgerEntryEntity>,
    range: Pair<LocalDate, LocalDate>,
    dateFormat: DateFormatOption,
    locale: Locale,
): LedgerTrendResult {
    val dayCount = ChronoUnit.DAYS.between(range.first, range.second) + 1
    if (dayCount <= 46) {
        val dayFormatter = statisticsDayAxisFormatter(dateFormat, locale)
        val grouped = entries.groupBy(LedgerEntryEntity::epochDay)
        return LedgerTrendResult(
            points = List(dayCount.toInt()) { offset ->
                val date = range.first.plusDays(offset.toLong())
                trendPoint(grouped[date.toEpochDay()].orEmpty(), date.format(dayFormatter))
            },
        )
    }

    val firstMonth = range.first.withDayOfMonth(1)
    val lastMonth = range.second.withDayOfMonth(1)
    val monthCount = ChronoUnit.MONTHS.between(firstMonth, lastMonth) + 1
    val spansMultipleYears = range.first.year != range.second.year
    val monthFormatter = statisticsMonthAxisFormatter(dateFormat, locale, spansMultipleYears)
    if (monthCount <= MAX_LEDGER_TREND_POINTS) {
        val grouped = entries.groupBy { entry ->
            val date = LocalDate.ofEpochDay(entry.epochDay)
            date.year * 12L + date.monthValue - 1L
        }
        return LedgerTrendResult(
            points = List(monthCount.toInt()) { offset ->
                val date = firstMonth.plusMonths(offset.toLong())
                val key = date.year * 12L + date.monthValue - 1L
                trendPoint(grouped[key].orEmpty(), date.format(monthFormatter))
            },
        )
    }

    val firstYear = range.first.year
    val yearCount = range.second.year - firstYear + 1
    val yearsPerBucket = (yearCount + MAX_LEDGER_TREND_POINTS - 1) / MAX_LEDGER_TREND_POINTS
    val bucketCount = (yearCount + yearsPerBucket - 1) / yearsPerBucket
    val grouped = entries.groupBy { entry ->
        (LocalDate.ofEpochDay(entry.epochDay).year - firstYear) / yearsPerBucket
    }
    return LedgerTrendResult(
        points = List(bucketCount) { index ->
            val startYear = firstYear + index * yearsPerBucket
            val endYear = minOf(startYear + yearsPerBucket - 1, range.second.year)
            val label = if (startYear == endYear) "$startYear" else "$startYear–$endYear"
            trendPoint(grouped[index].orEmpty(), label)
        },
        notice = if (yearsPerBucket == 1) {
            "Long range summarized by year to keep the chart readable."
        } else {
            "Long range summarized in $yearsPerBucket-year groups to keep the chart readable."
        }
    )
}

private fun statisticsDayAxisFormatter(
    dateFormat: DateFormatOption,
    locale: Locale,
): DateTimeFormatter {
    val fieldOrder = dateFieldOrder(dateFormat, locale)
    val dayBeforeMonth = fieldOrder.indexOf('d') < fieldOrder.indexOf('M')
    val separator = if (dateFormat == DateFormatOption.YEAR_MONTH_DAY) "-" else "/"
    return DateTimeFormatter.ofPattern(
        if (dayBeforeMonth) "d${separator}M" else "M${separator}d",
        locale,
    )
}

private fun statisticsMonthAxisFormatter(
    dateFormat: DateFormatOption,
    locale: Locale,
    includesYear: Boolean,
): DateTimeFormatter {
    if (!includesYear) return DateTimeFormatter.ofPattern("MMM", locale)
    val fieldOrder = dateFieldOrder(dateFormat, locale)
    val yearBeforeMonth = fieldOrder.indexOf('y') < fieldOrder.indexOf('M')
    return DateTimeFormatter.ofPattern(if (yearBeforeMonth) "yy MMM" else "MMM yy", locale)
}

private fun dateFieldOrder(dateFormat: DateFormatOption, locale: Locale): List<Char> = when (dateFormat) {
    DateFormatOption.MONTH_DAY_YEAR -> listOf('M', 'd', 'y')
    DateFormatOption.DAY_MONTH_YEAR -> listOf('d', 'M', 'y')
    DateFormatOption.YEAR_MONTH_DAY -> listOf('y', 'M', 'd')
    DateFormatOption.SYSTEM -> localizedDatePattern(locale).dateFieldOrder()
}

private fun localizedDatePattern(locale: Locale): String =
    DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        FormatStyle.SHORT,
        null,
        IsoChronology.INSTANCE,
        locale,
    )

private fun String.dateFieldOrder(): List<Char> {
    val fields = mutableListOf<Char>()
    var quoted = false
    forEach { character ->
        when {
            character == '\'' -> quoted = !quoted
            !quoted && character in "yYuUr" && 'y' !in fields -> fields += 'y'
            !quoted && character in "ML" && 'M' !in fields -> fields += 'M'
            !quoted && character == 'd' && 'd' !in fields -> fields += 'd'
        }
    }
    return fields
}

private fun trendPoint(entries: List<LedgerEntryEntity>, label: String): TrendPoint = TrendPoint(
    incomeCents = entries.filter { it.type == LedgerType.INCOME }.sumOf { it.amountCents },
    expenseCents = entries.filter { it.type == LedgerType.EXPENSE }.sumOf { it.amountCents },
    axisLabel = label,
)

internal fun chartAxisMaximum(maximumCents: Long): Long {
    if (maximumCents <= 0L) return 100L
    val padding = maximumCents / 10L + if (maximumCents % 10L == 0L) 0L else 1L
    return if (maximumCents > Long.MAX_VALUE - padding) {
        Long.MAX_VALUE
    } else {
        maximumCents + padding
    }
}

internal fun trendPointIndexAtX(
    x: Float,
    chartStartX: Float,
    chartWidth: Float,
    pointCount: Int,
): Int? {
    if (
        pointCount <= 0 ||
        !x.isFinite() ||
        !chartStartX.isFinite() ||
        !chartWidth.isFinite() ||
        chartWidth <= 0f ||
        x < chartStartX ||
        x > chartStartX + chartWidth
    ) {
        return null
    }
    if (pointCount == 1) return 0
    val fraction = ((x - chartStartX) / chartWidth).coerceIn(0f, 1f)
    return (fraction * pointCount).toInt().coerceAtMost(pointCount - 1)
}

internal fun trendSelectionSummary(
    points: List<TrendPoint>,
    index: Int,
): TrendSelectionSummary? = points.getOrNull(index)?.let { point ->
    TrendSelectionSummary(
        label = point.axisLabel,
        incomeText = "Income ${formatMoney(point.incomeCents)}",
        expensesText = "Expenses ${formatMoney(point.expenseCents)}",
    )
}

internal fun formatCompactAxisMoney(cents: Long): String = when {
    cents >= 100_000_000_000L -> String.format(Locale.US, "$%.1fB", cents / 100_000_000_000.0)
    cents >= 100_000_000L -> String.format(Locale.US, "$%.1fM", cents / 100_000_000.0)
    cents >= 100_000L -> String.format(Locale.US, "$%.1fK", cents / 100_000.0)
    cents % 100L == 0L -> "$${cents / 100L}"
    else -> String.format(Locale.US, "$%.2f", cents / 100.0)
}
