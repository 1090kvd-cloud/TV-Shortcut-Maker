package com.tvshortcut.maker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvshortcut.maker.ui.theme.TvColors

/**
 * Pill-shaped filter chip used in the top bar.
 *
 * Three visual states are distinguished, which matters a lot on TV:
 *  - **selected**  → filled with the accent colour;
 *  - **focused**   → bright outline + slight scale up (where the D-Pad is);
 *  - **idle**      → muted surface.
 */
@Composable
fun TvFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsStateCompat()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(150),
        label = "chipScale"
    )
    val shape = RoundedCornerShape(percent = 50)

    Row(
        modifier = modifier
            .scale(scale)
            .height(44.dp)
            .clip(shape)
            .background(
                when {
                    selected -> TvColors.Primary
                    focused -> TvColors.SurfaceFocused
                    else -> TvColors.Surface
                }
            )
            .border(
                BorderStroke(
                    width = if (focused) 2.dp else 1.dp,
                    color = when {
                        focused -> TvColors.BorderFocused
                        selected -> Color.Transparent
                        else -> TvColors.Border
                    }
                ),
                shape
            )
            .tvClickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) TvColors.OnPrimary else TvColors.TextPrimary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TvColors.OnPrimary.copy(alpha = 0.7f) else TvColors.TextTertiary
        )
    }
}

/**
 * Full-width action button used in the detail panel.
 *
 * @param destructive paints the button in the danger colour (uninstall).
 */
@Composable
fun TvActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsStateCompat()
    val shape = RoundedCornerShape(14.dp)
    val accent = if (destructive) TvColors.Danger else TvColors.Primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> TvColors.Surface.copy(alpha = 0.5f)
                    focused -> accent
                    else -> TvColors.SurfaceElevated
                }
            )
            .border(
                BorderStroke(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) TvColors.BorderFocused else TvColors.Border
                ),
                shape
            )
            .tvClickable(
                interactionSource = interactionSource,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                !enabled -> TvColors.TextTertiary
                focused -> TvColors.OnPrimary
                else -> accent
            },
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> TvColors.TextTertiary
                focused -> TvColors.OnPrimary
                else -> TvColors.TextPrimary
            }
        )
    }
}

/** Compact toggle used to pick the banner style in the detail panel. */
@Composable
fun TvSegmentedOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsStateCompat()
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(
                when {
                    selected -> TvColors.PrimaryDim
                    focused -> TvColors.SurfaceFocused
                    else -> TvColors.Surface
                }
            )
            .border(
                BorderStroke(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) TvColors.BorderFocused else TvColors.Border
                ),
                shape
            )
            .tvClickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected || focused) TvColors.TextPrimary else TvColors.TextSecondary
        )
    }
}
