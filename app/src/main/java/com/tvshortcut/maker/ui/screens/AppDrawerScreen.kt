package com.tvshortcut.maker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvshortcut.maker.R
import com.tvshortcut.maker.data.model.AppFilter
import com.tvshortcut.maker.data.model.AppInfo
import com.tvshortcut.maker.ui.components.AppCard
import com.tvshortcut.maker.ui.components.EmptyState
import com.tvshortcut.maker.ui.components.LoadingState
import com.tvshortcut.maker.ui.components.MessageBar
import com.tvshortcut.maker.ui.components.TvFilterChip
import com.tvshortcut.maker.ui.components.TvSearchField
import com.tvshortcut.maker.ui.components.TvSegmentedOption
import com.tvshortcut.maker.ui.theme.TvColors
import com.tvshortcut.maker.viewmodel.AppListUiState

/**
 * The home screen of the app: a grid of every installed application, filtered
 * by how the TV launcher treats it.
 *
 * Layout notes for TV:
 *  - a 5-column grid keeps each tile large enough to read from a sofa;
 *  - horizontal padding is 48 dp to stay inside the 5 % overscan margin that
 *    some TVs still crop;
 *  - focus is requested on the grid after the first successful load so the user
 *    can start navigating immediately without pressing anything.
 */
@Composable
fun AppDrawerScreen(
    state: AppListUiState,
    onFilterChange: (AppFilter) -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onAppLaunch: (AppInfo) -> Unit,
    onDismissMessage: () -> Unit,
    onDonateClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val firstItemFocus = remember { FocusRequester() }

    // One layout, three form factors. Column count and padding follow the
    // available width instead of assuming a 16:9 television: a phone in portrait
    // gets two columns and tight margins, a TV keeps five and the overscan-safe
    // 48 dp inset.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val columns = when {
        screenWidthDp < 500 -> 2
        screenWidthDp < 720 -> 3
        screenWidthDp < 1000 -> 4
        else -> GRID_COLUMNS
    }
    val compact = screenWidthDp < 720
    val sideInset = if (compact) 20.dp else 48.dp

    // Move the D-Pad focus into the grid as soon as content is available.
    LaunchedEffect(state.isLoading, state.filter) {
        if (!state.isLoading && state.visibleApps.isNotEmpty()) {
            runCatching { firstItemFocus.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(TvColors.SurfaceElevated.copy(alpha = 0.55f), TvColors.Background)
                )
            )
    ) {
        Column(Modifier.fillMaxSize()) {

            DrawerHeader(
                state = state,
                onFilterChange = onFilterChange,
                onDonateClick = onDonateClick,
                onQueryChange = onQueryChange,
                sideInset = sideInset,
                compact = compact
            )

            when {
                state.isLoading -> LoadingState(stringResource(R.string.state_loading))

                state.visibleApps.isEmpty() -> EmptyState(
                    title = if (state.query.isBlank()) {
                        stringResource(R.string.state_empty)
                    } else {
                        stringResource(R.string.search_empty, state.query)
                    },
                    hint = if (state.query.isBlank()) {
                        stringResource(R.string.state_empty_hint)
                    } else {
                        stringResource(R.string.search_empty_hint)
                    }
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = sideInset, end = sideInset, top = 8.dp, bottom = 48.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = state.visibleApps,
                        // A stable key preserves focus/scroll across filter changes.
                        key = { it.packageName }
                    ) { app ->
                        AppCard(
                            app = app,
                            // Short press opens the detail panel; long press launches
                            // straight away for power users.
                            onClick = { onAppSelected(app) },
                            onLongClick = { onAppLaunch(app) },
                            modifier = if (app.packageName == state.visibleApps.first().packageName) {
                                Modifier.focusRequester(firstItemFocus)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
        }

        MessageBar(
            message = state.message,
            onDismiss = onDismissMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

/** Title block plus the row of filter chips. */
@Composable
private fun DrawerHeader(
    state: AppListUiState,
    onFilterChange: (AppFilter) -> Unit,
    onDonateClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    sideInset: Dp,
    compact: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = sideInset,
                end = sideInset,
                top = if (compact) 24.dp else 40.dp,
                bottom = if (compact) 12.dp else 20.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = if (compact) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.displayMedium,
                color = TvColors.TextPrimary
            )
            Spacer(Modifier.padding(horizontal = 10.dp))
            AnimatedVisibility(
                visible = !state.isLoading,
                enter = fadeIn() + slideInHorizontally(),
                exit = fadeOut() + slideOutHorizontally()
            ) {
                Text(
                    text = stringResource(R.string.msg_app_count, state.visibleApps.size),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvColors.TextTertiary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Pushed to the right edge of the title line: the filter row can
            // grow as long as it likes without ever hiding this button.
            Spacer(Modifier.weight(1f))
            TvSegmentedOption(
                label = "\u2665 " + stringResource(R.string.donate_action),
                selected = false,
                onClick = onDonateClick
            )
        }

        // Credit line: small, unobtrusive, and always visible without an extra
        // screen — a TV remote makes buried "About" pages annoying to reach.
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_developer) + ": " +
                stringResource(R.string.about_developer_name),
            style = MaterialTheme.typography.labelMedium,
            color = TvColors.TextTertiary
        )

        Spacer(Modifier.height(if (compact) 12.dp else 18.dp))

        // Filters scroll horizontally when they no longer fit on a phone.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = if (compact) {
                Modifier.horizontalScroll(rememberScrollState())
            } else {
                Modifier
            }
        ) {
            TvSearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.search_hint)
            )

            AppFilter.entries.forEach { filter ->
                TvFilterChip(
                    label = filter.displayName(),
                    count = state.countFor(filter),
                    selected = state.filter == filter,
                    onClick = { onFilterChange(filter) }
                )
            }
        }
    }
}

/** Localised chip title for a filter. */
@Composable
private fun AppFilter.displayName(): String = stringResource(
    when (this) {
        AppFilter.HIDDEN -> R.string.filter_hidden
        AppFilter.ALL -> R.string.filter_all
        AppFilter.TV -> R.string.filter_tv
        AppFilter.FAVORITES -> R.string.filter_favorites
    }
)

/** Five columns is the sweet spot for a 1080p/4K television. */
private const val GRID_COLUMNS = 5
