package com.cdlstudy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Dark, with gold for anything that asks you to act.
 *
 * The app is one dark theme, whatever the system is set to. Gold is the only
 * accent: a gold thing is a thing to press or a number to remember. Everything
 * else is grey, so the gold means something.
 */

val Gold = Color(0xFFE3B341)
val GoldDim = Color(0xFF8A6B24)
val GoldInk = Color(0xFF17120A)
private val Bg = Color(0xFF0E1013)
private val SurfaceColor = Color(0xFF171A1F)
private val Raised = Color(0xFF1F242B)
private val Line = Color(0xFF2A3039)
private val Ink = Color(0xFFECEEF1)
private val Muted = Color(0xFF99A1AC)

private val Scheme = darkColorScheme(
    primary = Gold,
    onPrimary = GoldInk,
    primaryContainer = Color(0xFF3A2D0E),
    onPrimaryContainer = Gold,
    secondary = GoldDim,
    onSecondary = Ink,
    secondaryContainer = Raised,
    onSecondaryContainer = Ink,
    background = Bg,
    onBackground = Ink,
    surface = SurfaceColor,
    onSurface = Ink,
    surfaceVariant = Raised,
    onSurfaceVariant = Muted,
    surfaceContainer = SurfaceColor,
    surfaceContainerHigh = Raised,
    outline = Line,
    outlineVariant = Line,
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF4A2326),
    onErrorContainer = Color(0xFFFFCDD2),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
    )
}

/** Small, wide-spaced, gold: the line that says where you are. */
val OverlineStyle = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = 1.8.sp,
    color = Gold,
)

@Composable
fun CdlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
