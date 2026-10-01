package com.ced2711.lifetracker.ui.lock

import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.model.AppLockTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val settings = MutableStateFlow(AppSettings(appLockEnabled = true))
    private var now = 1_000L
    private val controller = AppLockController(settings, scope) { now }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun startsLockedWhenEnabledAndOpenWhenDisabled() {
        assertEquals(true, controller.locked.value)
        settings.value = AppSettings(appLockEnabled = false)
        assertEquals(false, controller.locked.value)
    }

    @Test
    fun relocksOnlyAfterTheTimeoutInTheBackground() {
        controller.onUnlocked()
        controller.onBackground()
        now += 30_000
        controller.onForeground()
        assertEquals(false, controller.locked.value)

        controller.onBackground()
        now += AppLockTimeout.ONE_MINUTE.millis
        controller.onForeground()
        assertEquals(true, controller.locked.value)
    }

    @Test
    fun itsOwnCredentialScreenDoesNotRelockTheApp() {
        settings.value = AppSettings(appLockEnabled = true, appLockTimeout = AppLockTimeout.IMMEDIATELY)
        controller.onUnlocked()
        controller.authenticating = true
        controller.onBackground()
        controller.authenticating = false
        controller.onForeground()
        assertEquals(false, controller.locked.value)
    }

    @Test
    fun foregroundWithoutBackgroundKeepsTheAppOpen() {
        controller.onUnlocked()
        controller.onForeground()
        assertEquals(false, controller.locked.value)
    }
}
