package com.tvshortcut.maker.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvshortcut.maker.R
import com.tvshortcut.maker.data.model.AppInfo
import com.tvshortcut.maker.data.model.BannerStyle
import com.tvshortcut.maker.ui.components.TvActionButton
import com.tvshortcut.maker.ui.components.TvSegmentedOption
import com.tvshortcut.maker.ui.theme.TvColors

/**
 * Slide-in panel with everything you can do to one app.
 *
 * Design decision: a side panel instead of a full screen keeps the grid visible
 * behind a scrim, so the user never loses the sense of where they were — a
 * pattern borrowed from the Google TV launcher itself.
 */
@Composable
fun AppDetailPanel(
    app: AppInfo?,
    bannerStyle: BannerStyle,
    @Suppress("UNUSED_PARAMETER") pinningSupported: Boolean,
    onBannerStyleChange: (BannerStyle) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onCreateShortcut: (AppInfo) -> Unit,
    onToggleFavorite: (AppInfo) -> Unit,
    onOpenSettings: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    renderBanner: suspend (AppInfo, BannerStyle) -> Bitmap,
    modifier: Modifier = Modifier
) {
    // The remote's BACK button closes the panel before leaving the app.
    BackHandler(enabled = app != null, onBack = onDismiss)

    AnimatedVisibility(
        visible = app != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Scrim dims the grid behind the panel.
                .background(TvColors.Scrim)
        ) {
            AnimatedVisibility(
                visible = app != null,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                if (app != null) {
                    PanelContent(
                        app = app,
                        bannerStyle = bannerStyle,
                        onBannerStyleChange = onBannerStyleChange,
                        onLaunch = onLaunch,
                        onCreateShortcut = onCreateShortcut,
                        onToggleFavorite = onToggleFavorite,
                        onOpenSettings = onOpenSettings,
                        onUninstall = onUninstall,
                        renderBanner = renderBanner
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelContent(
    app: AppInfo,
    bannerStyle: BannerStyle,
    onBannerStyleChange: (BannerStyle) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onCreateShortcut: (AppInfo) -> Unit,
    onToggleFavorite: (AppInfo) -> Unit,
    onOpenSettings: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    renderBanner: suspend (AppInfo, BannerStyle) -> Bitmap
) {
    val firstAction = remember { FocusRequester() }

    // Focus the primary action as soon as the panel appears, otherwise the
    // D-Pad would still be driving the grid underneath.
    LaunchedEffect(app.packageName) {
        runCatching { firstAction.requestFocus() }
    }

    // Banner rendering happens off the main thread inside the ViewModel; the
    // key list makes it re-run when either the app or the style changes.
    val preview by produceState<Bitmap?>(initialValue = null, app.packageName, bannerStyle) {
        value = runCatching { renderBanner(app, bannerStyle) }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.42f)
            .background(TvColors.Surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 36.dp, vertical = 40.dp)
    ) {
        // ---- Banner preview -------------------------------------------------
        Text(
            text = stringResource(R.string.detail_preview_title),
            style = MaterialTheme.typography.labelMedium,
            color = TvColors.TextTertiary
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(TvColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            preview?.let { bitmap ->
                Image(
                    bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                    contentDescription = stringResource(R.string.detail_banner_preview, app.label),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Banner style picker -------------------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BannerStyle.entries.forEach { style ->
                TvSegmentedOption(
                    label = stringResource(style.labelRes()),
                    selected = style == bannerStyle,
                    onClick = { onBannerStyleChange(style) }
                )
            }
        }

        Spacer(Modifier.height(26.dp))

        // ---- Metadata -------------------------------------------------------
        Text(
            text = app.label,
            style = MaterialTheme.typography.titleLarge,
            color = TvColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.packageName,
            style = MaterialTheme.typography.bodyMedium,
            color = TvColors.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        // Resources must be read in the composable scope, not inside buildString.
        val separator = stringResource(R.string.detail_separator)
        val typeLabel = stringResource(
            if (app.hasLeanbackEntry) R.string.detail_type_tv else R.string.detail_type_mobile
        )
        val systemLabel = stringResource(R.string.detail_system)
        Text(
            text = buildString {
                if (app.versionName.isNotBlank()) append("v${app.versionName}$separator")
                append(typeLabel)
                if (app.isSystemApp) append(separator + systemLabel)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TvColors.TextSecondary
        )

        Spacer(Modifier.height(28.dp))

        // ---- Actions --------------------------------------------------------
        TvActionButton(
            label = stringResource(R.string.action_launch),
            icon = Icons.Default.PlayArrow,
            enabled = app.isLaunchable,
            onClick = { onLaunch(app) },
            modifier = Modifier.focusRequester(firstAction)
        )
        Spacer(Modifier.height(10.dp))
        // Kept clickable even when the launcher rejects pinned shortcuts: pressing it
        // then explains why nothing happened instead of leaving a dead, unfocusable row.
        TvActionButton(
            label = stringResource(R.string.action_pin),
            icon = Icons.Default.AddToHomeScreen,
            onClick = { onCreateShortcut(app) }
        )
        Spacer(Modifier.height(10.dp))
        TvActionButton(
            label = stringResource(
                if (app.isFavorite) R.string.action_favorite_remove else R.string.action_favorite_add
            ),
            icon = if (app.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            onClick = { onToggleFavorite(app) }
        )
        Spacer(Modifier.height(10.dp))
        TvActionButton(
            label = stringResource(R.string.action_app_info),
            icon = Icons.Default.Info,
            onClick = { onOpenSettings(app) }
        )
        if (!app.isSystemApp) {
            Spacer(Modifier.height(10.dp))
            TvActionButton(
                label = stringResource(R.string.action_uninstall),
                icon = Icons.Default.Delete,
                destructive = true,
                onClick = { onUninstall(app) }
            )
        }
    }
}

/** String resource holding the human label for a banner style. */
private fun BannerStyle.labelRes(): Int = when (this) {
    BannerStyle.GRADIENT_ACCENT -> R.string.banner_style_gradient
    BannerStyle.DARK_MINIMAL -> R.string.banner_style_dark
    BannerStyle.ICON_ONLY -> R.string.banner_style_icon
}
