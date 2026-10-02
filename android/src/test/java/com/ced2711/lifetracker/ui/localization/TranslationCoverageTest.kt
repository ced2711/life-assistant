package com.ced2711.lifetracker.ui.localization

import com.ced2711.lifetracker.domain.model.UiLanguage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every fixed English text the Android screens show must have a Simplified Chinese translation.
 * It scans the UI sources for localizedText("…") and translateUiText("…"), including every
 * literal on such a line (as in `localizedText(if (x) "A" else "B")`), so a new label without a
 * translation fails here instead of showing English in the Chinese UI.
 *
 * -Dtranslations.area=<part of a path> limits the check, for example to ui/ledger.
 */
class TranslationCoverageTest {
    private val area: String? = System.getProperty("translations.area")
    private val sources = File("src/main/java/com/ced2711/lifetracker").walkTopDown()
        .filter { it.extension == "kt" && !it.path.replace('\\', '/').contains("/ui/localization/") }
        .filter { area == null || it.path.replace('\\', '/').contains(area) }
        .toList()

    // Texts that are meant to look the same in both languages.
    private val sameInBothLanguages = setOf("GitHub", "Google Drive", "OK", "English", "简体中文", "Life Assistant", "USD")

    private fun candidates(): Map<String, String> {
        val shows = Regex("""localizedText\(|translateUiText\(""")
        val literal = Regex(""""((?:[^"\\$]|\\.)*[A-Za-z]{2}(?:[^"\\$]|\\.)*)"""")
        val found = LinkedHashMap<String, String>()
        sources.forEach { file ->
            file.readLines().filter { line -> shows.containsMatchIn(line) && !line.trimStart().startsWith("//") && !line.trimStart().startsWith("*") }.forEach { line ->
                literal.findAll(line).forEach { match ->
                    val text = match.groupValues[1].replace("\\\"", "\"").replace("\\n", "\n")
                    // Texts start with a capital; lower-case literals on such lines are keys, tags or formats.
                    if (text.first().isUpperCase() && text !in sameInBothLanguages && !text.endsWith(" ")) found.putIfAbsent(text, file.name)
                }
            }
        }
        return found
    }

    @Test
    fun everyShownTextHasAChineseTranslation() {
        // Still English when unchanged, or when a loose pattern only swapped a word inside the sentence.
        val english = Regex("""[A-Za-z]{2,} [A-Za-z]{2,} [A-Za-z]{2,}""")
        val missing = candidates().filter { (text, _) ->
            val translated = translateUiText(text, UiLanguage.SIMPLIFIED_CHINESE)
            translated == text || english.containsMatchIn(translated)
        }
        assertTrue(
            "Missing Chinese translations (${missing.size}):\n" + missing.entries.joinToString("\n") { "${it.value}: ${it.key}" },
            missing.isEmpty(),
        )
    }
}
