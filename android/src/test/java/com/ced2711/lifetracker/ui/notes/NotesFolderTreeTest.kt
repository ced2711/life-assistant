package com.ced2711.lifetracker.ui.notes

import com.ced2711.lifetracker.data.local.NoteFolderEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesFolderTreeTest {
    @Test
    fun `folders are flattened parent first at any depth`() {
        val rows = flattenNoteFolders(
            listOf(
                NoteFolderEntity(3, "Passwords", 2, 0, 3),
                NoteFolderEntity(1, "Life", null, 0, 1),
                NoteFolderEntity(2, "Reference", 1, 0, 2),
                NoteFolderEntity(4, "School", null, 1, 4),
            ),
        )

        assertEquals(listOf(1L, 2L, 3L, 4L), rows.map { it.folder.id })
        assertEquals(listOf(0, 1, 2, 0), rows.map { it.depth })
    }

    @Test
    fun `corrupt cycles remain visible without infinite recursion`() {
        val rows = flattenNoteFolders(
            listOf(
                NoteFolderEntity(1, "One", 2, 0, 1),
                NoteFolderEntity(2, "Two", 1, 0, 2),
            ),
        )

        assertEquals(setOf(1L, 2L), rows.map { it.folder.id }.toSet())
    }
}
