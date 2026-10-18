package com.ced2711.lifetracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {
    @Test fun brandingIdentifiesTheRelease() {
        assertEquals("Life Assistant", AppIdentity.NAME)
        assertEquals("2.0.6", AppIdentity.VERSION)
        assertEquals("ced2711", AppIdentity.AUTHOR)
        assertEquals("All rights reserved", AppIdentity.LICENSE_LABEL)
    }
}
