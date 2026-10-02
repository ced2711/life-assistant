package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.WeekStart
import com.ced2711.lifetracker.ui.localization.uiLocale
import java.time.LocalDate

internal fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

internal fun weekStartLabel(value: WeekStart): String = when (value) {
    WeekStart.SYSTEM -> "System default"
    WeekStart.SUNDAY -> "Sunday"
    WeekStart.MONDAY -> "Monday"
}

internal fun timeFormatLabel(value: TimeFormatOption): String = when (value) {
    TimeFormatOption.SYSTEM -> "System default"
    TimeFormatOption.HOUR_12 -> "12-hour"
    TimeFormatOption.HOUR_24 -> "24-hour"
}

/** Shows each date format as an example date, which says more than a name. */
internal fun dateFormatLabel(value: DateFormatOption, language: UiLanguage): String =
    if (value == DateFormatOption.SYSTEM) desktopText("System default", language)
    else UserFormatting.formatDate(LocalDate.now(), value, uiLocale(language))
