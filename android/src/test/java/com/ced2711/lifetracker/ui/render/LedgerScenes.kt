package com.ced2711.lifetracker.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.ui.ledger.LedgerContent
import com.ced2711.lifetracker.ui.ledger.LedgerDisplayFormatting
import com.ced2711.lifetracker.ui.ledger.LedgerEditorUi
import com.ced2711.lifetracker.ui.ledger.LedgerPeriod
import com.ced2711.lifetracker.ui.ledger.LedgerRuleEditorUi
import com.ced2711.lifetracker.ui.ledger.LedgerTab
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import kotlinx.coroutines.flow.flowOf

/** More ledger data than RenderSamples has: a few weeks of ordinary spending and two schedules. */
private object LedgerSamples {
    private val day = RenderSamples.today.toEpochDay()

    val entries: List<LedgerEntryEntity> = RenderSamples.ledger + listOf(
        LedgerEntryEntity(id = 7, type = LedgerType.EXPENSE, amountCents = 1_899, epochDay = day - 1, minuteOfDay = 21 * 60 + 10, merchant = "Streaming", note = "Monthly plan", tagsCsv = "subscriptions", seriesId = 2),
        LedgerEntryEntity(id = 8, type = LedgerType.EXPENSE, amountCents = 4_650, epochDay = day - 3, minuteOfDay = 18 * 60 + 30, merchant = "Trattoria Roma", note = "Dinner with Ana", tagsCsv = "food,friends"),
        LedgerEntryEntity(id = 9, type = LedgerType.INCOME, amountCents = 12_000, epochDay = day - 5, minuteOfDay = 10 * 60, note = "Sold the old bike"),
        LedgerEntryEntity(id = 10, type = LedgerType.EXPENSE, amountCents = 5_840, epochDay = day - 10, minuteOfDay = 7 * 60 + 50, merchant = "Gas station", tagsCsv = "car"),
        LedgerEntryEntity(id = 11, type = LedgerType.EXPENSE, amountCents = 8_990, epochDay = day - 13, minuteOfDay = 16 * 60 + 5, merchant = "Electricity", tagsCsv = "home"),
        LedgerEntryEntity(id = 12, type = LedgerType.EXPENSE, amountCents = 2_150, epochDay = day - 16, minuteOfDay = 13 * 60, merchant = "Lunch", tagsCsv = "food"),
        LedgerEntryEntity(id = 13, type = LedgerType.INCOME, amountCents = 45_000, epochDay = day - 20, minuteOfDay = 9 * 60 + 15, merchant = "Freelance invoice", tagsCsv = "work"),
    )

    val series = listOf(
        LedgerSeriesEntity(id = 1, type = LedgerType.EXPENSE, amountCents = 145_000, startEpochDay = day - 120, recurrenceUnit = RecurrenceUnit.MONTH, merchant = "Rent", tagsCsv = "home"),
        LedgerSeriesEntity(id = 2, type = LedgerType.EXPENSE, amountCents = 1_899, startEpochDay = day - 61, recurrenceUnit = RecurrenceUnit.MONTH, merchant = "Streaming", note = "Monthly plan", tagsCsv = "subscriptions"),
        LedgerSeriesEntity(id = 3, type = LedgerType.INCOME, amountCents = 330_000, startEpochDay = day - 300, recurrenceUnit = RecurrenceUnit.WEEK, intervalCount = 2, endEpochDay = day + 200, merchant = "Salary", tagsCsv = "work"),
        LedgerSeriesEntity(id = 4, type = LedgerType.EXPENSE, amountCents = 3_500, startEpochDay = day - 400, recurrenceUnit = RecurrenceUnit.MONTH, merchant = "Gym", tagsCsv = "health", active = false),
    )

    val attachments = listOf(
        AttachmentEntity(id = 1, ownerType = AttachmentOwnerType.LEDGER, ownerId = 1, privatePath = "", originalName = "receipt-market.jpg", mimeType = "image/jpeg", sizeBytes = 240_000),
    )
}

/** The Ledger with sample data; the period follows the page so a week boundary never empties the picture. */
@Composable
private fun LedgerScene(
    isWide: Boolean,
    tab: LedgerTab = LedgerTab.ENTRIES,
    entries: List<LedgerEntryEntity> = LedgerSamples.entries,
    series: List<LedgerSeriesEntity> = LedgerSamples.series,
    // All time by default: the samples then fill the picture whatever day of the month it is made.
    period: LedgerPeriod = LedgerPeriod.ALL,
    editor: LedgerEditorUi? = null,
    ruleEditor: LedgerRuleEditorUi? = null,
    initialStopRequest: Long? = null,
) {
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    var shownTab by remember { mutableStateOf(tab) }
    LedgerContent(
        entries = entries,
        series = series,
        formatting = LedgerDisplayFormatting(
            settings = RenderSamples.settings,
            systemUses24Hour = false,
            locale = locale,
            firstDayOfWeek = UserFormatting.firstDayOfWeek(RenderSamples.settings.weekStart, locale),
        ),
        isWide = isWide,
        tab = shownTab,
        onTabChange = { shownTab = it },
        entriesWithAttachments = setOf(1L),
        editor = editor,
        ruleEditor = ruleEditor,
        attachmentsForEntry = { id -> flowOf(LedgerSamples.attachments.filter { it.ownerId == id }) },
        initialPeriod = period,
        initialStopRequest = initialStopRequest,
    )
}

private val newEntryDraft = LedgerDraft(
    amountCents = 0,
    epochDay = RenderSamples.today.toEpochDay(),
    minuteOfDay = 12 * 60 + 5,
)

private val existingEntryDraft = LedgerDraft(
    id = 1,
    type = LedgerType.EXPENSE,
    amountCents = 2_485,
    epochDay = RenderSamples.today.toEpochDay(),
    minuteOfDay = 12 * 60 + 5,
    merchant = "Neighborhood Market",
    note = "Groceries",
    tags = listOf("food"),
)

/** Scenes of the Ledger screens for ScreenRenderTest; see RenderScene. */
internal val ledgerScenes: List<RenderScene> = listOf(
    RenderScene("ledger-entries", TopLevelDestination.LEDGER) { isWide -> LedgerScene(isWide) },
    RenderScene("ledger-entries-empty", TopLevelDestination.LEDGER) { isWide ->
        LedgerScene(isWide, entries = emptyList(), series = emptyList())
    },
    RenderScene("ledger-entries-week", TopLevelDestination.LEDGER) { isWide -> LedgerScene(isWide, period = LedgerPeriod.WEEK) },
    RenderScene("ledger-statistics", TopLevelDestination.LEDGER) { isWide -> LedgerScene(isWide, tab = LedgerTab.STATISTICS) },
    RenderScene("ledger-statistics-custom", TopLevelDestination.LEDGER) { isWide ->
        LedgerScene(isWide, tab = LedgerTab.STATISTICS, period = LedgerPeriod.CUSTOM)
    },
    RenderScene("ledger-recurring", TopLevelDestination.LEDGER) { isWide -> LedgerScene(isWide, tab = LedgerTab.RECURRING) },
    RenderScene("ledger-recurring-empty", TopLevelDestination.LEDGER) { isWide ->
        LedgerScene(isWide, tab = LedgerTab.RECURRING, series = emptyList())
    },
    RenderScene("ledger-editor-new", TopLevelDestination.LEDGER) { isWide ->
        LedgerScene(isWide, editor = LedgerEditorUi(newEntryDraft, sessionKey = "new"))
    },
    RenderScene("ledger-editor-existing", TopLevelDestination.LEDGER) { isWide ->
        LedgerScene(isWide, editor = LedgerEditorUi(existingEntryDraft, sessionKey = "existing"))
    },
    RenderScene("ledger-rule-editor", TopLevelDestination.LEDGER) { isWide ->
        LedgerScene(isWide, tab = LedgerTab.RECURRING, ruleEditor = LedgerRuleEditorUi(seriesId = 3))
    },
    RenderScene("ledger-stop-confirmation", TopLevelDestination.LEDGER) { isWide ->
        LedgerScene(isWide, tab = LedgerTab.RECURRING, initialStopRequest = 1)
    },
)
