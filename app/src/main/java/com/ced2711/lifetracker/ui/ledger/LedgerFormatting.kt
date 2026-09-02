package com.ced2711.lifetracker.ui.ledger

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.MAX_LEDGER_AMOUNT_CENTS
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

internal val LEDGER_SHORTCUT_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US)

private const val MAX_LEDGER_WHOLE_DIGITS = 9
private const val MAX_LEDGER_AMOUNT_INPUT_LENGTH = MAX_LEDGER_WHOLE_DIGITS + 3

internal data class LedgerDisplayFormatting(
    val settings: AppSettings,
    val systemUses24Hour: Boolean,
    val locale: Locale,
    val firstDayOfWeek: DayOfWeek,
) {
    val uses24HourTime: Boolean
        get() = UserFormatting.uses24HourClock(settings.timeFormat, systemUses24Hour)

    fun date(epochDay: Long): String = UserFormatting.formatDate(
        date = LocalDate.ofEpochDay(epochDay),
        option = settings.dateFormat,
        locale = locale,
    )

    fun time(minuteOfDay: Int): String = UserFormatting.formatMinuteOfDay(
        minuteOfDay = minuteOfDay,
        option = settings.timeFormat,
        systemUses24Hour = systemUses24Hour,
        locale = locale,
    )
}

/** Resolves user-selected date, time, and week conventions once per Ledger composition. */
@Composable
internal fun rememberLedgerDisplayFormatting(settings: AppSettings): LedgerDisplayFormatting {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    val systemUses24Hour = DateFormat.is24HourFormat(context)
    return remember(settings, systemUses24Hour, locale) {
        LedgerDisplayFormatting(
            settings = settings,
            systemUses24Hour = systemUses24Hour,
            locale = locale,
            firstDayOfWeek = UserFormatting.firstDayOfWeek(settings.weekStart, locale),
        )
    }
}

internal fun formatMoney(cents: Long): String =
    String.format(Locale.US, "$%,.2f", cents / 100.0)

internal fun formatSignedMoney(cents: Long): String = when {
    cents > 0 -> "+${formatMoney(cents)}"
    cents < 0 -> "-${formatMoney(-cents)}"
    else -> formatMoney(0)
}

internal fun parseAmountCents(value: String): Long? = runCatching {
    BigDecimal(value)
        .setScale(2, RoundingMode.UNNECESSARY)
        .movePointRight(2)
        .longValueExact()
        .takeIf { it in 1..MAX_LEDGER_AMOUNT_CENTS }
}.getOrNull()

internal fun sanitizeAmountInput(value: String): String? {
    if (value.isEmpty()) return value
    if (value.length > MAX_LEDGER_AMOUNT_INPUT_LENGTH) return null
    if (!value.matches(Regex("\\d*(\\.\\d{0,2})?"))) return null
    if (value.substringBefore('.').length > MAX_LEDGER_WHOLE_DIGITS) return null
    val whole = value.substringBefore('.').trimStart('0').ifEmpty { "0" }
    return if ('.' in value) "$whole.${value.substringAfter('.')}" else whole
}

internal fun parseLedgerTimeInput(value: String): Int? {
    val normalized = value.trim().uppercase(Locale.US)
    if (normalized.isEmpty()) return null
    val formats = listOf(
        DateTimeFormatter.ofPattern("h:mm a", Locale.US),
        DateTimeFormatter.ofPattern("h a", Locale.US),
        DateTimeFormatter.ofPattern("H:mm", Locale.US),
    )
    return formats.firstNotNullOfOrNull { formatter ->
        try {
            LocalTime.parse(normalized, formatter).let { it.hour * 60 + it.minute }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

internal fun formatLedgerTimeInput(minuteOfDay: Int, uses24HourTime: Boolean): String {
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    return time.format(
        DateTimeFormatter.ofPattern(if (uses24HourTime) "HH:mm" else "h:mm a", Locale.US),
    )
}

internal fun sanitizeRecurrenceIntervalInput(value: String): String? =
    value.takeIf { candidate -> candidate.all(Char::isDigit) }

internal fun recurrenceIntervalError(value: String): String? =
    if (value.toIntOrNull()?.let { it > 0 } == true) {
        null
    } else {
        "Enter a whole number from 1 to 2,147,483,647"
    }

internal fun LedgerType.displayName(): String =
    name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }
