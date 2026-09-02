package com.tvshortcut.maker.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvshortcut.maker.R
import com.tvshortcut.maker.data.QrCodeFactory
import com.tvshortcut.maker.ui.components.TvActionButton
import com.tvshortcut.maker.ui.theme.TvColors

/**
 * Centred dialog offering the donation link as a QR code.
 *
 * A QR code rather than a link, because nobody types a URL with a TV remote —
 * the viewer points a phone at the screen instead. The code is generated
 * locally, so the panel works with no network access and phones home to
 * nothing.
 */
@Composable
fun DonatePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = visible, onBack = onDismiss)

    val url = stringResource(R.string.donate_url)
    val compact = LocalConfiguration.current.screenWidthDp < 720
    val qrSize = if (compact) 180.dp else 260.dp

    // Rendering happens off the composition; produceState keeps it out of the
    // frame that opens the dialog.
    val qr by produceState<Bitmap?>(initialValue = null, url) {
        value = QrCodeFactory.render(url, sizePx = 640)
    }

    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) runCatching { closeFocus.requestFocus() }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvColors.Scrim),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(initialScale = 0.92f) + fadeIn(),
                exit = scaleOut(targetScale = 0.92f) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(TvColors.Surface)
                        .padding(horizontal = 32.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.donate_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TvColors.TextPrimary
                    )

                    Spacer(Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .size(qrSize)
                            .clip(RoundedCornerShape(16.dp))
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        qr?.let { bitmap ->
                            Image(
                                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                                contentDescription = stringResource(R.string.donate_title),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.donate_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(6.dp))

                    // The plain URL, for anyone who would rather read it.
                    Text(
                        text = url,
                        style = MaterialTheme.typography.labelMedium,
                        color = TvColors.TextTertiary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.donate_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvColors.TextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(22.dp))

                    TvActionButton(
                        label = stringResource(R.string.action_close),
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(closeFocus)
                    )
                }
            }
        }
    }
}
