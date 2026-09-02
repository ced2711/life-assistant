package com.ced2711.lifetracker.domain.model

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoOrganizationTest {
    @Test
    fun tagNormalizationIsCaseInsensitiveAndLocaleIndependent() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            assertEquals("I,\u0131", normalizeTags(listOf("I", "i", "\u0131")))
            assertEquals("i", tagKey(" I "))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun globalTagsMergeRowsAndSeriesCaseInsensitively() {
        assertEquals(
            listOf("Errands", "Games", "School"),
            collectDistinctTodoTags(
                listOf("School, Errands", "school", "Games, errands"),
            ),
        )
    }

    @Test
    fun renameStandardizesSourceAndExistingTargetThenDeduplicates() {
        assertEquals(
            "Home,PERSONAL",
            renameTodoTagCsv("Work,home,PERSONAL", sourceTag = "work", replacementTag = "Home"),
        )
    }

    @Test
    fun renameRejectsACommaDelimitedReplacement() {
        val failure = runCatching {
            renameTodoTagCsv("Work", sourceTag = "Work", replacementTag = "Home,Urgent")
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun deleteRemovesOnlyExactCaseInsensitiveTag() {
        assertEquals("Homework", deleteTodoTagCsv("Home,Homework,HOME", "home"))
    }

    @Test
    fun suggestionsUseOnlyTrailingFragmentAndExcludeSelectedTags() {
        assertEquals(
            listOf("Games", "Gaming"),
            todoTagSuggestions(
                input = "School, ga",
                availableTags = listOf("school", "Games", "Gaming", "Garden"),
                limit = 2,
            ),
        )
        assertEquals("School, Games, ", acceptTodoTagSuggestion("School, ga", "Games"))
        assertEquals(emptyList<String>(), todoTagSuggestions("School, ", listOf("School")))
    }

    @Test
    fun swappingVisibleNeighborsPreservesHiddenTaskOrderAndNormalizesRanks() {
        val reordered = swapTodoIds(
            orderedIds = listOf(1L, 90L, 80L, 2L),
            movingId = 2L,
            adjacentVisibleId = 1L,
        )

        assertEquals(listOf(2L, 90L, 80L, 1L), reordered)
        assertEquals(
            listOf(2L to 4L, 90L to 3L, 80L to 2L, 1L to 1L),
            normalizeCustomTodoOrders(reordered),
        )
    }

    @Test
    fun staleMoveRequestLeavesOrderUnchanged() {
        val ids = listOf(3L, 2L, 1L)
        assertEquals(ids, swapTodoIds(ids, movingId = 3L, adjacentVisibleId = 99L))
    }

    @Test
    fun categoryFilterIncludesSelectedCategoryAndAllDescendantsButNotSiblings() {
        val parentIdsById = mapOf(
            1L to null,
            2L to 1L,
            3L to 2L,
            4L to 3L,
            5L to 1L,
        )

        assertEquals(
            setOf<Long?>(2L, 3L, 4L),
            categoryIdsIncludedByTodoFilter(2L, parentIdsById),
        )
    }

    @Test
    fun uncategorizedFilterIncludesOnlyTasksWithoutACategory() {
        val parentIdsById = mapOf(1L to null, 2L to 1L)

        assertEquals(
            setOf<Long?>(null),
            categoryIdsIncludedByTodoFilter(null, parentIdsById),
        )
    }

    @Test
    fun categoryFilterTerminatesSafelyForCyclesAndOrphans() {
        val parentIdsById = mapOf(
            1L to 2L,
            2L to 1L,
            3L to 2L,
            4L to 5L,
            5L to 4L,
            6L to 99L,
        )

        assertEquals(
            setOf<Long?>(1L, 2L, 3L),
            categoryIdsIncludedByTodoFilter(1L, parentIdsById),
        )
    }
}
