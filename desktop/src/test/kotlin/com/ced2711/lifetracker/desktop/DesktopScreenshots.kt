package com.ced2711.lifetracker.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import java.io.File
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders every page of the desktop app to PNG files, for reviewing the layout without a
 * window. Run through the `renderScreens` task, which points APPDATA at prepared sample data.
 */
fun main(args: Array<String>) {
    require(args.size == 3) { "Pass the output directory, width and height." }
    val output = File(args[0]).apply { mkdirs() }
    val width = args[1].toInt()
    val height = args[2].toInt()
    DesktopConfigStore().setVisibleDestinations(TopLevelDestination.entries.toSet())
    val shortcuts = DesktopShortcuts()
    val scene = ImageComposeScene(width, height, Density(1f)) {
        CompositionLocalProvider(LocalDesktopShortcuts provides shortcuts) {
            LifeTrackerDesktopApp()
        }
    }
    var time = 0L
    fun settle(steps: Int = 40) = repeat(steps) {
        scene.render(time)
        time += 50_000_000L
        Thread.sleep(50)
    }
    fun save(name: String) {
        settle()
        val image = scene.render(time)
        val data = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
        File(output, "$name.png").writeBytes(data.bytes)
        println(File(output, "$name.png").absolutePath)
    }
    settle(80)
    TopLevelDestination.entries.forEachIndexed { index, destination ->
        shortcuts.onNavigate?.invoke(index)
        save("${index + 1}-${destination.name.lowercase()}")
        // Pages with an editor also show it, as opened with Ctrl+N.
        if (destination == TopLevelDestination.TODO || destination == TopLevelDestination.NOTES) {
            shortcuts.onNew?.invoke()
            save("${index + 1}b-${destination.name.lowercase()}-new")
        }
    }
    shortcuts.onSettings?.invoke()
    save("9-settings")
    scene.close()
    kotlin.system.exitProcess(0)
}
