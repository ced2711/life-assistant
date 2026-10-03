package com.ced2711.lifetracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.UiLanguage
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the home-screen widget in its sizes to PNG files for design review, without a launcher.
 * Runs only with -Dscreens.dir=<folder>; pictures land in <folder>/widget-<theme>-<language>/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = android.app.Application::class)
class WidgetRenderTest {
    private val output: File? = System.getProperty("screens.dir")?.let(::File)
    private val theme = if (System.getProperty("screens.theme") == "light") ThemeMode.LIGHT else ThemeMode.DARK
    private val language = if (System.getProperty("screens.language") == "zh") UiLanguage.SIMPLIFIED_CHINESE else UiLanguage.ENGLISH

    private val today: LocalDate = LocalDate.now()
    private val day = today.toEpochDay()
    private val todos = listOf(
        TodoEntity(id = 1, title = "Renew passport", description = "", deadlineEpochDay = day - 2, priority = TodoPriority.URGENT),
        TodoEntity(id = 2, title = "Water the plants", description = "", deadlineEpochDay = day, deadlineMinute = 18 * 60, seriesId = 1),
        TodoEntity(id = 3, title = "Send the quarterly report to the client", description = "", deadlineEpochDay = day, priority = TodoPriority.HIGH),
        TodoEntity(id = 4, title = "Morning walk", description = "", deadlineEpochDay = day, deadlineMinute = 7 * 60 + 30, priority = TodoPriority.MEDIUM),
        TodoEntity(id = 5, title = "Call the dentist", description = "", deadlineEpochDay = day, priority = TodoPriority.LOW),
    )
    private val done = listOf(
        TodoEntity(id = 8, title = "Buy groceries", description = "", completedAt = today.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()),
        TodoEntity(id = 9, title = "Pay rent", description = "", completedAt = today.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()),
    )

    private fun state(active: List<TodoEntity>, completed: List<TodoEntity>, ready: Boolean = true) = WidgetState(
        model = if (ready) buildWidgetModel(active, completed, today, language, TimeFormatOption.HOUR_12, systemUses24Hour = false) else null,
        language = language,
        palette = widgetPalette(theme, AccentColor.TEAL),
    )

    @Test
    fun widget() {
        assumeTrue(output != null)
        val directory = File(requireNotNull(output), "widget-${theme.name.lowercase()}-${if (language == UiLanguage.ENGLISH) "en" else "zh"}").apply { mkdirs() }
        val full = state(todos, done)
        val allDone = state(emptyList(), done)
        val nothing = state(emptyList(), emptyList())
        listOf(
            Triple("1x1-tiny", DpSize(110.dp, 56.dp), full),
            Triple("1x1-tiny-done", DpSize(110.dp, 56.dp), allDone),
            Triple("4x1-wide", DpSize(320.dp, 72.dp), full),
            Triple("4x1-wide-done", DpSize(320.dp, 72.dp), allDone),
            Triple("2x2-small", DpSize(150.dp, 160.dp), full),
            Triple("4x2", DpSize(320.dp, 170.dp), full),
            Triple("4x3", DpSize(320.dp, 250.dp), full),
            Triple("4x3-done", DpSize(320.dp, 250.dp), allDone),
            Triple("4x3-nothing", DpSize(320.dp, 250.dp), nothing),
            Triple("4x3-recovering", DpSize(320.dp, 250.dp), state(emptyList(), emptyList(), ready = false)),
            Triple("3x4-tall", DpSize(250.dp, 360.dp), full),
        ).forEach { (name, size, state) -> render(File(directory, "$name.png"), size, state) }
    }

    @OptIn(ExperimentalGlanceApi::class)
    private fun render(file: File, size: DpSize, state: WidgetState) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val widget = object : GlanceAppWidget() {
            override suspend fun provideGlance(context: Context, id: GlanceId) {
                // A scrolling list is filled in by the launcher and stays empty here: lay the rows out instead.
                provideContent { TodayWidgetContent(context, state, scrolling = false) }
            }
        }
        val views = runBlocking { widget.compose(context, size = size) }
        val density = context.resources.displayMetrics.density
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
        val host = FrameLayout(context)
        host.addView(views.apply(context, host))
        repeat(3) {
            host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, width, height)
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        host.draw(Canvas(bitmap))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
