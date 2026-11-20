package com.cdlstudy.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF1C5D99)
private val Amber = Color(0xFFB4690E)

private val Light = lightColorScheme(primary = Blue, secondary = Amber)
private val Dark = darkColorScheme(primary = Color(0xFF9CC7F0), secondary = Color(0xFFF0B966))

@Composable
fun CdlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
