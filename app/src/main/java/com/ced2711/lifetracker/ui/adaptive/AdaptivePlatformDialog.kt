package com.ced2711.lifetracker.ui.adaptive

import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

/** Pixel bounds applied to a platform dialog window inside one safe foldable pane. */
internal data class PlatformDialogWindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * Returns a launcher that preserves conventional platform-dialog behavior on ordinary windows and
 * centers dialogs inside the requested unobstructed pane when a separating fold is present.
 */
@Composable
fun rememberHingeSafePlatformDialogLauncher(
    panePreference: SafePanePreference = SafePanePreference.PRIMARY,
): (Dialog) -> Unit {
    val safePaneLayout = LocalSafePaneLayout.current
    val density = LocalDensity.current.density
    val controller = remember { HingeSafePlatformDialogController() }

    SideEffect {
        controller.updateLayout(
            safePaneLayout = safePaneLayout,
            panePreference = panePreference,
            density = density,
        )
    }
    DisposableEffect(controller) {
        onDispose(controller::dispose)
    }

    return remember(controller) { controller::show }
}

/** Keeps one shown platform dialog attached to live safe-pane geometry without recreating it. */
private class HingeSafePlatformDialogController {
    private var safePaneLayout: SafePaneLayout? = null
    private var panePreference: SafePanePreference = SafePanePreference.PRIMARY
    private var density: Float = 1f
    private var activeSession: ActivePlatformDialogSession? = null

    fun show(dialog: Dialog) {
        val current = activeSession
        if (current?.dialog?.isShowing == true) return
        current?.detach()

        if (!dialog.isShowing) dialog.show()
        val window = dialog.window ?: return
        val session = ActivePlatformDialogSession(
            dialog = dialog,
            window = window,
            initialDensity = density,
            onDetached = ::clearIfActive,
        )
        activeSession = session
        session.attach()
        session.updateLayout(safePaneLayout, panePreference, density)
    }

    fun updateLayout(
        safePaneLayout: SafePaneLayout?,
        panePreference: SafePanePreference,
        density: Float,
    ) {
        this.safePaneLayout = safePaneLayout
        this.panePreference = panePreference
        this.density = density
        activeSession?.updateLayout(safePaneLayout, panePreference, density)
    }

    fun dispose() {
        val session = activeSession ?: return
        activeSession = null
        session.detach()
        if (session.dialog.isShowing) session.dialog.dismiss()
    }

    private fun clearIfActive(session: ActivePlatformDialogSession) {
        if (activeSession === session) activeSession = null
    }
}

private class ActivePlatformDialogSession(
    val dialog: Dialog,
    private val window: Window,
    initialDensity: Float,
    private val onDetached: (ActivePlatformDialogSession) -> Unit,
) : View.OnLayoutChangeListener, View.OnAttachStateChangeListener {
    private val baseline = PlatformDialogWindowPlacement.from(window.attributes)
    private var safePaneLayout: SafePaneLayout? = null
    private var panePreference: SafePanePreference = SafePanePreference.PRIMARY
    private var density: Float = initialDensity
    private var preferredWidthDp: Float? = null
    private var preferredHeightDp: Float? = null
    private var attached = false

    fun attach() {
        if (attached) return
        attached = true
        window.decorView.addOnLayoutChangeListener(this)
        window.decorView.addOnAttachStateChangeListener(this)
    }

    fun detach() {
        if (!attached) return
        attached = false
        window.decorView.removeOnLayoutChangeListener(this)
        window.decorView.removeOnAttachStateChangeListener(this)
    }

    fun updateLayout(
        safePaneLayout: SafePaneLayout?,
        panePreference: SafePanePreference,
        density: Float,
    ) {
        this.safePaneLayout = safePaneLayout
        this.panePreference = panePreference
        this.density = density
        applyCurrentLayout()
    }

    override fun onLayoutChange(
        view: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int,
    ) {
        capturePreferredSize(view)
        applyCurrentLayout()
    }

    override fun onViewAttachedToWindow(view: View) = Unit

    override fun onViewDetachedFromWindow(view: View) {
        detach()
        onDetached(this)
    }

    private fun capturePreferredSize(decorView: View) {
        if (preferredWidthDp != null || preferredHeightDp != null || density <= 0f) return
        if (decorView.width <= 0 || decorView.height <= 0) return
        preferredWidthDp = decorView.width / density
        preferredHeightDp = decorView.height / density
    }

    private fun applyCurrentLayout() {
        if (!dialog.isShowing) return
        val layout = safePaneLayout
        if (layout?.hasSeparatingFeature != true) {
            baseline.applyTo(window)
            return
        }

        val decorView = window.decorView
        capturePreferredSize(decorView)
        val dialogWidthPx = preferredWidthDp?.times(density)?.roundToInt() ?: decorView.width
        val dialogHeightPx = preferredHeightDp?.times(density)?.roundToInt() ?: decorView.height
        val bounds = calculatePlatformDialogWindowBounds(
            safePaneLayout = layout,
            panePreference = panePreference,
            density = density,
            dialogWidthPx = dialogWidthPx,
            dialogHeightPx = dialogHeightPx,
        ) ?: return

        PlatformDialogWindowPlacement(
            gravity = Gravity.TOP or Gravity.LEFT,
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
        ).applyTo(window)
    }
}

private data class PlatformDialogWindowPlacement(
    val gravity: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun applyTo(window: Window) {
        val current = window.attributes
        if (
            current.gravity == gravity &&
            current.x == x &&
            current.y == y &&
            current.width == width &&
            current.height == height
        ) {
            return
        }

        window.attributes = current.apply {
            gravity = this@PlatformDialogWindowPlacement.gravity
            x = this@PlatformDialogWindowPlacement.x
            y = this@PlatformDialogWindowPlacement.y
            width = this@PlatformDialogWindowPlacement.width
            height = this@PlatformDialogWindowPlacement.height
        }
    }

    companion object {
        fun from(attributes: WindowManager.LayoutParams) = PlatformDialogWindowPlacement(
            gravity = attributes.gravity,
            x = attributes.x,
            y = attributes.y,
            width = attributes.width,
            height = attributes.height,
        )
    }
}

/** Pure geometry used by the Android window adapter and local unit tests. */
internal fun calculatePlatformDialogWindowBounds(
    safePaneLayout: SafePaneLayout?,
    panePreference: SafePanePreference,
    density: Float,
    dialogWidthPx: Int,
    dialogHeightPx: Int,
): PlatformDialogWindowBounds? {
    if (safePaneLayout?.hasSeparatingFeature != true || density <= 0f || !density.isFinite()) {
        return null
    }

    val windowWidthPx = safePaneLayout.windowWidth.toPixels(density).coerceAtLeast(0)
    val windowHeightPx = safePaneLayout.windowHeight.toPixels(density).coerceAtLeast(0)
    if (windowWidthPx == 0 || windowHeightPx == 0) return null

    val requestedPane = when (panePreference) {
        SafePanePreference.PRIMARY -> safePaneLayout.primaryPane
        SafePanePreference.SECONDARY -> safePaneLayout.secondaryPane ?: safePaneLayout.primaryPane
    }
    val paneLeft = requestedPane.left.toPixels(density).coerceIn(0, windowWidthPx)
    val paneTop = requestedPane.top.toPixels(density).coerceIn(0, windowHeightPx)
    val paneRight = requestedPane.right.toPixels(density).coerceIn(paneLeft, windowWidthPx)
    val paneBottom = requestedPane.bottom.toPixels(density).coerceIn(paneTop, windowHeightPx)
    val paneWidth = paneRight - paneLeft
    val paneHeight = paneBottom - paneTop
    if (paneWidth == 0 || paneHeight == 0 || dialogWidthPx <= 0 || dialogHeightPx <= 0) return null

    val width = dialogWidthPx.coerceAtMost(paneWidth)
    val height = dialogHeightPx.coerceAtMost(paneHeight)
    return PlatformDialogWindowBounds(
        x = paneLeft + (paneWidth - width) / 2,
        y = paneTop + (paneHeight - height) / 2,
        width = width,
        height = height,
    )
}

private fun androidx.compose.ui.unit.Dp.toPixels(density: Float): Int =
    (value * density).roundToInt()
