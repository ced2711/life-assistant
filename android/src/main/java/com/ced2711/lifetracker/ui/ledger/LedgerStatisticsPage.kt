package com.ced2711.lifetracker.ui.ledger

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.MoneyTotals
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.parseTags
import com.ced2711.lifetracker.ui.design.Dot
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.design.Stat
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import kotlin.math.max

/**
 * The Statistics page: the period and its totals ([header]), then the period as a picture: the
 * trend, the share of income and expense, the largest expense and the daily average, and where
 * the money went by tag.
 */
@Composable
internal fun LedgerStatisticsPage(
    periodEntries: List<LedgerEntryEntity>,
    range: LedgerRange,
    totals: MoneyTotals,
    formatting: LedgerDisplayFormatting,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val twoColumns = maxWidth >= 700.dp
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Space.lg, end = Space.lg, top = Space.xs, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            header()
            LedgerTrendPanel(periodEntries, range, formatting)
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        LedgerRatioPanel(totals)
                        LedgerHighlightsPanel(periodEntries, range, totals)
                    }
                    LedgerTagPanel(periodEntries, Modifier.weight(1f))
                }
            } else {
                LedgerRatioPanel(totals)
                LedgerHighlightsPanel(periodEntries, range, totals)
                LedgerTagPanel(periodEntries)
            }
        }
    }
}

@Composable
private fun PanelTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = modifier.semantics { heading() })
}

@Composable
private fun QuietLine(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Income and expense over the period as bars; tap a bar (or use the accessibility actions) for its numbers. */
@Composable
internal fun LedgerTrendPanel(
    periodEntries: List<LedgerEntryEntity>,
    range: LedgerRange,
    formatting: LedgerDisplayFormatting,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 176.dp,
) {
    val language = LocalUiLanguage.current
    val trend = remember(periodEntries, range, formatting.settings.dateFormat, formatting.locale) {
        trendPoints(
            entries = periodEntries,
            range = LocalDate.ofEpochDay(range.start) to LocalDate.ofEpochDay(range.end),
            dateFormat = formatting.settings.dateFormat,
            locale = formatting.locale,
        )
    }
    val points = trend.points
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val selected = selectedIndex?.let { trendSelectionSummary(points, it) }
    val selectedText = selected?.let { summary ->
        summary.label + " · " + localizedText(summary.incomeText) + " · " + localizedText(summary.expensesText)
    }
    val selectedDescription = selected?.let { summary ->
        "${summary.label}. ${localizedText(summary.incomeText)}. ${localizedText(summary.expensesText)}."
    }
    Panel(modifier.fillMaxWidth(), padding = PaddingValues(Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTitle(localizedText("Trend"), Modifier.weight(1f))
            LegendDot(LifeTheme.colors.income, localizedText("Income"))
            Spacer(Modifier.width(Space.md))
            LegendDot(LifeTheme.colors.expense, localizedText("Expense"))
        }
        if (points.all { it.incomeCents == 0L && it.expenseCents == 0L }) {
            Box(Modifier.fillMaxWidth().height(chartHeight * 0.6f), contentAlignment = Alignment.Center) {
                QuietLine(localizedText("No activity in this period"))
            }
        } else {
            Text(
                selectedText ?: localizedText("Tap a bar for its numbers"),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = Space.md),
            )
            TrendChart(
                points = points,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
                stateText = selectedDescription
                    ?: localizedText("No period selected. Use the previous or next period action to inspect values."),
                modifier = Modifier.fillMaxWidth().height(chartHeight),
            )
        }
        trend.notice?.let { notice ->
            Text(
                trendNoticeText(notice, language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.sm),
            )
        }
    }
}

/** The note under a chart that had to group a very long range. */
private fun trendNoticeText(notice: String, language: UiLanguage): String {
    if (language == UiLanguage.ENGLISH) return notice
    val years = Regex("""in (\d+)-year groups""").find(notice)?.groupValues?.get(1)
    return if (years != null) "时间范围很长，已按每 $years 年汇总，方便查看。" else "时间范围很长，已按年汇总，方便查看。"
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Where the bars start, leaving room for the money axis on the left. */
private fun chartStartX(width: Float, axisWidth: Float): Float = axisWidth.coerceAtMost(width * 0.34f)

@Composable
private fun TrendChart(
    points: List<TrendPoint>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    stateText: String,
    modifier: Modifier = Modifier,
) {
    val incomeColor = LifeTheme.colors.income
    val expenseColor = LifeTheme.colors.expense
    val gridColor = LifeTheme.colors.divider
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val chartDescription = localizedText("Ledger income and expense trend")
    val previousLabel = localizedText("Previous period")
    val nextLabel = localizedText("Next period")
    // The axis labels follow the font size, up to a point, so they stay readable but never crowd the bars.
    val labelSize = (11 * LocalDensity.current.fontScale.coerceAtMost(1.3f)).sp
    val axisWidth = (48 * LocalDensity.current.fontScale.coerceAtMost(1.3f)).dp
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = chartDescription
                stateDescription = stateText
                customActions = listOf(
                    CustomAccessibilityAction(previousLabel) {
                        val previous = when (selectedIndex) {
                            null -> points.lastIndex
                            0 -> return@CustomAccessibilityAction false
                            else -> selectedIndex - 1
                        }
                        onSelect(previous)
                        true
                    },
                    CustomAccessibilityAction(nextLabel) {
                        val next = when (selectedIndex) {
                            null -> 0
                            points.lastIndex -> return@CustomAccessibilityAction false
                            else -> selectedIndex + 1
                        }
                        onSelect(next)
                        true
                    },
                )
            }
            .pointerInput(points) {
                fun pick(offset: Offset) {
                    val start = chartStartX(size.width.toFloat(), axisWidth.toPx())
                    trendPointIndexAtX(
                        x = offset.x,
                        chartStartX = start,
                        chartWidth = (size.width - start - 4.dp.toPx()).coerceAtLeast(1f),
                        pointCount = points.size,
                    )?.let(onSelect)
                }
                detectTapGestures(onTap = ::pick, onLongPress = ::pick)
            },
    ) {
        val maximum = chartAxisMaximum(points.maxOf { max(it.incomeCents, it.expenseCents) })
        val startX = chartStartX(size.width, axisWidth.toPx())
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = axisTextColor.toArgb()
            textSize = labelSize.toPx()
        }
        val topPadding = 6.dp.toPx()
        val bottomAxisHeight = labelPaint.textSize + 10.dp.toPx()
        val chartWidth = (size.width - startX - 4.dp.toPx()).coerceAtLeast(1f)
        val chartHeight = (size.height - topPadding - bottomAxisHeight).coerceAtLeast(1f)
        val baseline = topPadding + chartHeight
        val groupWidth = chartWidth / points.size.coerceAtLeast(1)

        selectedIndex?.takeIf { it in points.indices }?.let { index ->
            drawRoundRect(
                color = highlight,
                topLeft = Offset(startX + groupWidth * index, topPadding),
                size = Size(groupWidth, chartHeight),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
        // Above $20 the middle label is cut to whole dollars so it stays short.
        val middle = (maximum / 2).let { if (it >= 2_000L) it / 100L * 100L else it }
        listOf(maximum to 0f, middle to 0.5f, 0L to 1f).forEach { (tick, fraction) ->
            val y = topPadding + chartHeight * fraction
            drawLine(gridColor, Offset(startX, y), Offset(startX + chartWidth, y), strokeWidth = 1.dp.toPx())
            labelPaint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(
                formatCompactAxisMoney(tick),
                startX - 6.dp.toPx(),
                y + labelPaint.textSize * 0.35f,
                labelPaint,
            )
        }
        val barWidth = (groupWidth * 0.3f).coerceIn(1f, 10.dp.toPx())
        val gap = (groupWidth * 0.06f).coerceAtMost(2.dp.toPx())
        points.forEachIndexed { index, point ->
            val left = startX + groupWidth * index + (groupWidth - (barWidth * 2 + gap)) / 2f
            fun bar(value: Long, x: Float, color: Color) {
                if (value <= 0L) return
                val height = (chartHeight * (value.toFloat() / maximum)).coerceAtLeast(2.dp.toPx())
                drawRoundRect(color, Offset(x, baseline - height), Size(barWidth, height), CornerRadius(barWidth / 3f))
            }
            bar(point.incomeCents, left, incomeColor)
            bar(point.expenseCents, left + barWidth + gap, expenseColor)
        }
        // First, middle and last label only, so a month of days stays readable.
        val labelIndices = if (chartWidth < 180.dp.toPx() || points.size < 3) {
            listOf(0, points.lastIndex)
        } else {
            listOf(0, points.lastIndex / 2, points.lastIndex)
        }.distinct()
        labelIndices.forEach { index ->
            labelPaint.textAlign = when (index) {
                0 -> Paint.Align.LEFT
                points.lastIndex -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            val x = when (index) {
                0 -> startX
                points.lastIndex -> startX + chartWidth
                else -> startX + groupWidth * index + groupWidth / 2f
            }
            drawContext.canvas.nativeCanvas.drawText(points[index].axisLabel, x, size.height - 2.dp.toPx(), labelPaint)
        }
    }
}

/** How much of the money that moved was income, and how much expense. */
@Composable
internal fun LedgerRatioPanel(totals: MoneyTotals, modifier: Modifier = Modifier) {
    val language = LocalUiLanguage.current
    Panel(modifier.fillMaxWidth(), padding = PaddingValues(Space.lg)) {
        PanelTitle(localizedText("Income and expense"))
        val sum = totals.incomeCents + totals.expenseCents
        if (sum == 0L) {
            QuietLine(localizedText("No activity in this period"), Modifier.padding(top = Space.sm))
        } else {
            val share = totals.incomeCents.toFloat() / sum
            val incomePercent = (share * 100).toInt()
            Row(Modifier.fillMaxWidth().padding(top = Space.md).height(10.dp).clip(CircleShape)) {
                if (share > 0f) Box(Modifier.weight(share.coerceAtLeast(0.01f)).fillMaxHeight().background(LifeTheme.colors.income))
                if (share < 1f) Box(Modifier.weight((1f - share).coerceAtLeast(0.01f)).fillMaxHeight().background(LifeTheme.colors.expense))
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.sm)) {
                Text(
                    ledgerSharePercent(incomePercent, "Income", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = LifeTheme.colors.income,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    ledgerSharePercent(100 - incomePercent, "Expense", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = LifeTheme.colors.expense,
                )
            }
        }
    }
}

/** The largest expense of the period and what was spent per day on average. */
@Composable
internal fun LedgerHighlightsPanel(
    periodEntries: List<LedgerEntryEntity>,
    range: LedgerRange,
    totals: MoneyTotals,
    modifier: Modifier = Modifier,
) {
    val language = LocalUiLanguage.current
    val largest = periodEntries.filter { it.type == LedgerType.EXPENSE }.maxByOrNull { it.amountCents }
    // Days that have not happened yet do not lower the average.
    val days = (minOf(range.end, LocalDate.now().toEpochDay()) - range.start + 1).coerceAtLeast(1)
    Panel(modifier.fillMaxWidth(), padding = PaddingValues(Space.lg)) {
        BoxWithConstraints {
            val largestStat: @Composable (Modifier) -> Unit = { statModifier ->
                Stat(
                    label = localizedText("Largest expense"),
                    value = largest?.let { formatMoney(it.amountCents) } ?: "—",
                    modifier = statModifier,
                    caption = largest?.let { entry ->
                        entry.merchant.ifBlank { entry.note }.ifBlank { localizedText("No description") }
                    } ?: localizedText("No expenses"),
                    valueStyle = MaterialTheme.typography.titleLarge,
                )
            }
            val averageStat: @Composable (Modifier) -> Unit = { statModifier ->
                Stat(
                    label = localizedText("Average daily spending"),
                    value = formatMoney(totals.expenseCents / days),
                    modifier = statModifier,
                    caption = ledgerAcrossDays(days, language),
                    valueStyle = MaterialTheme.typography.titleLarge,
                )
            }
            if (maxWidth < 300.dp * LocalDensity.current.fontScale) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    largestStat(Modifier.fillMaxWidth())
                    averageStat(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                    largestStat(Modifier.weight(1f))
                    averageStat(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Where the money went: the period's expenses by tag, largest first. */
@Composable
internal fun LedgerTagPanel(periodEntries: List<LedgerEntryEntity>, modifier: Modifier = Modifier, limit: Int = 8) {
    Panel(modifier.fillMaxWidth(), padding = PaddingValues(Space.lg)) {
        PanelTitle(localizedText("Spending by tag"))
        val expenses = periodEntries.filter { it.type == LedgerType.EXPENSE }
        val total = expenses.sumOf { it.amountCents }
        if (total == 0L) {
            QuietLine(localizedText("No expenses in this period"), Modifier.padding(top = Space.sm))
        } else {
            val byTag = remember(expenses) {
                expenses.flatMap { entry -> parseTags(entry.tagsCsv).ifEmpty { listOf("") }.map { it to entry.amountCents } }
                    .groupBy({ it.first.lowercase() }, { it.second }).mapValues { it.value.sum() }
                    .entries.sortedByDescending { it.value }.take(limit)
            }
            byTag.forEach { (tag, cents) ->
                Column(Modifier.padding(top = Space.md)) {
                    Row {
                        Text(
                            if (tag.isEmpty()) localizedText("Untagged") else "#$tag",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        Text(
                            formatMoney(cents),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Box(
                            Modifier.fillMaxWidth((cents.toFloat() / total).coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape)
                                .background(LifeTheme.colors.expense),
                        )
                    }
                }
            }
        }
    }
}
