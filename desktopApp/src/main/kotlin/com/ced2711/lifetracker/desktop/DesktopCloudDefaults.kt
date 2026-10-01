package com.ced2711.lifetracker.desktop

import java.util.Properties

/**
 * Sign-in clients built into release installers (see `generateCloudDefaults` in the build
 * script). When present, connecting a cloud needs no typing at all.
 */
data class DesktopCloudDefaults(
    val googleClientId: String = "",
    val googleClientSecret: String = "",
    val gitHubClientId: String = "",
) {
    val hasGoogle: Boolean get() = googleClientId.endsWith(".apps.googleusercontent.com") && googleClientSecret.isNotBlank()
    val hasGitHub: Boolean get() = gitHubClientId.isNotBlank()

    companion object {
        val builtIn: DesktopCloudDefaults by lazy {
            val properties = Properties()
            DesktopCloudDefaults::class.java.getResourceAsStream("/life-assistant-cloud.properties")
                ?.use(properties::load)
            DesktopCloudDefaults(
                googleClientId = properties.getProperty("google.desktop.clientId").orEmpty().trim(),
                googleClientSecret = properties.getProperty("google.desktop.clientSecret").orEmpty().trim(),
                gitHubClientId = properties.getProperty("github.clientId").orEmpty().trim(),
            )
        }
    }
}
