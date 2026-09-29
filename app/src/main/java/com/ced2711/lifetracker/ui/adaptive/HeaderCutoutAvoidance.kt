package com.ced2711.lifetracker.ui.adaptive

import androidx.compose.runtime.staticCompositionLocalOf

/** Window-pixel bounds of a small top camera island the header may paint behind. */
internal data class CutoutIsland(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Provided only where the header sits directly under a camera island it must avoid itself. */
internal val LocalHeaderCutoutIsland = staticCompositionLocalOf<CutoutIsland?> { null }

/** Extra header padding, in pixels, that keeps the title and trailing action clear of a camera. */
internal data class HeaderCutoutPadding(
    val start: Int = 0,
    val titleEnd: Int = 0,
    val end: Int = 0,
)

/**
 * The header row is `[title (fills)] [trailing action]`. A camera island that overlaps the row
 * pushes the whole row right when it sits over the start of the title, the trailing action left
 * when it sits over the action, and otherwise only shortens the title so it ellipsizes before it.
 */
internal fun headerCutoutPadding(
    barLeft: Int,
    barTop: Int,
    barRight: Int,
    barBottom: Int,
    titleStart: Int,
    trailingWidth: Int,
    minimumTitleWidth: Int,
    gap: Int,
    island: CutoutIsland?,
): HeaderCutoutPadding {
    if (
        island == null ||
        island.bottom <= barTop || island.top >= barBottom ||
        island.right <= barLeft || island.left >= barRight
    ) {
        return HeaderCutoutPadding()
    }
    val width = barRight - barLeft
    val islandStart = island.left - barLeft - gap
    val islandEnd = island.right - barLeft + gap
    val trailingStart = width - trailingWidth
    return when {
        islandEnd > trailingStart -> HeaderCutoutPadding(end = (width - islandStart).coerceIn(0, width))
        islandStart < titleStart + minimumTitleWidth -> HeaderCutoutPadding(start = islandEnd.coerceIn(0, width))
        else -> HeaderCutoutPadding(titleEnd = (trailingStart - islandStart).coerceAtLeast(0))
    }
}
