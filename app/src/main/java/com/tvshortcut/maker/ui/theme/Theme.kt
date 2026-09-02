package com.tvshortcut.maker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape

/** Rounded, Material-You style corners used across cards and dialogs. */
val TvShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = TvColors.Primary,
    onPrimary = TvColors.OnPrimary,
    primaryContainer = TvColors.PrimaryDim,
    onPrimaryContainer = TvColors.TextPrimary,
    secondary = TvColors.Secondary,
    onSecondary = TvColors.OnPrimary,
    background = TvColors.Background,
    onBackground = TvColors.TextPrimary,
    surface = TvColors.Surface,
    onSurface = TvColors.TextPrimary,
    surfaceVariant = TvColors.SurfaceElevated,
    onSurfaceVariant = TvColors.TextSecondary,
    border = TvColors.Border,
    error = TvColors.Danger
)

/**
 * Root theme. The app is dark-only by design: a light theme on a TV in a dark
 * room is uncomfortable, and every Android TV launcher is dark anyway.
 */
@Composable
fun TvShortcutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = TvTypography,
        shapes = TvShapes,
        content = content
    )
}
