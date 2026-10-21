package com.sruti.music.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color.White,
    secondary = NeonPurple,
    onSecondary = Color.White,
    background = Midnight,
    onBackground = Color.White,
    surface = DeepViolet,
    onSurface = Color.White,
    surfaceVariant = Color(0x25FFFFFF),
    onSurfaceVariant = GlassWhite,
    outline = GlassBorderColor
)

@Composable
fun SrutiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
