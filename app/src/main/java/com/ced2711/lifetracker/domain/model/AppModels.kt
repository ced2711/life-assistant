package com.ced2711.lifetracker.domain.model

enum class TodoPriority {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    URGENT,
}

enum class TodoQuickAddField {
    DEADLINE,
    PRIORITY,
    CATEGORY,
    TAGS,
}

enum class LedgerType {
    EXPENSE,
    INCOME,
}

/** Maximum storable ledger amount: $999,999,999.99 represented in cents. */
internal const val MAX_LEDGER_AMOUNT_CENTS: Long = 99_999_999_999L

internal fun requireValidLedgerAmount(amountCents: Long) {
    require(amountCents in 1L..MAX_LEDGER_AMOUNT_CENTS) {
        "Amount must be between 1 and $MAX_LEDGER_AMOUNT_CENTS cents"
    }
}

enum class RecurrenceUnit {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

enum class AttachmentOwnerType {
    TODO,
    LEDGER,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AccentColor {
    TEAL,
    BLUE,
    VIOLET,
    ROSE,
    ORANGE,
    GREEN,
}

enum class WeekStart {
    SYSTEM,
    SUNDAY,
    MONDAY,
}

enum class TimeFormatOption {
    SYSTEM,
    HOUR_12,
    HOUR_24,
}

enum class DateFormatOption {
    SYSTEM,
    MONTH_DAY_YEAR,
    DAY_MONTH_YEAR,
    YEAR_MONTH_DAY,
}

enum class ReminderOffsetPreset(val minutesBeforeDue: Long) {
    AT_DUE(0),
    ONE_HOUR(60),
    ONE_DAY(1_440),
    THREE_DAYS(4_320),
    ONE_WEEK(10_080),
}

enum class TopLevelDestination {
    TODO,
    LEDGER,
    CALENDAR,
}

enum class SeriesEditScope {
    ONLY_THIS_OCCURRENCE,
    THIS_AND_FUTURE_OCCURRENCES,
}

data class RecurrenceRule(
    val unit: RecurrenceUnit,
    val interval: Int = 1,
    val endEpochDay: Long? = null,
)

data class TodoDraft(
    val id: Long? = null,
    val title: String = "",
    val description: String,
    val categoryId: Long? = null,
    val deadlineEpochDay: Long? = null,
    val deadlineMinute: Int? = null,
    val priority: TodoPriority = TodoPriority.NONE,
    val tags: List<String> = emptyList(),
    val reminderOffsetsMinutes: List<Long> = emptyList(),
    val subtasks: List<String> = emptyList(),
    val recurrence: RecurrenceRule? = null,
)

data class LedgerDraft(
    val id: Long? = null,
    val type: LedgerType = LedgerType.EXPENSE,
    val amountCents: Long,
    val epochDay: Long,
    val minuteOfDay: Int,
    val note: String = "",
    val merchant: String = "",
    val tags: List<String> = emptyList(),
    val recurrence: RecurrenceRule? = null,
)

/**
 * Saving a future recurring ledger rule can legitimately create a series without materializing an
 * entry yet. Callers should only offer entry attachments when [entryId] is non-null.
 */
data class LedgerSaveResult(
    val entryId: Long?,
    val seriesId: Long?,
)

/** Opaque Undo receipt for a transactional occurrence or series-tail deletion. */
data class RecurringDeleteResult(
    val ownerType: AttachmentOwnerType,
    val itemId: Long,
    val scope: SeriesEditScope,
    val seriesId: Long?,
    val boundaryEpochDay: Long?,
    val seriesWasActive: Boolean,
    val seriesUpdatedAt: Long?,
    val deletedAt: Long,
    /** Process-local user Undo deadline measured by Android's monotonic elapsed-realtime clock. */
    val undoExpiresAtElapsedRealtime: Long,
    /** Persisted wall-clock cleanup deadline and attachment compare-and-set token. */
    val attachmentsPendingAt: Long,
    val exceptionCreated: Boolean,
)

fun deriveTodoTitle(description: String, maximumLength: Int = 40): String {
    require(maximumLength >= 0) { "Maximum title length must not be negative" }
    val firstParagraph = description
        .lineSequence()
        .map(String::trim)
        .dropWhile(String::isEmpty)
        .takeWhile(String::isNotEmpty)
        .joinToString(" ")
    val codePointCount = firstParagraph.codePointCount(0, firstParagraph.length)
    return if (codePointCount <= maximumLength) {
        firstParagraph
    } else if (maximumLength == 0) {
        ""
    } else {
        val prefixEnd = firstParagraph.offsetByCodePoints(0, maximumLength - 1)
        firstParagraph.substring(0, prefixEnd).trimEnd() + "…"
    }
}

internal fun categoryPathLabel(
    categoryId: Long,
    namesById: Map<Long, String>,
    parentIdsById: Map<Long, Long?>,
): String? {
    val names = mutableListOf<String>()
    val visited = mutableSetOf<Long>()
    var cursor: Long? = categoryId
    while (cursor != null && visited.add(cursor)) {
        val name = namesById[cursor] ?: break
        names += name
        cursor = parentIdsById[cursor]
    }
    return names.takeIf { it.isNotEmpty() }?.asReversed()?.joinToString(" / ")
}

fun normalizeTags(tags: Iterable<String>): String = tags
    .flatMap { it.split(',') }
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(::tagKey)
    .joinToString(",")

fun parseTags(csv: String): List<String> = csv
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
