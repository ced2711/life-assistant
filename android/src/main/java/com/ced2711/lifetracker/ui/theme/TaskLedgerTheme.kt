package com.ced2711.lifetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.ThemeMode

/*
 * Life Assistant's visual language, shared by the Android and desktop apps (docs/DESIGN.md).
 * Calm neutral surfaces and one accent the user picks; other colours only carry meaning (money
 * in and out, overdue, priority), never decoration.
 */

/** Colours with a meaning, beyond Material's roles. Read them through [LifeTheme.colors]. */
@Immutable
data class LifeColors(
    val income: Color,
    val expense: Color,
    /** Overdue dates and destructive actions. */
    val danger: Color,
    val warning: Color,
    val priorityUrgent: Color,
    val priorityHigh: Color,
    val priorityMedium: Color,
    val priorityLow: Color,
    /** Faint fills behind income, expense and danger text. */
    val incomeContainer: Color,
    val expenseContainer: Color,
    val dangerContainer: Color,
    /** The accent at low strength, for selected rows and today's cell. */
    val accentSoft: Color,
    /** Hairlines between rows. */
    val divider: Color,
    val dark: Boolean,
)

val LocalLifeColors = staticCompositionLocalOf { lifeColors(dark = true, accent = accentOf(AccentColor.TEAL, dark = true)) }

object LifeTheme {
    val colors: LifeColors
        @Composable get() = LocalLifeColors.current
}

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
    val dark = isTaskLedgerDarkTheme(themeMode)
    val accent = accentOf(accentColor, dark)
    CompositionLocalProvider(LocalLifeColors provides lifeColors(dark, accent)) {
        MaterialTheme(
            colorScheme = lifeColorScheme(accent, dark),
            typography = LifeTypography,
            shapes = LifeShapes,
            content = content,
        )
    }
}

/** The accent in both modes: strong enough for text on the background, calm enough for fills. */
internal fun accentOf(accentColor: AccentColor, dark: Boolean): Color = when (accentColor) {
    AccentColor.TEAL -> if (dark) Color(0xFF3FD2BD) else Color(0xFF0B8577)
    AccentColor.BLUE -> if (dark) Color(0xFF7EA8FF) else Color(0xFF2E64D2)
    AccentColor.VIOLET -> if (dark) Color(0xFFB7A2FF) else Color(0xFF6B4FD3)
    AccentColor.ROSE -> if (dark) Color(0xFFFF8DB0) else Color(0xFFC93D6A)
    AccentColor.ORANGE -> if (dark) Color(0xFFFFAE5E) else Color(0xFFC2620A)
    AccentColor.GREEN -> if (dark) Color(0xFF6CD68B) else Color(0xFF1D8545)
}

private object Neutral {
    // Dark: near-black with a hint of blue-grey; light: paper white with a hint of warmth.
    val darkBackground = Color(0xFF0F1012)
    val darkLowest = Color(0xFF0B0C0E)
    val darkLow = Color(0xFF141518)
    val darkContainer = Color(0xFF191A1D)
    val darkHigh = Color(0xFF202125)
    val darkHighest = Color(0xFF292A2F)
    val darkText = Color(0xFFEDEEF0)
    val darkTextSecondary = Color(0xFF9DA0A6)
    val darkOutline = Color(0xFF3B3D42)
    val darkOutlineVariant = Color(0xFF26282C)

    val lightBackground = Color(0xFFF6F6F4)
    val lightLowest = Color(0xFFFFFFFF)
    val lightLow = Color(0xFFFBFBFA)
    val lightContainer = Color(0xFFF0F0EE)
    val lightHigh = Color(0xFFE9E9E6)
    val lightHighest = Color(0xFFE1E1DE)
    val lightText = Color(0xFF17181A)
    val lightTextSecondary = Color(0xFF62656B)
    val lightOutline = Color(0xFFC7C8CB)
    val lightOutlineVariant = Color(0xFFE2E2DF)
}

private fun lifeColorScheme(accent: Color, dark: Boolean): ColorScheme {
    val onAccent = if (dark) Color(0xFF0A1513) else Color.White
    return if (dark) {
        val container = lerp(Neutral.darkContainer, accent, 0.22f)
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = container,
            onPrimaryContainer = lerp(accent, Color.White, 0.55f),
            inversePrimary = lerp(accent, Color.Black, 0.35f),
            secondary = lerp(accent, Neutral.darkText, 0.45f),
            onSecondary = onAccent,
            secondaryContainer = lerp(Neutral.darkHigh, accent, 0.16f),
            onSecondaryContainer = lerp(accent, Color.White, 0.6f),
            tertiary = lerp(accent, Color.White, 0.2f),
            onTertiary = onAccent,
            tertiaryContainer = lerp(Neutral.darkHigh, accent, 0.24f),
            onTertiaryContainer = lerp(accent, Color.White, 0.6f),
            background = Neutral.darkBackground,
            onBackground = Neutral.darkText,
            surface = Neutral.darkBackground,
            onSurface = Neutral.darkText,
            surfaceVariant = Neutral.darkHigh,
            onSurfaceVariant = Neutral.darkTextSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = Neutral.darkText,
            inverseOnSurface = Neutral.darkBackground,
            error = Color(0xFFFF7B72),
            onError = Color(0xFF2A0705),
            errorContainer = Color(0xFF4A1C19),
            onErrorContainer = Color(0xFFFFD9D5),
            outline = Neutral.darkOutline,
            outlineVariant = Neutral.darkOutlineVariant,
            scrim = Color.Black,
            surfaceBright = Color(0xFF36383E),
            surfaceDim = Neutral.darkLowest,
            surfaceContainerLowest = Neutral.darkLowest,
            surfaceContainerLow = Neutral.darkLow,
            surfaceContainer = Neutral.darkContainer,
            surfaceContainerHigh = Neutral.darkHigh,
            surfaceContainerHighest = Neutral.darkHighest,
        )
    } else {
        val container = lerp(Neutral.lightLowest, accent, 0.13f)
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = container,
            onPrimaryContainer = lerp(accent, Color.Black, 0.45f),
            inversePrimary = lerp(accent, Color.White, 0.4f),
            secondary = lerp(accent, Neutral.lightText, 0.4f),
            onSecondary = onAccent,
            secondaryContainer = lerp(Neutral.lightContainer, accent, 0.12f),
            onSecondaryContainer = lerp(accent, Color.Black, 0.5f),
            tertiary = lerp(accent, Color.Black, 0.15f),
            onTertiary = onAccent,
            tertiaryContainer = lerp(Neutral.lightLowest, accent, 0.2f),
            onTertiaryContainer = lerp(accent, Color.Black, 0.5f),
            background = Neutral.lightBackground,
            onBackground = Neutral.lightText,
            surface = Neutral.lightBackground,
            onSurface = Neutral.lightText,
            surfaceVariant = Neutral.lightHigh,
            onSurfaceVariant = Neutral.lightTextSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFF232427),
            inverseOnSurface = Color(0xFFF2F2F0),
            error = Color(0xFFC83A32),
            onError = Color.White,
            errorContainer = Color(0xFFFBE3E0),
            onErrorContainer = Color(0xFF5A100B),
            outline = Neutral.lightOutline,
            outlineVariant = Neutral.lightOutlineVariant,
            scrim = Color.Black,
            surfaceBright = Neutral.lightLowest,
            surfaceDim = Neutral.lightHighest,
            surfaceContainerLowest = Neutral.lightLowest,
            surfaceContainerLow = Neutral.lightLow,
            surfaceContainer = Neutral.lightContainer,
            surfaceContainerHigh = Neutral.lightHigh,
            surfaceContainerHighest = Neutral.lightHighest,
        )
    }
}

internal fun lifeColors(dark: Boolean, accent: Color): LifeColors {
    val background = if (dark) Neutral.darkBackground else Neutral.lightLowest
    val income = if (dark) Color(0xFF5DD28C) else Color(0xFF1C8549)
    val expense = if (dark) Color(0xFFFF7B72) else Color(0xFFCC4038)
    val warning = if (dark) Color(0xFFFFB45C) else Color(0xFFB8650A)
    return LifeColors(
        income = income,
        expense = expense,
        danger = expense,
        warning = warning,
        priorityUrgent = expense,
        priorityHigh = warning,
        priorityMedium = if (dark) Color(0xFF7EA8FF) else Color(0xFF2E64D2),
        priorityLow = if (dark) Color(0xFF8B8E94) else Color(0xFF8A8D93),
        incomeContainer = lerp(background, income, if (dark) 0.16f else 0.1f),
        expenseContainer = lerp(background, expense, if (dark) 0.16f else 0.1f),
        dangerContainer = lerp(background, expense, if (dark) 0.16f else 0.1f),
        accentSoft = lerp(if (dark) Neutral.darkContainer else Neutral.lightLowest, accent, if (dark) 0.2f else 0.12f),
        divider = if (dark) Color(0xFF222428) else Color(0xFFEAEAE7),
        dark = dark,
    )
}

private val Tight = (-0.02).em

/** A short, clear scale: big page titles, few sizes in between, numbers that line up. */
val LifeTypography = Typography(
    displayLarge = TextStyle(fontSize = 52.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold, letterSpacing = Tight),
    displayMedium = TextStyle(fontSize = 42.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = Tight),
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = Tight),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = Tight),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, letterSpacing = Tight),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = Tight),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.02.em),
)

val LifeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
