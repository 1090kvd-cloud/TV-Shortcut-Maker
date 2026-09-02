package com.tvshortcut.maker.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.compositeOver
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvshortcut.maker.data.model.AppInfo
import com.tvshortcut.maker.ui.theme.TvColors

/**
 * A single tile in the app grid.
 *
 * Focus feedback follows the Android TV guidelines:
 *  - the card scales up (1.0 → 1.08) with a short ease-out animation;
 *  - an accent-coloured outline is drawn around it;
 *  - elevation increases, producing a subtle drop shadow ("parallax" depth);
 *  - the background picks up a tint derived from the icon itself.
 *
 * The whole card is a single focusable node, which is what makes D-Pad
 * navigation predictable: one press = one tile.
 */
@Composable
fun AppCard(
    app: AppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = interactionSource.collectIsFocusedAsStateCompat()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "cardScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 18.dp else 0.dp,
        animationSpec = tween(180),
        label = "cardElevation"
    )

    val accent = Color(app.accentColor)
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 16:9 keeps every tile aligned with the TV banner ratio.
                .aspectRatio(16f / 9f)
                .shadow(elevation, shape, ambientColor = accent, spotColor = accent)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = if (isFocused) {
                            listOf(
                                accent.copy(alpha = 0.35f).compositeOver(TvColors.SurfaceElevated),
                                TvColors.SurfaceElevated
                            )
                        } else {
                            listOf(TvColors.Surface, TvColors.Surface)
                        }
                    )
                )
                .border(
                    BorderStroke(
                        width = if (isFocused) 3.dp else 1.dp,
                        color = if (isFocused) TvColors.BorderFocused else TvColors.Border
                    ),
                    shape
                )
                .tvClickable(
                    interactionSource = interactionSource,
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(app, Modifier.size(72.dp))

            // Badges: "already on TV" and "favourite".
            if (app.hasLeanbackEntry) {
                Badge(
                    icon = Icons.Default.Tv,
                    tint = TvColors.Secondary,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                )
            }
            if (app.isFavorite) {
                Badge(
                    icon = Icons.Default.Star,
                    tint = TvColors.Primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) TvColors.TextPrimary else TvColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
    }
}

/** Draws the icon extracted from the APK, or a coloured placeholder. */
@Composable
fun AppIcon(app: AppInfo, modifier: Modifier = Modifier) {
    val bitmap = app.icon
    if (bitmap != null) {
        // `remember` avoids re-wrapping the same bitmap on every recomposition.
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        androidx.compose.foundation.Image(
            bitmap = image,
            contentDescription = app.label,
            contentScale = ContentScale.Fit,
            modifier = modifier.clip(RoundedCornerShape(16.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(app.accentColor).copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = TvColors.TextPrimary
            )
        }
    }
}

/** Small circular badge overlaid on a card corner. */
@Composable
private fun Badge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TvColors.Background.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
    }
}
