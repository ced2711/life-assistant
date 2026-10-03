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
    NOTE,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class UiLanguage {
    ENGLISH,
    SIMPLIFIED_CHINESE,
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
    /** The day at a glance: what is due, what was spent, the diary and what comes next. */
    TODAY,
    TODO,
    LEDGER,
    CALENDAR,
    NOTES,
    DIARY,
    CONFESSIONAL,
}

/** Optional modules stay out of the menu until the user turns them on in Settings. */
val DefaultHiddenDestinations: Set<TopLevelDestination> =
    setOf(TopLevelDestination.TODAY, TopLevelDestination.DIARY, TopLevelDestination.CONFESSIONAL)

/**
 * Modules that show only when the user chose them, also on a device whose saved choice is older
 * than the module. The apps save the hidden modules, so that a module added later shows up by
 * itself; Today is the exception and must not appear uninvited.
 */
val OptInDestinations: Set<TopLevelDestination> = setOf(TopLevelDestination.TODAY)

/**
 * The modules in the menu, from what a device saved: the names of the hidden modules ([hidden],
 * null when nothing was saved yet) and of the opt-in modules the user turned on ([optedIn]).
 */
fun resolveVisibleDestinations(hidden: Set<String>?, optedIn: Set<String>?): Set<TopLevelDestination> {
    val hiddenNames = hidden ?: DefaultHiddenDestinations.mapTo(mutableSetOf()) { it.name }
    return TopLevelDestination.entries.filterTo(mutableSetOf()) { destination ->
        destination.name !in hiddenNames && (destination !in OptInDestinations || destination.name in optedIn.orEmpty())
    }
}

val DefaultVisibleDestinations: Set<TopLevelDestination> =
    TopLevelDestination.entries.toSet() - DefaultHiddenDestinations

/**
 * Navigation order for the visible modules. An empty choice falls back to every module so the app
 * can never hide all of its navigation. Visibility is a device-local preference, never backed up.
 */
fun normalizeVisibleDestinations(selected: Set<TopLevelDestination>): List<TopLevelDestination> =
    TopLevelDestination.entries.filter { it in selected }.ifEmpty { TopLevelDestination.entries }

/** How long the app may stay in the background before the optional app lock engages again. */
enum class AppLockTimeout(val millis: Long) {
    IMMEDIATELY(0),
    ONE_MINUTE(60_000),
    FIVE_MINUTES(300_000),
}

/**
 * Whether a lock that is enabled should be shown after the app returned to the foreground.
 * [backgroundedAtElapsed] is null while the app has not been in the background since unlocking.
 */
fun appLockExpired(
    backgroundedAtElapsed: Long?,
    nowElapsed: Long,
    timeout: AppLockTimeout,
): Boolean = backgroundedAtElapsed != null &&
    (nowElapsed < backgroundedAtElapsed || nowElapsed - backgroundedAtElapsed >= timeout.millis)

/** Where to land when the remembered destination has been hidden. */
fun resolveVisibleDestination(
    preferred: TopLevelDestination,
    visible: List<TopLevelDestination>,
): TopLevelDestination = if (preferred in visible) preferred else visible.first()

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

data class NoteDraft(
    val id: Long? = null,
    val folderId: Long? = null,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
)

const val MAX_DIARY_LENGTH = 1_000_000

/** First non-blank line of a diary page, shortened for lists and calendar details. */
fun diaryPreview(body: String, maximumLength: Int = 80): String {
    val line = body.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty).orEmpty()
    return if (line.length <= maximumLength) line else line.take(maximumLength - 1).trimEnd() + "…"
}

/** One diary page per calendar day; saving a blank body removes that day's page. */
data class DiaryDraft(
    val epochDay: Long,
    val body: String,
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
