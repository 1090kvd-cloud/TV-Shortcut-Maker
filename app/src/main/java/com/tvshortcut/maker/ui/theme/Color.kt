package com.tvshortcut.maker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette of the app. Deep blue/violet accents on a near-black background —
 * the combination recommended for OLED TV panels (true blacks, low eye strain
 * in a dark living room).
 */
object TvColors {
    val Background = Color(0xFF0B0E14)
    val Surface = Color(0xFF141922)
    val SurfaceElevated = Color(0xFF1C2230)
    val SurfaceFocused = Color(0xFF262E40)

    val Primary = Color(0xFF7C6BFF)        // violet accent
    val PrimaryDim = Color(0xFF4B3FB0)
    val Secondary = Color(0xFF3D8BFF)      // deep blue accent
    val OnPrimary = Color(0xFF0B0714)

    val TextPrimary = Color(0xFFECEEF5)
    val TextSecondary = Color(0xFF9AA3B8)
    val TextTertiary = Color(0xFF636C82)

    val Border = Color(0xFF2A3242)
    val BorderFocused = Color(0xFFB9AEFF)
    val Danger = Color(0xFFFF6B6B)
    val Success = Color(0xFF4ADE80)

    val Scrim = Color(0xE60A0C12)
}
