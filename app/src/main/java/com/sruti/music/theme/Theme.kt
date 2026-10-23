package com.sruti.music.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = PureBlack,
    secondary = Color(0xFFA1A1AA),
    onSecondary = PureBlack,
    background = PureBlack,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = Color(0x18FFFFFF),
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
