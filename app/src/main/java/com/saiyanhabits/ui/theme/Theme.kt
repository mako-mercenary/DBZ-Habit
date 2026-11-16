package com.saiyanhabits.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.saiyanhabits.domain.Mentor

object SaiyanColors {
    val Orange = Color(0xFFFF8A1F)
    val OrangeDeep = Color(0xFFE85D04)
    val Blue = Color(0xFF2E9BFF)
    val BlueDeep = Color(0xFF123A6B)
    val Night = Color(0xFF0A0D16)
    val Panel = Color(0xFF131827)
    val PanelRaised = Color(0xFF1B2236)
    val Outline = Color(0xFF2B3552)
    val TextHigh = Color(0xFFF2F5FF)
    val TextDim = Color(0xFF9AA6C4)
    val Danger = Color(0xFFFF5A5F)
    val Bean = Color(0xFF7BD66B)
    val Gold = Color(0xFFFFD23F)
}

/** Aura colour of each form (0 = base form). */
fun formAura(form: Int): Color = when (form) {
    0 -> SaiyanColors.Blue
    1 -> SaiyanColors.Gold
    2 -> Color(0xFFFFE066)
    else -> Color(0xFFFFF1A8)
}

/** Second colour for sparks and lightning in each form. */
fun formSpark(form: Int): Color = when (form) {
    0 -> Color(0xFFBFE0FF)
    1 -> SaiyanColors.Orange
    2 -> Color(0xFF7FD4FF)
    else -> Color.White
}

/** Accent colour used wherever a mentor's habits show up. */
val Mentor.color: Color
    get() = when (this) {
        Mentor.ROSHI -> Color(0xFFFFC857)
        Mentor.GOKU -> Color(0xFFFF8A1F)
        Mentor.PICCOLO -> Color(0xFF5FD38D)
        Mentor.BULMA -> Color(0xFF4FD1E0)
        Mentor.VEGETA -> Color(0xFF6C8CFF)
    }

private val ColorScheme = darkColorScheme(
    primary = SaiyanColors.Orange,
    onPrimary = Color(0xFF1B0E00),
    primaryContainer = Color(0xFF4A2600),
    onPrimaryContainer = Color(0xFFFFDCC0),
    secondary = SaiyanColors.Blue,
    onSecondary = Color(0xFF001B33),
    secondaryContainer = SaiyanColors.BlueDeep,
    onSecondaryContainer = Color(0xFFD3E7FF),
    tertiary = Color(0xFFFFC857),
    onTertiary = Color(0xFF261A00),
    tertiaryContainer = SaiyanColors.BlueDeep,
    onTertiaryContainer = Color(0xFFD3E7FF),
    background = SaiyanColors.Night,
    onBackground = SaiyanColors.TextHigh,
    surface = SaiyanColors.Night,
    onSurface = SaiyanColors.TextHigh,
    surfaceVariant = SaiyanColors.PanelRaised,
    onSurfaceVariant = SaiyanColors.TextDim,
    surfaceContainerLowest = SaiyanColors.Night,
    surfaceContainerLow = SaiyanColors.Panel,
    surfaceContainer = SaiyanColors.Panel,
    surfaceContainerHigh = SaiyanColors.PanelRaised,
    surfaceContainerHighest = SaiyanColors.PanelRaised,
    outline = SaiyanColors.Outline,
    outlineVariant = SaiyanColors.Outline,
    error = SaiyanColors.Danger,
    onError = Color(0xFF2B0003),
)

private val Base = Typography()

/** Chunky type: the default family, pushed to heavy weights for anything that is a title or label. */
private val ChunkyTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Black),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Black),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Black),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Black),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Black),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Black),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
)

@Composable
fun SaiyanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = ChunkyTypography, content = content)
}
