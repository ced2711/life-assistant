package com.ced2711.lifetracker.domain.model

import java.util.Locale

internal fun tagKey(tag: String): String = tag.trim().lowercase(Locale.ROOT)

internal fun collectDistinctTodoTags(csvValues: Iterable<String>): List<String> {
    val tagsByKey = linkedMapOf<String, String>()
    csvValues.forEach { csv ->
        parseTags(csv).forEach { tag ->
            tagsByKey.putIfAbsent(tagKey(tag), tag)
        }
    }
    return tagsByKey.values.sortedWith(compareBy(::tagKey).thenBy { it })
}

internal fun renameTodoTagCsv(
    csv: String,
    sourceTag: String,
    replacementTag: String,
): String {
    val sourceKey = tagKey(sourceTag)
    val cleanReplacement = replacementTag.trim()
    val replacementKey = tagKey(cleanReplacement)
    require(sourceKey.isNotEmpty()) { "Source tag is required" }
    require(replacementKey.isNotEmpty()) { "Replacement tag is required" }
    require(',' !in cleanReplacement) { "A tag name cannot contain commas" }
    return normalizeTags(
        parseTags(csv).map { tag ->
            if (tagKey(tag) == sourceKey || tagKey(tag) == replacementKey) {
                cleanReplacement
            } else {
                tag
            }
        },
    )
}

internal fun deleteTodoTagCsv(csv: String, tagToDelete: String): String {
    val deletedKey = tagKey(tagToDelete)
    require(deletedKey.isNotEmpty()) { "Tag is required" }
    return normalizeTags(parseTags(csv).filterNot { tagKey(it) == deletedKey })
}

internal fun todoTagSuggestions(
    input: String,
    availableTags: Iterable<String>,
    limit: Int = 6,
): List<String> {
    require(limit >= 0) { "Suggestion limit must not be negative" }
    val parts = input.split(',')
    val fragmentKey = tagKey(parts.lastOrNull().orEmpty())
    if (fragmentKey.isEmpty() || limit == 0) return emptyList()
    val existingKeys = parts.dropLast(1).mapTo(mutableSetOf(), ::tagKey)
    return availableTags
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(::tagKey)
        .filter { tag -> tagKey(tag).startsWith(fragmentKey) && tagKey(tag) !in existingKeys }
        .sortedWith(compareBy(::tagKey).thenBy { it })
        .take(limit)
        .toList()
}

internal fun acceptTodoTagSuggestion(input: String, suggestion: String): String {
    val cleanSuggestion = suggestion.trim()
    require(cleanSuggestion.isNotEmpty()) { "Suggestion is required" }
    val lastComma = input.lastIndexOf(',')
    val prefix = if (lastComma >= 0) input.substring(0, lastComma + 1).trimEnd() + " " else ""
    return "$prefix$cleanSuggestion, "
}

internal fun swapTodoIds(
    orderedIds: List<Long>,
    movingId: Long,
    adjacentVisibleId: Long,
): List<Long> {
    if (movingId == adjacentVisibleId) return orderedIds
    val movingIndex = orderedIds.indexOf(movingId)
    val adjacentIndex = orderedIds.indexOf(adjacentVisibleId)
    if (movingIndex < 0 || adjacentIndex < 0) return orderedIds
    return orderedIds.toMutableList().apply {
        this[movingIndex] = adjacentVisibleId
        this[adjacentIndex] = movingId
    }
}

internal fun normalizeCustomTodoOrders(orderedIds: List<Long>): List<Pair<Long, Long>> =
    orderedIds.mapIndexed { index, id -> id to (orderedIds.size - index).toLong() }

internal fun categoryIdsIncludedByTodoFilter(
    selectedCategoryId: Long?,
    parentIdsById: Map<Long, Long?>,
): Set<Long?> {
    if (selectedCategoryId == null) return setOf(null)
    return buildSet {
        add(selectedCategoryId)
        parentIdsById.keys.forEach { candidateId ->
            val visited = mutableSetOf<Long>()
            var cursor: Long? = candidateId
            while (cursor != null && visited.add(cursor)) {
                if (cursor == selectedCategoryId) {
                    add(candidateId)
                    break
                }
                cursor = parentIdsById[cursor]
            }
        }
    }
}
