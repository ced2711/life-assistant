package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.domain.model.UiLanguage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every fixed English UI text in the desktop sources must have a Simplified Chinese translation.
 * It scans the sources for desktopText("…") and for the label tables ("…" after ->), so a new
 * label without a translation fails here instead of showing English in the Chinese UI.
 */
class DesktopTranslationCoverageTest {
    private val sources = File("src/main/kotlin/com/ced2711/lifetracker/desktop").walkTopDown().filter { it.extension == "kt" }.toList()

    // Texts that are meant to look the same in both languages.
    private val sameInBothLanguages = setOf("GitHub", "Ctrl+N", "OK", "9:30", "Google Drive", "English", "简体中文", "Life Assistant")

    private fun candidates(): Set<String> {
        val direct = Regex("""desktopText\(\s*"((?:[^"\\$]|\\.)+)"""")
        val label = Regex("""->\s*"([A-Z][^"\\$]*)"""")
        val found = HashSet<String>()
        sources.forEach { file ->
            val text = file.readText()
            direct.findAll(text).forEach { found += it.groupValues[1] }
            // Every English literal on a line that shows text, e.g. desktopText(if (x) "A" else "B").
            val shows = Regex("""desktopText\(|PageHeader\(|FilterSectionTitle\(|SeriesScopeDialog\(|StatCard\(|DataPasswordDialog\(|title = "|message = """")
            text.lines().filter { shows.containsMatchIn(it) && !it.trimStart().startsWith("//") }.forEach { line ->
                Regex(""""([A-Z][^"\$]*[a-z.?)…][^"\$]*)"""").findAll(line).forEach { found += it.groupValues[1] }
            }
            // Label tables: functions and enums whose values are passed to desktopText.
            if (file.name in setOf("DesktopTodoPage.kt", "DesktopLedgerPage.kt", "DesktopCalendarPage.kt", "DesktopSettingsParts.kt", "DesktopUiHelpers.kt", "DesktopSidebar.kt")) {
                label.findAll(text).forEach { found += it.groupValues[1] }
                Regex("""\w+\("([A-Z][^"\\$]*)"\)[,;]""").findAll(text).forEach { found += it.groupValues[1] }
            }
        }
        return found.map { it.replace("\\\"", "\"").replace("\\n", "\n") }.filterNot { it in sameInBothLanguages || it.length < 2 || it.endsWith(" ") /* first part of a joined string */ }.toSet()
    }

    @Test
    fun everyDesktopLabelHasAChineseTranslation() {
        val missing = candidates().filter { desktopText(it, UiLanguage.SIMPLIFIED_CHINESE) == it }.sorted()
        assertTrue("Missing Chinese translations (${missing.size}):\n" + missing.joinToString("\n"), missing.isEmpty())
    }
}
