package com.ced2711.lifetracker

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies branding changes do not remove launcher compatibility or bundled legal material. */
@RunWith(AndroidJUnit4::class)
class BrandingCompatibilityInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun applicationLabelUsesLifeAssistant() {
        val applicationInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )

        assertEquals("Life Assistant", applicationInfo.loadLabel(context.packageManager).toString())
    }

    @Test
    fun legacyLauncherAliasComponentNamesRemainStable() {
        val aliases = listOf(
            "com.ced2711.lifetracker.launcher.LifeTrackerQuiet",
            "com.ced2711.lifetracker.launcher.LifeTrackerMomentum",
            "com.ced2711.lifetracker.launcher.LifeTrackerComplete",
        )
        aliases.forEach { alias ->
            val info = context.packageManager.getActivityInfo(
                ComponentName(context.packageName, alias),
                PackageManager.GET_META_DATA or PackageManager.GET_DISABLED_COMPONENTS,
            )
            assertEquals(alias, info.name)
        }
    }

    @Test
    fun bundledLegalAssetsContainLicensePermissionAndNotice() {
        val license = readAsset("legal/LICENSE")
        val permission = readAsset("legal/ADDITIONAL_PERMISSIONS.md")
        val notice = readAsset("legal/NOTICE")

        assertTrue(license.contains("13. Remote Network Interaction"))
        assertTrue(permission.contains("Additional permission under GNU AGPL version 3, section 7"))
        assertTrue(permission.contains("Google Play"))
        assertTrue(notice.contains("Life Assistant"))
        assertTrue(notice.contains("WITHOUT ANY WARRANTY"))
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
