package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSettings
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.ReminderOffsetPreset
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.WeekStart
import com.ced2711.lifetracker.ui.localization.uiLocale
import java.time.LocalDate

/** Reminder defaults; they are part of the synced data, so the phone uses them too. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderSettingsCard(settings: BackupSettings, onChange: ((BackupSettings) -> BackupSettings) -> Unit) {
    var allDayText by remember(settings.defaultAllDayReminderMinute) {
        mutableStateOf("%d:%02d".format(settings.defaultAllDayReminderMinute / 60, settings.defaultAllDayReminderMinute % 60))
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, null)
                Spacer(Modifier.width(10.dp))
                Text(desktopText("Reminders"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(desktopText("Reminder notifications"))
                    Text(desktopText("Shown by the phone app. Shared with your other devices through sync."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(settings.notificationsEnabled, { enabled -> onChange { it.copy(notificationsEnabled = enabled) } })
            }
            Text(desktopText("Default reminders for new todos with a date"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReminderOffsetPreset.entries.forEach { preset ->
                    val on = preset.minutesBeforeDue in settings.defaultReminderOffsetsMinutes
                    FilterChipSimple(desktopText(reminderLabel(preset)), on) {
                        onChange { current ->
                            current.copy(defaultReminderOffsetsMinutes = if (on) current.defaultReminderOffsetsMinutes - preset.minutesBeforeDue else current.defaultReminderOffsetsMinutes + preset.minutesBeforeDue)
                        }
                    }
                }
            }
            OutlinedTextField(
                allDayText,
                { value ->
                    allDayText = value
                    parseTimeOfDay(value)?.let { minute -> onChange { it.copy(defaultAllDayReminderMinute = minute) } }
                },
                label = { Text(desktopText("Reminder time for todos without a time")) },
                isError = parseTimeOfDay(allDayText) == null,
                singleLine = true,
                modifier = Modifier.width(320.dp),
            )
        }
    }
}

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
