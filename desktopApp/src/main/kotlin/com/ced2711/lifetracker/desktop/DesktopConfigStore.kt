package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.cloudsync.LocalCloudSyncState
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.UiLanguage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

data class DesktopCloudConfig(
    val clientId: String,
    val automaticSync: Boolean,
    val syncState: LocalCloudSyncState,
    val uiLanguage: UiLanguage = UiLanguage.ENGLISH,
    val lastDestination: TopLevelDestination = TopLevelDestination.TODO,
)

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

    private fun load() = Properties().also { properties ->
        if (file.isFile) file.inputStream().use(properties::load)
    }

    private fun save(properties: Properties) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.part")
        try {
            temporary.outputStream().use { properties.store(it, "Life Tracker desktop settings") }
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
    }
}
