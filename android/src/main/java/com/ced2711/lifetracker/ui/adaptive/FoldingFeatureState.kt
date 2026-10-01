package com.ced2711.lifetracker.ui.adaptive

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.collect

/**
 * Observes the folding feature for [activity] only while it is started.
 *
 * The returned state updates for resizing, posture changes, and moving the activity between
 * displays. A null value represents a conventional single display region.
 */
@Composable
fun collectFoldingFeature(activity: ComponentActivity): State<FoldingFeature?> {
    val tracker = remember(activity) { WindowInfoTracker.getOrCreate(activity) }
    return produceState<FoldingFeature?>(initialValue = null, activity, tracker) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            tracker.windowLayoutInfo(activity).collect { layoutInfo ->
                val features = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
                // Either orientation can partition the window. Prefer a real separator over a
                // merely visible/flat fold so safe-pane calculation receives the useful feature.
                value = features.firstOrNull(FoldingFeature::isSeparating)
                    ?: features.firstOrNull()
            }
        }
    }
}
