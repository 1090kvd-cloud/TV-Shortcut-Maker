package com.tvshortcut.maker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvshortcut.maker.ui.theme.TvColors
import com.tvshortcut.maker.viewmodel.UiMessage

/** Pulsing placeholder shown while the package list is being scanned. */
@Composable
fun LoadingState(text: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")
    val pulse by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(pulse)
                    .clip(RoundedCornerShape(20.dp))
                    .background(TvColors.Primary.copy(alpha = 0.35f))
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = TvColors.TextSecondary,
                modifier = Modifier.alpha(pulse)
            )
        }
    }
}

/** Shown when a filter matches nothing. */
@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = TvColors.TextTertiary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TvColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Bottom snack bar for one-shot feedback.
 *
 * It is deliberately non-focusable: stealing focus from the grid would break
 * the user's position in the list, which is the number-one TV UX complaint.
 */
@Composable
fun MessageBar(
    message: UiMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-hide after a few seconds, mirroring the platform toast timing.
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(4_000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (message?.isError == true) TvColors.Danger.copy(alpha = 0.18f) else TvColors.SurfaceElevated)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (message?.isError == true) Icons.Default.Error else Icons.Default.Info,
                contentDescription = null,
                tint = if (message?.isError == true) TvColors.Danger else TvColors.Success,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message?.text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.TextPrimary
            )
        }
    }
}
