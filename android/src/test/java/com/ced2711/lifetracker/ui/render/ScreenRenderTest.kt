package com.ced2711.lifetracker.ui.render

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.ui.theme.TaskLedgerTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders screens to PNG files for design review, without an emulator. Runs only when
 * -Dscreens.dir=<folder> is given (task :android:renderScreens), so normal test runs skip it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private val output: File? = System.getProperty("screens.dir")?.let(::File)

    @Test
    fun smoke() {
        assumeTrue(output != null)
        compose.setContent {
            TaskLedgerTheme(ThemeMode.DARK, AccentColor.TEAL) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Today", style = MaterialTheme.typography.headlineLarge)
                        Text("Rendered without an emulator")
                    }
                }
            }
        }
        save("smoke")
    }

    private fun save(name: String) {
        val directory = requireNotNull(output).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
