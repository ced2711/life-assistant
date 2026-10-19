package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.domain.model.AppIdentity
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLegalResourcesTest {
    @Test fun licenseAndAdditionalPermissionAreAvailableOffline() {
        fun read(path: String) = requireNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Missing bundled legal resource: $path"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertTrue(read(AppIdentity.LICENSE_RESOURCE).contains("13. Remote Network Interaction"))
        assertTrue(read(AppIdentity.PERMISSION_RESOURCE).replace(Regex("\\s+"), " ").contains("Google Play services"))
        assertTrue(read(AppIdentity.NOTICE_RESOURCE).contains("Copyright 2026 ced2711"))
    }
}
