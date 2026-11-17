package com.saiyanhabits.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.saiyanhabits.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.saiyanhabits.domain.Boss
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
    val TextDim = Color(0xFFB4C1E4)
    val Danger = Color(0xFFFF5A5F)
    val Bean = Color(0xFF7BD66B)
    val Gold = Color(0xFFFFD23F)

    // The gold-framed panels and banners.
    val FrameDark = Color(0xFF3A2200)
    val FrameGoldLight = Color(0xFFFFF0A0)
    val FrameGold = Color(0xFFF5B800)
    val FrameGoldDark = Color(0xFFB06A00)
    val PanelBlueTop = Color(0xFF1D4BA6)
    val PanelBlueBottom = Color(0xFF0B1F5C)
    val BannerOrangeTop = Color(0xFFFF9D1E)
    val BannerOrangeBottom = Color(0xFFE0500A)
    val ScouterGreenTop = Color(0xFF1C9A2E)
    val ScouterGreenBottom = Color(0xFF07501A)
    val ScouterLine = Color(0xFF8CFF6A)
    val TabGoldTop = Color(0xFFFFE54D)
    val TabGoldBottom = Color(0xFFF2B000)
    val TabInk = Color(0xFF1C1200)
    val RibbonRedTop = Color(0xFFFF4A3A)
    val RibbonRedBottom = Color(0xFFB8120E)
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

/** Colour of each boss's placeholder figure, aura and HP bar. */
val Boss.color: Color
    get() = when (this) {
        Boss.RADITZ -> Color(0xFFD9694A)
        Boss.NAPPA -> Color(0xFFD9B13B)
        Boss.FRIEZA -> Color(0xFFB678F0)
        Boss.CELL -> Color(0xFF74D05F)
        Boss.BUU -> Color(0xFFFF82B8)
        Boss.BROLY -> Color(0xFF3FE0A8)
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

/** The display face: Bangers, a chunky comic-style italic (SIL Open Font License; see CREDITS.md). */
val DisplayFont = FontFamily(Font(R.font.bangers))

/** A hard drop shadow under display text, for the punchy look. */
private val Punch = Shadow(color = Color(0xCC000000), offset = Offset(0f, 3f), blurRadius = 0f)

private fun display(base: TextStyle, size: Int, spacing: Float = 0.6f) = base.copy(
    fontFamily = DisplayFont,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = (size * 1.15f).sp,
    letterSpacing = spacing.sp,
    shadow = Punch,
)

/**
 * Titles, headings and labels in the display face; body text stays in the
 * phone's own font so longer lines read easily.
 */
private val ChunkyTypography = Typography(
    displayLarge = display(Base.displayLarge, 60),
    displayMedium = display(Base.displayMedium, 48),
    displaySmall = display(Base.displaySmall, 38),
    headlineLarge = display(Base.headlineLarge, 34),
    headlineMedium = display(Base.headlineMedium, 30),
    headlineSmall = display(Base.headlineSmall, 26),
    titleLarge = display(Base.titleLarge, 24),
    titleMedium = display(Base.titleMedium, 20),
    titleSmall = display(Base.titleSmall, 17),
    labelLarge = display(Base.labelLarge, 17, 1f),
    labelMedium = display(Base.labelMedium, 15, 1.2f),
    labelSmall = display(Base.labelSmall, 13, 1.2f),
    bodyLarge = Base.bodyLarge.copy(fontWeight = FontWeight.Medium),
)

@Composable
fun SaiyanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = ChunkyTypography, content = content)
}
