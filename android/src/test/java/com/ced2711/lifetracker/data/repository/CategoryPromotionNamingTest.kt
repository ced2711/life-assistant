package com.ced2711.lifetracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryPromotionNamingTest {
    @Test
    fun `keeps preferred name when target parent has no collision`() {
        assertEquals("Games", uniquePromotedCategoryName("Games", setOf("School")))
    }

    @Test
    fun `uses first deterministic numeric suffix not already occupied`() {
        assertEquals(
            "Games (4)",
            uniquePromotedCategoryName("Games", setOf("Games", "Games (2)", "Games (3)")),
        )
    }
}
