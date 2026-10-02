package com.ced2711.lifetracker.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.ced2711.lifetracker.domain.model.AppLockTimeout
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLocalSettingsTest {
    private fun repository() = SettingsRepository(MemoryPreferences())

    @Test
    fun `app lock is off and optional modules are hidden by default`() = runBlocking {
        val settings = repository().settings.first()
        assertEquals(false, settings.appLockEnabled)
        assertEquals(AppLockTimeout.ONE_MINUTE, settings.appLockTimeout)
        assertEquals(
            setOf(TopLevelDestination.TODAY, TopLevelDestination.TODO, TopLevelDestination.LEDGER, TopLevelDestination.CALENDAR, TopLevelDestination.NOTES),
            settings.visibleDestinations,
        )
    }

    @Test
    fun `choosing every module is kept instead of falling back to the defaults`() = runBlocking {
        val repository = repository()
        repository.setVisibleDestinations(TopLevelDestination.entries.toSet())
        assertEquals(TopLevelDestination.entries.toSet(), repository.settings.first().visibleDestinations)
    }

    @Test
    fun `module visibility and app lock preferences persist`() = runBlocking {
        val repository = repository()
        repository.setVisibleDestinations(setOf(TopLevelDestination.TODO, TopLevelDestination.DIARY))
        repository.setAppLockEnabled(true)
        repository.setAppLockTimeout(AppLockTimeout.IMMEDIATELY)

        val settings = repository.settings.first()
        assertEquals(setOf(TopLevelDestination.TODO, TopLevelDestination.DIARY), settings.visibleDestinations)
        assertEquals(true, settings.appLockEnabled)
        assertEquals(AppLockTimeout.IMMEDIATELY, settings.appLockTimeout)
    }

    @Test
    fun `restoring backed up settings never changes device-local preferences`() = runBlocking {
        val repository = repository()
        repository.setVisibleDestinations(setOf(TopLevelDestination.NOTES))
        repository.setAppLockEnabled(true)
        val before = repository.snapshot()

        // A backup restored from another device carries its own defaults for these fields.
        val restored = AppSettings(themeMode = ThemeMode.LIGHT)
        assertTrue(repository.replaceIfUnchanged(before.copy(appLockEnabled = false), restored))

        val after = repository.settings.first()
        assertEquals(ThemeMode.LIGHT, after.themeMode)
        assertEquals(true, after.appLockEnabled)
        assertEquals(setOf(TopLevelDestination.NOTES), after.visibleDestinations)
    }
}

private class MemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    private val updateMutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        updateMutex.withLock { transform(state.value).also { state.value = it } }
}
