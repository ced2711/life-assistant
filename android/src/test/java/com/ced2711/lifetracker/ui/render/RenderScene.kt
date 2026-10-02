package com.ced2711.lifetracker.ui.render

import androidx.compose.runtime.Composable
import com.ced2711.lifetracker.domain.model.TopLevelDestination

/**
 * One picture for design review: a screen's stateless content with sample data, shown inside the
 * app's real navigation. Each group of screens lists its scenes in a file of its own
 * (TodoScenes.kt, LedgerScenes.kt, …) so they can be worked on independently.
 *
 * [name] becomes the file name. [destination] is the module selected in the navigation, or null
 * with [auxiliaryTitle] for Settings, Vault and Backup & sync. [content] gets whether the pane is
 * wide enough for a two-pane layout, as MainActivity passes `isWide`.
 */
class RenderScene(
    val name: String,
    val destination: TopLevelDestination? = null,
    val auxiliaryTitle: String? = null,
    val content: @Composable (isWide: Boolean) -> Unit,
)

/** Every scene, in the order the pictures are numbered. */
internal val allRenderScenes: List<RenderScene>
    get() = shellScenes + todoScenes + ledgerScenes + calendarScenes + notesScenes + vaultScenes + settingsScenes
