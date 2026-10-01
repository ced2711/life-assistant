package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.cloudsync.LocalCloudSyncState
import com.ced2711.lifetracker.domain.model.AppLockTimeout
import com.ced2711.lifetracker.domain.model.DefaultHiddenDestinations
import com.ced2711.lifetracker.domain.model.DefaultVisibleDestinations
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.UiLanguage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

enum class DesktopCloudProvider { GOOGLE_DRIVE, GITHUB }

data class DesktopCloudConfig(
    val clientId: String,
    val automaticSync: Boolean,
    val syncState: LocalCloudSyncState,
    val uiLanguage: UiLanguage = UiLanguage.ENGLISH,
    val lastDestination: TopLevelDestination = TopLevelDestination.TODO,
    val visibleDestinations: Set<TopLevelDestination> = DefaultVisibleDestinations,
    val appLockEnabled: Boolean = false,
    val appLockTimeout: AppLockTimeout = AppLockTimeout.ONE_MINUTE,
    val provider: DesktopCloudProvider = DesktopCloudProvider.GOOGLE_DRIVE,
    val gitHubClientId: String = "",
    val gitHubRepository: String = "",
)

data class DesktopWindowBounds(val width: Int, val height: Int, val x: Int?, val y: Int?, val maximized: Boolean)

class DesktopConfigStore(
    private val file: File = File(DesktopDataStore.defaultAppDirectory(), "desktop.properties"),
) {
    @Synchronized
    fun read(): DesktopCloudConfig {
        val properties = load()
        val deviceId = properties.getProperty(KEY_DEVICE_ID)?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString().also {
                properties.setProperty(KEY_DEVICE_ID, it)
                save(properties)
            }
        return DesktopCloudConfig(
            clientId = properties.getProperty(KEY_CLIENT_ID)
                ?: System.getenv("LIFE_TRACKER_GOOGLE_DESKTOP_CLIENT_ID").orEmpty(),
            automaticSync = properties.getProperty(KEY_AUTOMATIC)?.toBooleanStrictOrNull() ?: false,
            syncState = LocalCloudSyncState(
                deviceId = deviceId,
                lastRevisionId = properties.getProperty(KEY_LAST_REVISION),
                lastContentFingerprint = properties.getProperty(KEY_LAST_FINGERPRINT),
                lastSyncAt = properties.getProperty(KEY_LAST_SYNC_AT)?.toLongOrNull(),
            ),
            uiLanguage = properties.getProperty(KEY_UI_LANGUAGE)
                ?.let { value -> runCatching { UiLanguage.valueOf(value) }.getOrNull() }
                ?: UiLanguage.ENGLISH,
            lastDestination = properties.getProperty(KEY_LAST_DESTINATION)
                ?.let { value -> runCatching { TopLevelDestination.valueOf(value) }.getOrNull() }
                ?: TopLevelDestination.TODO,
            // Hidden rather than visible modules are stored so modules added later start visible.
            // No saved choice yet means the defaults; an empty saved value means every module is shown.
            visibleDestinations = (
                properties.getProperty(KEY_HIDDEN_DESTINATIONS)
                    ?: DefaultHiddenDestinations.joinToString(",") { it.name }
                )
                .split(',')
                .map(String::trim)
                .toSet()
                .let { hidden -> TopLevelDestination.entries.filterNot { it.name in hidden }.toSet() },
            appLockEnabled = properties.getProperty(KEY_APP_LOCK)?.toBooleanStrictOrNull() ?: false,
            appLockTimeout = properties.getProperty(KEY_APP_LOCK_TIMEOUT)
                ?.let { value -> AppLockTimeout.entries.firstOrNull { it.name == value } }
                ?: AppLockTimeout.ONE_MINUTE,
            provider = properties.getProperty(KEY_PROVIDER)
                ?.let { value -> DesktopCloudProvider.entries.firstOrNull { it.name == value } }
                ?: DesktopCloudProvider.GOOGLE_DRIVE,
            gitHubClientId = properties.getProperty(KEY_GITHUB_CLIENT_ID)
                ?: System.getenv("LIFE_ASSISTANT_GITHUB_CLIENT_ID").orEmpty(),
            gitHubRepository = properties.getProperty(KEY_GITHUB_REPOSITORY).orEmpty(),
        )
    }

    @Synchronized
    fun setClientId(value: String) {
        val properties = load()
        properties.setProperty(KEY_CLIENT_ID, value.trim())
        save(properties)
    }

    @Synchronized
    fun setAutomaticSync(enabled: Boolean) {
        val properties = load()
        properties.setProperty(KEY_AUTOMATIC, enabled.toString())
        save(properties)
    }

    @Synchronized
    fun setUiLanguage(value: UiLanguage) {
        val properties = load()
        properties.setProperty(KEY_UI_LANGUAGE, value.name)
        save(properties)
    }

    @Synchronized
    fun setLastDestination(value: TopLevelDestination) {
        val properties = load()
        properties.setProperty(KEY_LAST_DESTINATION, value.name)
        save(properties)
    }

    @Synchronized
    fun setVisibleDestinations(value: Set<TopLevelDestination>) {
        val properties = load()
        properties.setProperty(
            KEY_HIDDEN_DESTINATIONS,
            TopLevelDestination.entries.filterNot(value::contains).joinToString(",") { it.name },
        )
        save(properties)
    }

    @Synchronized
    fun setAppLock(enabled: Boolean, timeout: AppLockTimeout) {
        val properties = load()
        properties.setProperty(KEY_APP_LOCK, enabled.toString())
        properties.setProperty(KEY_APP_LOCK_TIMEOUT, timeout.name)
        save(properties)
    }

    @Synchronized
    fun setProvider(provider: DesktopCloudProvider, gitHubClientId: String? = null, gitHubRepository: String? = null) {
        val properties = load()
        properties.setProperty(KEY_PROVIDER, provider.name)
        gitHubClientId?.let { properties.setProperty(KEY_GITHUB_CLIENT_ID, it.trim()) }
        gitHubRepository?.let { properties.setProperty(KEY_GITHUB_REPOSITORY, it.trim()) }
        save(properties)
    }

    @Synchronized
    fun recordSync(revisionId: String, fingerprint: String, syncedAt: Long) {
        val properties = load()
        properties.setProperty(KEY_LAST_REVISION, revisionId)
        properties.setProperty(KEY_LAST_FINGERPRINT, fingerprint)
        properties.setProperty(KEY_LAST_SYNC_AT, syncedAt.toString())
        save(properties)
    }

    @Synchronized
    fun clearSyncState() {
        val properties = load()
        properties.remove(KEY_LAST_REVISION)
        properties.remove(KEY_LAST_FINGERPRINT)
        properties.remove(KEY_LAST_SYNC_AT)
        save(properties)
    }

    /** Whether this PC shows todo reminders as system notifications (on by default). */
    @Synchronized
    fun desktopReminders(): Boolean = load().getProperty(KEY_DESKTOP_REMINDERS)?.toBooleanStrictOrNull() ?: true

    @Synchronized
    fun setDesktopReminders(enabled: Boolean) = load().let { it.setProperty(KEY_DESKTOP_REMINDERS, enabled.toString()); save(it) }

    /** Whether closing the window keeps the app running in the tray (off by default). */
    @Synchronized
    fun keepInTray(): Boolean = load().getProperty(KEY_KEEP_IN_TRAY)?.toBooleanStrictOrNull() ?: false

    @Synchronized
    fun setKeepInTray(enabled: Boolean) = load().let { it.setProperty(KEY_KEEP_IN_TRAY, enabled.toString()); save(it) }

    /** Until when reminders were already checked, so none is shown twice. */
    @Synchronized
    fun remindersCheckedAt(): Long? = load().getProperty(KEY_REMINDERS_CHECKED_AT)?.toLongOrNull()

    @Synchronized
    fun setRemindersCheckedAt(millis: Long) = load().let { it.setProperty(KEY_REMINDERS_CHECKED_AT, millis.toString()); save(it) }

    /** The window's last size and position, or null the first time. */
    @Synchronized
    fun windowBounds(): DesktopWindowBounds? {
        val properties = load()
        val width = properties.getProperty(KEY_WINDOW_WIDTH)?.toIntOrNull() ?: return null
        val height = properties.getProperty(KEY_WINDOW_HEIGHT)?.toIntOrNull() ?: return null
        return DesktopWindowBounds(
            width = width.coerceIn(640, 10_000),
            height = height.coerceIn(480, 10_000),
            x = properties.getProperty(KEY_WINDOW_X)?.toIntOrNull(),
            y = properties.getProperty(KEY_WINDOW_Y)?.toIntOrNull(),
            maximized = properties.getProperty(KEY_WINDOW_MAXIMIZED)?.toBooleanStrictOrNull() ?: false,
        )
    }

    @Synchronized
    fun setWindowBounds(bounds: DesktopWindowBounds) {
        val properties = load()
        properties.setProperty(KEY_WINDOW_WIDTH, bounds.width.toString())
        properties.setProperty(KEY_WINDOW_HEIGHT, bounds.height.toString())
        bounds.x?.let { properties.setProperty(KEY_WINDOW_X, it.toString()) } ?: properties.remove(KEY_WINDOW_X)
        bounds.y?.let { properties.setProperty(KEY_WINDOW_Y, it.toString()) } ?: properties.remove(KEY_WINDOW_Y)
        properties.setProperty(KEY_WINDOW_MAXIMIZED, bounds.maximized.toString())
        save(properties)
    }

    private fun load() = Properties().also { properties ->
        if (file.isFile) file.inputStream().use(properties::load)
    }

    private fun save(properties: Properties) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.part")
        try {
            temporary.outputStream().use { properties.store(it, "Life Assistant desktop settings") }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private companion object {
        const val KEY_CLIENT_ID = "google.desktop.clientId"
        const val KEY_AUTOMATIC = "cloud.automatic"
        const val KEY_DEVICE_ID = "device.id"
        const val KEY_LAST_REVISION = "cloud.lastRevision"
        const val KEY_LAST_FINGERPRINT = "cloud.lastFingerprint"
        const val KEY_LAST_SYNC_AT = "cloud.lastSyncAt"
        const val KEY_UI_LANGUAGE = "ui.language"
        const val KEY_LAST_DESTINATION = "ui.lastDestination"
        const val KEY_HIDDEN_DESTINATIONS = "ui.hiddenDestinations"
        const val KEY_APP_LOCK = "security.appLock"
        const val KEY_APP_LOCK_TIMEOUT = "security.appLockTimeout"
        const val KEY_PROVIDER = "cloud.provider"
        const val KEY_GITHUB_CLIENT_ID = "github.clientId"
        const val KEY_GITHUB_REPOSITORY = "github.repository"
        const val KEY_WINDOW_WIDTH = "window.width"
        const val KEY_WINDOW_HEIGHT = "window.height"
        const val KEY_WINDOW_X = "window.x"
        const val KEY_WINDOW_Y = "window.y"
        const val KEY_WINDOW_MAXIMIZED = "window.maximized"
        const val KEY_DESKTOP_REMINDERS = "reminders.desktop"
        const val KEY_KEEP_IN_TRAY = "window.keepInTray"
        const val KEY_REMINDERS_CHECKED_AT = "reminders.checkedAt"
    }
}
