package com.ced2711.lifetracker.ui.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import com.ced2711.lifetracker.data.cloud.CloudSyncIndicator
import com.ced2711.lifetracker.data.cloud.CloudSyncIndicatorState
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.adaptive.AdaptiveTaskLedgerScaffold
import com.ced2711.lifetracker.ui.adaptive.TopBarSyncStatus
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.domain.model.TodayOverview
import com.ced2711.lifetracker.ui.theme.TaskLedgerTheme
import com.ced2711.lifetracker.ui.today.TodayGlanceContent
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every [RenderScene] to PNG files for design review, without an emulator. Runs only when
 * -Dscreens.dir=<folder> is given, so normal test runs skip it:
 *
 * ./gradlew :android:testStandardDebugUnitTest --tests '*ScreenRenderTest.phone' -Dscreens.dir=<folder>
 *     [-Dscreens.theme=light] [-Dscreens.language=zh] [-Dscreens.only=<part of a scene name>]
 *
 * Pictures land in <folder>/<device>-<theme>-<language>/<scene>.png. Devices: phone, smallPhone,
 * landscapePhone, tablet, foldBook (vertical hinge), foldTabletop (horizontal hinge), largeFont.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private val output: File? = System.getProperty("screens.dir")?.let(::File)
    private val theme = if (System.getProperty("screens.theme") == "light") ThemeMode.LIGHT else ThemeMode.DARK
    private val language = if (System.getProperty("screens.language") == "zh") UiLanguage.SIMPLIFIED_CHINESE else UiLanguage.ENGLISH
    private val only: String? = System.getProperty("screens.only")

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phone() = render("phone")

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    fun smallPhone() = render("smallPhone")

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xhdpi")
    fun landscapePhone() = render("landscapePhone")

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tablet() = render("tablet")

    /** Wide enough for the two-pane layouts (isWide). */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletLandscape() = render("tabletLandscape")

    /** An unfolded book-style foldable held half open: the hinge runs top to bottom. */
    @Test
    @Config(qualifiers = "w841dp-h701dp-xhdpi")
    fun foldBook() = render("foldBook", fold = FoldingFeature.Orientation.VERTICAL)

    /** A flip phone standing on a table: the hinge runs left to right. */
    @Test
    @Config(qualifiers = "w412dp-h1004dp-xhdpi")
    fun foldTabletop() = render("foldTabletop", fold = FoldingFeature.Orientation.HORIZONTAL)

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 1.5f)
    fun largeFont() = render("largeFont")

    private fun render(device: String, fold: FoldingFeature.Orientation? = null) {
        assumeTrue(output != null)
        val scenes = allRenderScenes.filter { only == null || it.name.contains(only, ignoreCase = true) }
        assumeTrue(scenes.isNotEmpty())
        var index by mutableIntStateOf(0)
        compose.setContent {
            val scene = scenes[index]
            CompositionLocalProvider(LocalUiLanguage provides language) {
                TaskLedgerTheme(theme, AccentColor.TEAL) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val hinge = fold?.let { orientation ->
                            val width = with(density) { maxWidth.roundToPx() }
                            val height = with(density) { maxHeight.roundToPx() }
                            if (orientation == FoldingFeature.Orientation.VERTICAL) {
                                FakeFold(Rect(width / 2, 0, width / 2, height), orientation)
                            } else {
                                FakeFold(Rect(0, height / 2, width, height / 2), orientation)
                            }
                        }
                        AdaptiveTaskLedgerScaffold(
                            selected = scene.destination ?: TopLevelDestination.TODAY,
                            onSelected = {},
                            onSettings = {},
                            isSettings = scene.destination == null,
                            auxiliaryTitle = scene.auxiliaryTitle,
                            foldingFeature = hinge,
                            destinations = TopLevelDestination.entries.filterNot { it == TopLevelDestination.CONFESSIONAL || it == TopLevelDestination.DIARY } +
                                listOfNotNull(scene.destination?.takeIf { it == TopLevelDestination.CONFESSIONAL || it == TopLevelDestination.DIARY }),
                            syncStatus = TopBarSyncStatus(CloudSyncIndicator(CloudSyncIndicatorState.UP_TO_DATE, null), {}, {}),
                            glance = {
                                TodayGlanceContent(TodayOverview.of(RenderSamples.todos, RenderSamples.ledger, RenderSamples.today), RenderSamples.settings, showLedger = true)
                            },
                        ) { padding ->
                            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                                scene.content(maxWidth >= 840.dp)
                            }
                        }
                    }
                }
            }
        }
        val directory = File(requireNotNull(output), "$device-${theme.name.lowercase()}-${if (language == UiLanguage.ENGLISH) "en" else "zh"}").apply { mkdirs() }
        scenes.forEachIndexed { position, scene ->
            index = position
            compose.waitForIdle()
            // A scene that opens a dialog (an editor, a confirmation) is drawn in the last window.
            val roots = compose.onAllNodes(isRoot())
            val image = roots.onFirst().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
            // A scene that opens a dialog (an editor, a confirmation) has more windows: draw each
            // over the page, dimmed as on a device. captureToImage only sees the first window.
            val canvas = Canvas(image)
            roots.fetchSemanticsNodes().drop(1).forEach { node ->
                val window = (node.root as ViewRootForTest).view.rootView
                canvas.drawColor(0x99000000.toInt())
                val location = IntArray(2).also(window::getLocationOnScreen)
                canvas.save()
                canvas.translate(location[0].toFloat(), location[1].toFloat())
                window.draw(canvas)
                canvas.restore()
            }
            File(directory, "%02d-%s.png".format(position + 1, scene.name)).outputStream().use { stream ->
                image.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
    }

    /** A hinge without width across the middle of the window, half opened. */
    private class FakeFold(override val bounds: Rect, override val orientation: FoldingFeature.Orientation) : FoldingFeature {
        override val isSeparating = true
        override val occlusionType = FoldingFeature.OcclusionType.NONE
        override val state = FoldingFeature.State.HALF_OPENED
    }
}
