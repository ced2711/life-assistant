package com.ced2711.lifetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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

private data class AccentRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

private fun taskLedgerColorScheme(accentColor: AccentColor, dark: Boolean) =
    (if (dark) TaskLedgerDarkColors else TaskLedgerLightColors).let { base ->
        val accent = when (accentColor) {
            AccentColor.TEAL -> if (dark) {
                AccentRoles(Color(0xFF63DCCB), Color(0xFF003731), Color(0xFF005048), Color(0xFF84F8E7))
            } else {
                AccentRoles(Color(0xFF006B60), Color.White, Color(0xFF7AF8E4), Color(0xFF00201C))
            }
            AccentColor.BLUE -> if (dark) {
                AccentRoles(Color(0xFFAAC7FF), Color(0xFF002F65), Color(0xFF17477C), Color(0xFFD6E3FF))
            } else {
                AccentRoles(Color(0xFF315F93), Color.White, Color(0xFFD6E3FF), Color(0xFF001B3D))
            }
            AccentColor.VIOLET -> if (dark) {
                AccentRoles(Color(0xFFD0BCFF), Color(0xFF381E72), Color(0xFF4F378B), Color(0xFFEADDFF))
            } else {
                AccentRoles(Color(0xFF6750A4), Color.White, Color(0xFFEADDFF), Color(0xFF21005D))
            }
            AccentColor.ROSE -> if (dark) {
                AccentRoles(Color(0xFFFFB1C8), Color(0xFF5E1131), Color(0xFF7A2948), Color(0xFFFFD9E3))
            } else {
                AccentRoles(Color(0xFF984061), Color.White, Color(0xFFFFD9E3), Color(0xFF3E001D))
            }
            AccentColor.ORANGE -> if (dark) {
                AccentRoles(Color(0xFFFFB86C), Color(0xFF4A2800), Color(0xFF663B00), Color(0xFFFFDCB5))
            } else {
                AccentRoles(Color(0xFF8B5000), Color.White, Color(0xFFFFDCB5), Color(0xFF2C1600))
            }
            AccentColor.GREEN -> if (dark) {
                AccentRoles(Color(0xFF75DC8B), Color(0xFF003916), Color(0xFF005225), Color(0xFF91F9A5))
            } else {
                AccentRoles(Color(0xFF176D35), Color.White, Color(0xFFA8F5B7), Color(0xFF002109))
            }
        }

        val neutralSurface = if (dark) Color(0xFF121313) else Color(0xFFFAFAFA)
        val neutralVariant = if (dark) Color(0xFF343636) else Color(0xFFE5E7E7)
        val secondary = lerp(
            accent.primary,
            if (dark) Color(0xFFD7DADA) else Color(0xFF414343),
            0.34f,
        )
        val tintedVariant = lerp(neutralVariant, accent.primary, if (dark) 0.12f else 0.08f)
        base.copy(
            primary = accent.primary,
            onPrimary = accent.onPrimary,
            primaryContainer = accent.primaryContainer,
            onPrimaryContainer = accent.onPrimaryContainer,
            inversePrimary = accent.primaryContainer,
            secondary = secondary,
            onSecondary = accent.onPrimary,
            secondaryContainer = lerp(tintedVariant, accent.primaryContainer, 0.42f),
            onSecondaryContainer = accent.onPrimaryContainer,
            tertiary = lerp(accent.primary, if (dark) Color.White else Color.Black, 0.16f),
            onTertiary = accent.onPrimary,
            tertiaryContainer = lerp(tintedVariant, accent.primaryContainer, 0.62f),
            onTertiaryContainer = accent.onPrimaryContainer,
            background = neutralSurface,
            onBackground = if (dark) Color(0xFFE4E7E6) else Color(0xFF1A1C1C),
            surface = neutralSurface,
            onSurface = if (dark) Color(0xFFE4E7E6) else Color(0xFF1A1C1C),
            surfaceVariant = tintedVariant,
            onSurfaceVariant = if (dark) Color(0xFFC5C9C8) else Color(0xFF444847),
            outlineVariant = lerp(tintedVariant, accent.primary, 0.16f),
            surfaceTint = accent.primary,
            surfaceContainerLowest = if (dark) Color(0xFF0C0D0D) else Color.White,
            surfaceContainerLow = lerp(neutralSurface, accent.primary, 0.025f),
            surfaceContainer = lerp(neutralSurface, accent.primary, 0.045f),
            surfaceContainerHigh = lerp(neutralSurface, accent.primary, 0.07f),
            surfaceContainerHighest = lerp(neutralSurface, accent.primary, 0.10f),
        )
    }
