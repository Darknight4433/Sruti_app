package com.sruti.music.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class DynamicThemeColors(
    val primary: Color = Color.White,
    val secondary: Color = Color(0xFF71717A),
    val accent: Color = Color(0xFFE2E8F0),
    val darkBase: Color = PureBlack,
    val surface: Color = Color(0x18FFFFFF),
    val surfaceBorder: Color = Color(0x30FFFFFF),
    val glow: Color = Color.White.copy(alpha = 0.25f),
    val isDynamic: Boolean = false
)

val LocalDynamicTheme = compositionLocalOf { DynamicThemeColors() }

fun themePrimary(themeId: String): Color = when (themeId) {
    "pure_black", "minimal_dark" -> Color.White
    "cyber_cyan" -> Color(0xFF00E5FF)
    "electric_blue" -> Color(0xFF38BDF8)
    "emerald_mint" -> Color(0xFF10B981)
    "midnight_neon" -> Color(0xFFA855F7)
    "aurora" -> Color(0xFFFF2A85)
    else -> Color.White // Default pure black
}

fun themeSecondary(themeId: String): Color = when (themeId) {
    "pure_black", "minimal_dark" -> Color(0xFF71717A)
    "cyber_cyan" -> Color(0xFF007799)
    "electric_blue" -> Color(0xFF1D4ED8)
    "emerald_mint" -> Color(0xFF047857)
    "midnight_neon" -> Color(0xFF4169F5)
    "aurora" -> Color(0xFF8B008B)
    else -> Color(0xFF27272A)
}

fun themeAccent(themeId: String): Color = when (themeId) {
    "pure_black", "minimal_dark" -> Color(0xFFE2E8F0)
    "cyber_cyan" -> Color(0xFF38BDF8)
    "electric_blue" -> Color(0xFF60A5FA)
    "emerald_mint" -> Color(0xFF34D399)
    "midnight_neon" -> Color(0xFF38BDF8)
    "aurora" -> Color(0xFFE040FB)
    else -> Color(0xFFA1A1AA)
}

fun themeDarkBase(themeId: String): Color = when (themeId) {
    "pure_black" -> PureBlack
    "cyber_cyan" -> Color(0xFF08121C)
    "midnight_neon" -> Color(0xFF07040D)
    "aurora" -> Color(0xFF0A0214)
    "minimal_dark" -> Color(0xFF07070A)
    else -> PureBlack
}
