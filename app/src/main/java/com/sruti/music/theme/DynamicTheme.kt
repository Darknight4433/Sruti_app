package com.sruti.music.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class DynamicThemeColors(
    val primary: Color = NeonPurple,
    val secondary: Color = Color(0xFF4169F5),
    val accent: Color = Color(0xFF38BDF8),
    val darkBase: Color = Color(0xFF0A0715),
    val surface: Color = Color(0x18FFFFFF),
    val surfaceBorder: Color = Color(0x30FFFFFF),
    val glow: Color = NeonPurple.copy(alpha = 0.35f),
    val isDynamic: Boolean = true
)

val LocalDynamicTheme = compositionLocalOf { DynamicThemeColors() }

fun themePrimary(themeId: String): Color = when (themeId) {
    "cyber_cyan" -> Color(0xFF00E5FF)
    "aurora" -> Color(0xFFFF007F)
    "minimal_dark" -> Color(0xFFE2E8F0)
    else -> NeonPurple // "midnight_neon" default
}

fun themeSecondary(themeId: String): Color = when (themeId) {
    "cyber_cyan" -> Color(0xFF007799)
    "aurora" -> Color(0xFF8B008B)
    "minimal_dark" -> Color(0xFF64748B)
    else -> Color(0xFF4169F5)
}

fun themeAccent(themeId: String): Color = when (themeId) {
    "cyber_cyan" -> Color(0xFF38BDF8)
    "aurora" -> Color(0xFFE040FB)
    "minimal_dark" -> Color(0xFF94A3B8)
    else -> Color(0xFF38BDF8)
}
