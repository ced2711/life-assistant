package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.domain.model.AppIdentity
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLegalResourcesTest {
    @Test fun licenseAndNoticesAreAvailableOffline() {
        fun read(path: String) = requireNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Missing bundled legal resource: $path"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertTrue(read(AppIdentity.LICENSE_RESOURCE).contains("is proprietary"))
        assertTrue(read(AppIdentity.NOTICE_RESOURCE).contains("Copyright 2026 ced2711"))
    }
}
