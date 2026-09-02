package com.tvshortcut.maker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvshortcut.maker.ui.screens.AppDetailPanel
import com.tvshortcut.maker.ui.screens.AppDrawerScreen
import com.tvshortcut.maker.ui.theme.TvColors
import com.tvshortcut.maker.ui.theme.TvShortcutTheme
import com.tvshortcut.maker.viewmodel.AppListViewModel

/**
 * The single activity of the app.
 *
 * Everything is Compose; there is no fragment or Leanback fragment involved.
 * The activity itself only wires the ViewModel to the two screens and rescans
 * the device on resume (an app may have been installed or removed while we
 * were in the background).
 */
class MainActivity : ComponentActivity() {

    private var viewModelRef: AppListViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvShortcutTheme {
                val viewModel: AppListViewModel = viewModel(factory = AppListViewModel.Factory)
                viewModelRef = viewModel
                TvShortcutApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Cheap enough (~1 s on a mid-range TV box) and guarantees a fresh list
        // right after the user installs or uninstalls something.
        viewModelRef?.refresh()
    }
}

/** Root composable: the drawer with the detail panel layered on top. */
@Composable
private fun TvShortcutApp(viewModel: AppListViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
    ) {
        AppDrawerScreen(
            state = state,
            onFilterChange = viewModel::setFilter,
            onAppSelected = { viewModel.select(it) },
            onAppLaunch = { viewModel.launch(it) },
            onDismissMessage = viewModel::consumeMessage
        )

        AppDetailPanel(
            app = state.selectedApp,
            pinningSupported = state.pinningSupported,
            onLaunch = { viewModel.launch(it) },
            onCreateShortcut = {
                viewModel.createShortcut(it)
                viewModel.select(null)
            },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onOpenSettings = { viewModel.openSystemSettings(it) },
            onUninstall = { viewModel.requestUninstall(it) },
            onDismiss = { viewModel.select(null) },
            renderBanner = viewModel::renderBannerPreview
        )
    }
}
