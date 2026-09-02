package com.ced2711.lifetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.ThemeMode

private val TaskLedgerDarkColors = darkColorScheme(
    primary = Color(0xFF63DCCB),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF84F8E7),
    secondary = Color(0xFFB0CCC7),
    onSecondary = Color(0xFF1B3531),
    secondaryContainer = Color(0xFF324B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFADCAE6),
    onTertiary = Color(0xFF153349),
    background = Color(0xFF0D1514),
    onBackground = Color(0xFFDCE5E2),
    surface = Color(0xFF0D1514),
    onSurface = Color(0xFFDCE5E2),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val TaskLedgerLightColors = lightColorScheme(
    primary = Color(0xFF006B60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF7AF8E4),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF06201C),
    tertiary = Color(0xFF456179),
    onTertiary = Color.White,
    background = Color(0xFFF5FBF8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FBF8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBEC9C5),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val TaskLedgerTypography = Typography()

/**
 * Shared TaskLedger theme. The app defaults to its dark teal palette while still allowing
 * callers to explicitly follow the system or force the light palette.
 */
@Composable
internal fun isTaskLedgerDarkTheme(themeMode: ThemeMode): Boolean =
    when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

@Composable
fun TaskLedgerTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: AccentColor = AccentColor.TEAL,
    content: @Composable () -> Unit,
) {
    val useDarkColors = isTaskLedgerDarkTheme(themeMode)

    MaterialTheme(
        colorScheme = taskLedgerColorScheme(accentColor, useDarkColors),
        typography = TaskLedgerTypography,
        content = content,
    )
}

private fun taskLedgerColorScheme(accentColor: AccentColor, dark: Boolean) =
    (if (dark) TaskLedgerDarkColors else TaskLedgerLightColors).let { base ->
        when (accentColor) {
            AccentColor.TEAL -> base
            AccentColor.BLUE -> if (dark) {
                base.copy(
                    primary = Color(0xFFAAC7FF),
                    onPrimary = Color(0xFF002F65),
                    primaryContainer = Color(0xFF17477C),
                    onPrimaryContainer = Color(0xFFD6E3FF),
                )
            } else {
                base.copy(
                    primary = Color(0xFF315F93),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFD6E3FF),
                    onPrimaryContainer = Color(0xFF001B3D),
                )
            }
            AccentColor.VIOLET -> if (dark) {
                base.copy(
                    primary = Color(0xFFD0BCFF),
                    onPrimary = Color(0xFF381E72),
                    primaryContainer = Color(0xFF4F378B),
                    onPrimaryContainer = Color(0xFFEADDFF),
                )
            } else {
                base.copy(
                    primary = Color(0xFF6750A4),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFEADDFF),
                    onPrimaryContainer = Color(0xFF21005D),
                )
            }
            AccentColor.ROSE -> if (dark) {
                base.copy(
                    primary = Color(0xFFFFB1C8),
                    onPrimary = Color(0xFF5E1131),
                    primaryContainer = Color(0xFF7A2948),
                    onPrimaryContainer = Color(0xFFFFD9E3),
                )
            } else {
                base.copy(
                    primary = Color(0xFF984061),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFFFD9E3),
                    onPrimaryContainer = Color(0xFF3E001D),
                )
            }
            AccentColor.ORANGE -> if (dark) {
                base.copy(
                    primary = Color(0xFFFFB86C),
                    onPrimary = Color(0xFF4A2800),
                    primaryContainer = Color(0xFF663B00),
                    onPrimaryContainer = Color(0xFFFFDCB5),
                )
            } else {
                base.copy(
                    primary = Color(0xFF8B5000),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFFFDCB5),
                    onPrimaryContainer = Color(0xFF2C1600),
                )
            }
            AccentColor.GREEN -> if (dark) {
                base.copy(
                    primary = Color(0xFF75DC8B),
                    onPrimary = Color(0xFF003916),
                    primaryContainer = Color(0xFF005225),
                    onPrimaryContainer = Color(0xFF91F9A5),
                )
            } else {
                base.copy(
                    primary = Color(0xFF176D35),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFA8F5B7),
                    onPrimaryContainer = Color(0xFF002109),
                )
            }
        }
    }
