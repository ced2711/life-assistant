package com.ced2711.lifetracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.WeekStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.taskLedgerDataStore by preferencesDataStore(name = "taskledger_settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: AccentColor = AccentColor.TEAL,
    val uiLanguage: UiLanguage = UiLanguage.ENGLISH,
    val weekStart: WeekStart = WeekStart.SUNDAY,
    val timeFormat: TimeFormatOption = TimeFormatOption.HOUR_12,
    val dateFormat: DateFormatOption = DateFormatOption.MONTH_DAY_YEAR,
    val notificationsEnabled: Boolean = false,
    val defaultAllDayReminderMinute: Int = 0,
    val defaultReminderOffsetsMinutes: Set<Long> = setOf(0L),
    val todoQuickAddFields: Set<TodoQuickAddField> = emptySet(),
    val lastDestination: TopLevelDestination = TopLevelDestination.TODO,
)

class SettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.taskLedgerDataStore)

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val accentColor = stringPreferencesKey("accent_color")
        val uiLanguage = stringPreferencesKey("ui_language")
        val weekStart = stringPreferencesKey("week_start")
        val timeFormat = stringPreferencesKey("time_format")
        val dateFormat = stringPreferencesKey("date_format")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val allDayReminderMinute = intPreferencesKey("all_day_reminder_minute")
        val defaultReminderOffsetsMinutes = stringSetPreferencesKey("default_reminder_offsets_minutes")
        val todoQuickAddFields = stringSetPreferencesKey("todo_quick_add_fields")
        val lastDestination = stringPreferencesKey("last_destination")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { preferences -> preferences.toAppSettings() }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
            themeMode = this[Keys.theme].enumOrDefault(ThemeMode.DARK),
            accentColor = this[Keys.accentColor].enumOrDefault(AccentColor.TEAL),
            uiLanguage = this[Keys.uiLanguage].enumOrDefault(UiLanguage.ENGLISH),
            weekStart = this[Keys.weekStart].enumOrDefault(WeekStart.SUNDAY),
            timeFormat = this[Keys.timeFormat].enumOrDefault(TimeFormatOption.HOUR_12),
            dateFormat = this[Keys.dateFormat].enumOrDefault(DateFormatOption.MONTH_DAY_YEAR),
            notificationsEnabled = this[Keys.notificationsEnabled] ?: false,
            defaultAllDayReminderMinute = (this[Keys.allDayReminderMinute] ?: 0)
                .coerceIn(0, 1_439),
            defaultReminderOffsetsMinutes = this[Keys.defaultReminderOffsetsMinutes]
                ?.mapNotNull(String::toLongOrNull)
                ?.filter { it >= 0 }
                ?.sorted()
                ?.toSet()
                ?: setOf(0L),
            todoQuickAddFields = this[Keys.todoQuickAddFields]
                .orEmpty()
                .mapNotNull { saved -> TodoQuickAddField.entries.firstOrNull { it.name == saved } }
                .toSet(),
            lastDestination = this[Keys.lastDestination].enumOrDefault(TopLevelDestination.TODO),
        )

    suspend fun setTheme(value: ThemeMode) = edit(Keys.theme, value.name)
    suspend fun setAccentColor(value: AccentColor) = edit(Keys.accentColor, value.name)
    suspend fun setUiLanguage(value: UiLanguage) = edit(Keys.uiLanguage, value.name)
    suspend fun setWeekStart(value: WeekStart) = edit(Keys.weekStart, value.name)
    suspend fun setTimeFormat(value: TimeFormatOption) = edit(Keys.timeFormat, value.name)
    suspend fun setDateFormat(value: DateFormatOption) = edit(Keys.dateFormat, value.name)
    suspend fun setNotificationsEnabled(value: Boolean) = edit(Keys.notificationsEnabled, value)
    suspend fun setDefaultAllDayReminderMinute(value: Int) = edit(Keys.allDayReminderMinute, value.coerceIn(0, 1_439))
    suspend fun setDefaultReminderOffsetsMinutes(value: Set<Long>) = edit(
        Keys.defaultReminderOffsetsMinutes,
        value.asSequence()
            .filter { it >= 0 }
            .distinct()
            .sorted()
            .map(Long::toString)
            .toSet(),
    )
    suspend fun setTodoQuickAddFields(value: Set<TodoQuickAddField>) = edit(
        Keys.todoQuickAddFields,
        value.mapTo(mutableSetOf()) { it.name },
    )
    suspend fun setLastDestination(value: TopLevelDestination) = edit(Keys.lastDestination, value.name)

    /** Returns one normalized, complete settings value suitable for an encrypted backup. */
    suspend fun snapshot(): AppSettings = settings.first()

    /** Atomically replaces every setting owned by this repository. */
    suspend fun replace(value: AppSettings) {
        dataStore.edit { preferences ->
            preferences.replaceBackedUpSettings(value)
        }
    }

    /** Compare-and-replace prevents a cloud restore from overwriting a concurrent settings edit. */
    suspend fun replaceIfUnchanged(expected: AppSettings, value: AppSettings): Boolean {
        var replaced = false
        dataStore.edit { preferences ->
            val current = preferences.toAppSettings()
            if (current.sameRestorableSettings(expected)) {
                preferences.replaceBackedUpSettings(value)
                replaced = true
            }
        }
        return replaced
    }

    private fun MutablePreferences.replaceBackedUpSettings(value: AppSettings) {
        this[Keys.theme] = value.themeMode.name
        this[Keys.accentColor] = value.accentColor.name
        // UI language remains a device preference.
        this[Keys.weekStart] = value.weekStart.name
        this[Keys.timeFormat] = value.timeFormat.name
        this[Keys.dateFormat] = value.dateFormat.name
        this[Keys.notificationsEnabled] = value.notificationsEnabled
        this[Keys.allDayReminderMinute] = value.defaultAllDayReminderMinute.coerceIn(0, 1_439)
        this[Keys.defaultReminderOffsetsMinutes] = value.defaultReminderOffsetsMinutes
            .asSequence()
            .filter { it >= 0 }
            .distinct()
            .sorted()
            .map(Long::toString)
            .toSet()
        this[Keys.todoQuickAddFields] = value.todoQuickAddFields.mapTo(mutableSetOf()) { it.name }
        this[Keys.lastDestination] = value.lastDestination.name
    }

    private suspend fun <T> edit(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }
}

private fun AppSettings.sameRestorableSettings(other: AppSettings): Boolean = copy(
    uiLanguage = other.uiLanguage,
    lastDestination = other.lastDestination,
) == other

private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
