package com.ced2711.lifetracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIdentityTest {
    @Test fun brandingAndSourceOfferIdentifyTheSameRelease() {
        assertEquals("Life Assistant", AppIdentity.NAME)
        assertEquals("1.7.0", AppIdentity.VERSION)
        assertEquals("ced2711", AppIdentity.AUTHOR)
        assertEquals("https://github.com/ced2711/life-assistant/tree/v1.7.0", AppIdentity.SOURCE_URL)
        assertTrue(AppIdentity.LICENSE_LABEL.startsWith("AGPL-3.0-only"))
    }
}
