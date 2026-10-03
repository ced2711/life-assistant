package com.ced2711.lifetracker.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.WeekStart
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `empty store uses the agreed defaults`() = runBlocking {
        val job = SupervisorJob()
        try {
            val repository = repository(
                file = File(temporaryFolder.newFolder(), "empty.preferences_pb"),
                scope = CoroutineScope(Dispatchers.IO + job),
            )

            val settings = repository.settings.first()
            assertEquals(ThemeMode.DARK, settings.themeMode)
            assertEquals(AccentColor.TEAL, settings.accentColor)
            assertEquals(UiLanguage.ENGLISH, settings.uiLanguage)
            assertEquals(WeekStart.SUNDAY, settings.weekStart)
            assertEquals(TimeFormatOption.HOUR_12, settings.timeFormat)
            assertEquals(DateFormatOption.MONTH_DAY_YEAR, settings.dateFormat)
            assertEquals(false, settings.notificationsEnabled)
            assertEquals(0, settings.defaultAllDayReminderMinute)
            assertEquals(setOf(0L), settings.defaultReminderOffsetsMinutes)
            assertEquals(emptySet<TodoQuickAddField>(), settings.todoQuickAddFields)
            assertEquals(TopLevelDestination.TODO, settings.lastDestination)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `all settings survive reopening the data store`() = runBlocking {
        assertSettingSurvivesReopening(
            name = "theme",
            expected = AppSettings(themeMode = ThemeMode.LIGHT),
        ) { setTheme(ThemeMode.LIGHT) }
        assertSettingSurvivesReopening(
            name = "accent-color",
            expected = AppSettings(accentColor = AccentColor.VIOLET),
        ) { setAccentColor(AccentColor.VIOLET) }
        assertSettingSurvivesReopening(
            name = "ui-language",
            expected = AppSettings(uiLanguage = UiLanguage.SIMPLIFIED_CHINESE),
        ) { setUiLanguage(UiLanguage.SIMPLIFIED_CHINESE) }
        assertSettingSurvivesReopening(
            name = "week-start",
            expected = AppSettings(weekStart = WeekStart.MONDAY),
        ) { setWeekStart(WeekStart.MONDAY) }
        assertSettingSurvivesReopening(
            name = "time-format",
            expected = AppSettings(timeFormat = TimeFormatOption.HOUR_24),
        ) { setTimeFormat(TimeFormatOption.HOUR_24) }
        assertSettingSurvivesReopening(
            name = "date-format",
            expected = AppSettings(dateFormat = DateFormatOption.YEAR_MONTH_DAY),
        ) { setDateFormat(DateFormatOption.YEAR_MONTH_DAY) }
        assertSettingSurvivesReopening(
            name = "notifications",
            expected = AppSettings(notificationsEnabled = true),
        ) { setNotificationsEnabled(true) }
        assertSettingSurvivesReopening(
            name = "all-day-minute",
            expected = AppSettings(defaultAllDayReminderMinute = 23 * 60 + 59),
        ) { setDefaultAllDayReminderMinute(23 * 60 + 59) }
        assertSettingSurvivesReopening(
            name = "default-reminders",
            expected = AppSettings(
                defaultReminderOffsetsMinutes = setOf(0L, 60L, 1_440L, 10_080L),
            ),
        ) {
            setDefaultReminderOffsetsMinutes(setOf(0L, 60L, 1_440L, 10_080L))
        }
        assertSettingSurvivesReopening(
            name = "todo-quick-add-fields",
            expected = AppSettings(
                todoQuickAddFields = setOf(
                    TodoQuickAddField.DEADLINE,
                    TodoQuickAddField.CATEGORY,
                ),
            ),
        ) {
            setTodoQuickAddFields(
                setOf(TodoQuickAddField.DEADLINE, TodoQuickAddField.CATEGORY),
            )
        }
        assertSettingSurvivesReopening(
            name = "last-destination",
            expected = AppSettings(lastDestination = TopLevelDestination.CALENDAR),
        ) { setLastDestination(TopLevelDestination.CALENDAR) }
    }

    @Test
    fun `explicitly saved empty default reminders survive reopening`() = runBlocking {
        assertSettingSurvivesReopening(
            name = "empty-default-reminders",
            expected = AppSettings(defaultReminderOffsetsMinutes = emptySet()),
        ) {
            setDefaultReminderOffsetsMinutes(emptySet())
        }
    }

    @Test
    fun `unknown quick add field values are ignored`() = runBlocking {
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + job),
                produceFile = {
                    File(temporaryFolder.newFolder(), "unknown-quick-add.preferences_pb")
                },
            )
            store.edit { preferences ->
                preferences[stringSetPreferencesKey("todo_quick_add_fields")] =
                    setOf("DEADLINE", "A_FUTURE_FIELD")
            }

            assertEquals(
                setOf(TodoQuickAddField.DEADLINE),
                SettingsRepository(store).settings.first().todoQuickAddFields,
            )
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `unknown accent color falls back to teal`() = runBlocking {
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + job),
                produceFile = {
                    File(temporaryFolder.newFolder(), "unknown-accent.preferences_pb")
                },
            )
            store.edit { preferences ->
                preferences[stringPreferencesKey("accent_color")] = "A_FUTURE_COLOR"
            }

            assertEquals(
                AccentColor.TEAL,
                SettingsRepository(store).settings.first().accentColor,
            )
        } finally {
            job.cancelAndJoin()
        }
    }

    private suspend fun assertSettingSurvivesReopening(
        name: String,
        expected: AppSettings,
        update: suspend SettingsRepository.() -> Unit,
    ) {
        val file = File(
            temporaryFolder.newFolder(name),
            "$name.preferences_pb",
        )
        val writeJob = SupervisorJob()
        try {
            repository(file, CoroutineScope(Dispatchers.IO + writeJob)).update()
        } finally {
            writeJob.cancelAndJoin()
        }

        val readJob = SupervisorJob()
        try {
            val reader = repository(file, CoroutineScope(Dispatchers.IO + readJob))
            assertEquals(expected, reader.settings.first())
        } finally {
            readJob.cancelAndJoin()
        }
    }

    @Test
    fun `all-day reminder values are clamped on write`() = runBlocking {
        val job = SupervisorJob()
        try {
            val repository = repository(
                file = File(temporaryFolder.newFolder(), "clamp.preferences_pb"),
                scope = CoroutineScope(Dispatchers.IO + job),
            )

            repository.setDefaultAllDayReminderMinute(Int.MAX_VALUE)

            assertEquals(1_439, repository.settings.first().defaultAllDayReminderMinute)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `snapshot and replace cover every setting in one operation`() = runBlocking {
        val job = SupervisorJob()
        try {
            val repository = repository(
                file = File(temporaryFolder.newFolder(), "replace.preferences_pb"),
                scope = CoroutineScope(Dispatchers.IO + job),
            )
            val expected = AppSettings(
                themeMode = ThemeMode.LIGHT,
                accentColor = AccentColor.ROSE,
                weekStart = WeekStart.MONDAY,
                timeFormat = TimeFormatOption.HOUR_24,
                dateFormat = DateFormatOption.DAY_MONTH_YEAR,
                notificationsEnabled = true,
                defaultAllDayReminderMinute = 8 * 60 + 15,
                defaultReminderOffsetsMinutes = setOf(0L, 30L, 1_440L),
                todoQuickAddFields = TodoQuickAddField.entries.toSet(),
                lastDestination = TopLevelDestination.LEDGER,
            )

            repository.replace(expected)

            assertEquals(expected, repository.snapshot())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `backup replacement preserves the device UI language`() = runBlocking {
        val repository = SettingsRepository(InMemoryPreferencesDataStore())
        repository.setUiLanguage(UiLanguage.SIMPLIFIED_CHINESE)

        repository.replace(
            AppSettings(
                themeMode = ThemeMode.LIGHT,
                uiLanguage = UiLanguage.ENGLISH,
                notificationsEnabled = true,
            ),
        )

        assertEquals(
            AppSettings(
                themeMode = ThemeMode.LIGHT,
                uiLanguage = UiLanguage.SIMPLIFIED_CHINESE,
                notificationsEnabled = true,
            ),
            repository.snapshot(),
        )
    }

    private fun repository(file: File, scope: CoroutineScope) = SettingsRepository(
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        ),
    )
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    private val updateMutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        updateMutex.withLock {
            transform(state.value).also { state.value = it }
        }
}
