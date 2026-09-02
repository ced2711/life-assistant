package com.ced2711.lifetracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppModelsTest {
    @Test
    fun `derived title including ellipsis never exceeds forty code points`() {
        val title = deriveTodoTitle("a".repeat(41))

        assertEquals("a".repeat(39) + "…", title)
        assertEquals(40, title.codePointCount(0, title.length))
    }

    @Test
    fun `derived title does not split supplementary Unicode characters`() {
        val emoji = "\uD83D\uDE00"
        val title = deriveTodoTitle(emoji.repeat(41))

        assertEquals(emoji.repeat(39) + "…", title)
        assertEquals(40, title.codePointCount(0, title.length))
    }

    @Test
    fun `exactly forty Unicode code points are not truncated`() {
        val description = "\uD83D\uDE00".repeat(40)

        assertEquals(description, deriveTodoTitle(description))
    }

    @Test
    fun `derived title joins the first nonempty paragraph and stops at a blank line`() {
        val description = "\n \t\n  Buy milk  \r\nand eggs\n  \t  \nIgnore this paragraph"

        assertEquals("Buy milk and eggs", deriveTodoTitle(description))
    }

    @Test
    fun `category path includes ancestors and tolerates missing parents`() {
        val names = mapOf(1L to "Life", 2L to "Home", 3L to "Chores", 4L to "Orphan")
        val parents = mapOf<Long, Long?>(1L to null, 2L to 1L, 3L to 2L, 4L to 99L)

        assertEquals("Life / Home / Chores", categoryPathLabel(3L, names, parents))
        assertEquals("Orphan", categoryPathLabel(4L, names, parents))
        assertNull(categoryPathLabel(99L, names, parents))
    }

    @Test
    fun `category path terminates a parent cycle without repeating nodes`() {
        val names = mapOf(1L to "Child", 2L to "Parent")
        val parents = mapOf<Long, Long?>(1L to 2L, 2L to 1L)

        assertEquals("Parent / Child", categoryPathLabel(1L, names, parents))
    }
}
