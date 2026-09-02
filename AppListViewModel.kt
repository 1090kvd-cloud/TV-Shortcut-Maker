package com.tvshortcut.maker.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.annotation.StringRes
import com.tvshortcut.maker.AppContainer
import com.tvshortcut.maker.R
import com.tvshortcut.maker.TvShortcutApplication
import com.tvshortcut.maker.data.model.AppFilter
import com.tvshortcut.maker.data.model.AppInfo
import com.tvshortcut.maker.data.apk.ShortcutResult
import com.tvshortcut.maker.data.model.BannerStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immutable snapshot rendered by the UI.
 *
 * [visibleApps] is derived rather than stored, which keeps the state impossible
 * to desynchronise: changing the filter can never leave a stale list behind.
 */
data class AppListUiState(
    val isLoading: Boolean = true,
    val allApps: List<AppInfo> = emptyList(),
    val filter: AppFilter = AppFilter.HIDDEN,
    val query: String = "",
    val selectedApp: AppInfo? = null,
    val bannerStyle: BannerStyle = BannerStyle.GRADIENT_ACCENT,
    val pinningSupported: Boolean = true,
    /** One-shot user feedback; cleared by [AppListViewModel.consumeMessage]. */
    val message: UiMessage? = null
) {
    val visibleApps: List<AppInfo>
        get() = allApps
            .filter { app ->
                when (filter) {
                    AppFilter.HIDDEN -> app.isHiddenOnTv
                    AppFilter.TV -> app.hasLeanbackEntry
                    AppFilter.FAVORITES -> app.isFavorite
                    AppFilter.ALL -> true
                }
            }
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }

    /** Number of apps per filter, shown as a badge in the top bar. */
    fun countFor(target: AppFilter): Int = allApps.count { app ->
        when (target) {
            AppFilter.HIDDEN -> app.isHiddenOnTv
            AppFilter.TV -> app.hasLeanbackEntry
            AppFilter.FAVORITES -> app.isFavorite
            AppFilter.ALL -> true
        }
    }
}

/** Transient message displayed as a toast-like snack bar. */
data class UiMessage(val text: String, val isError: Boolean = false)

class AppListViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Rescans the device. Called on start and whenever the app is resumed. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val apps = container.appRepository.loadInstalledApps()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    allApps = apps,
                    pinningSupported = container.shortcutHelper.isPinningSupported()
                )
            }
        }
    }

    fun setFilter(filter: AppFilter) = _uiState.update { it.copy(filter = filter) }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun select(app: AppInfo?) = _uiState.update { it.copy(selectedApp = app) }

    fun setBannerStyle(style: BannerStyle) = _uiState.update { it.copy(bannerStyle = style) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    // ---------------------------------------------------------------------
    //  Actions
    // ---------------------------------------------------------------------

    /** Starts [app] through the repository-resolved intent. */
    fun launch(app: AppInfo) {
        val intent = container.appRepository.resolveLaunchIntent(app.packageName)
        if (intent == null) {
            postMessage(R.string.msg_launch_failed, isError = true)
            return
        }
        // The intent carries FLAG_ACTIVITY_NEW_TASK, so starting it from the
        // application context is safe.
        runCatching { TvShortcutApplication.instance.startActivity(intent) }
            .onFailure { postMessage(R.string.msg_launch_error, app.label, isError = true) }
    }

    /**
     * Creates a home-screen shortcut for [app].
     *
     * Strategy: generate and install a tiny APK. Unlike `requestPinShortcut()`,
     * which most TV launchers silently ignore, an installed package always shows
     * up. If the generated-APK path is unavailable, fall back to pinning so the
     * feature still works on launchers that do support it.
     */
    fun createShortcut(app: AppInfo) {
        viewModelScope.launch {
            val style = _uiState.value.bannerStyle
            postMessage(R.string.msg_shortcut_building, app.label)

            if (!container.shortcutApkService.isSupported()) {
                createPinnedShortcut(app, style)
                return@launch
            }

            val banner = withContext(Dispatchers.Default) {
                val icon = container.bannerFactory.extractIcon(app.packageName, size = 512)
                    ?: app.icon
                container.bannerFactory.createBanner(icon, app.accentColor, app.label, style)
            }

            when (val result = container.shortcutApkService.createShortcut(
                targetPackage = app.packageName,
                label = app.label,
                banner = banner
            )) {
                is ShortcutResult.InstallStarted ->
                    postMessage(R.string.msg_shortcut_install_started, app.label)

                is ShortcutResult.PermissionRequired -> {
                    postMessage(R.string.msg_shortcut_permission, isError = true)
                    container.shortcutApkService.requestInstallPermission()
                }

                is ShortcutResult.TemplateMissing -> {
                    // Packaging problem — still try the legacy path rather than
                    // leaving the user with nothing.
                    createPinnedShortcut(app, style)
                }

                is ShortcutResult.Failed ->
                    postMessage(R.string.msg_shortcut_failed, result.reason, isError = true)
            }
        }
    }

    /** Legacy path: ask the launcher to pin a shortcut. */
    private suspend fun createPinnedShortcut(app: AppInfo, style: BannerStyle) {
        val pinned = withContext(Dispatchers.Default) {
            container.shortcutHelper.pinShortcut(app, style)
        }
        if (pinned) {
            postMessage(R.string.msg_pin_ok, app.label)
        } else {
            postMessage(R.string.msg_pin_unsupported, isError = true)
        }
    }

    fun toggleFavorite(app: AppInfo) {
        val nowFavorite = container.appRepository.toggleFavorite(app.packageName)
        // Patch the cached list in place instead of rescanning: a full rescan
        // would take ~1 s and visibly reset the D-Pad focus.
        _uiState.update { state ->
            val updated = state.allApps.map {
                if (it.packageName == app.packageName) it.copy(isFavorite = nowFavorite) else it
            }
            state.copy(
                allApps = updated,
                selectedApp = state.selectedApp?.takeIf { it.packageName == app.packageName }
                    ?.copy(isFavorite = nowFavorite) ?: state.selectedApp
            )
        }
    }

    fun openSystemSettings(app: AppInfo) = container.appRepository.openSystemSettings(app.packageName)

    fun requestUninstall(app: AppInfo) = container.appRepository.requestUninstall(app.packageName)

    /** Renders a preview of the banner exactly as it will be pinned. */
    suspend fun renderBannerPreview(app: AppInfo, style: BannerStyle): Bitmap =
        withContext(Dispatchers.Default) {
            val icon = container.bannerFactory.extractIcon(app.packageName, size = 384) ?: app.icon
            container.bannerFactory.createBanner(icon, app.accentColor, app.label, style)
        }

    /**
     * Resolves [resId] against the application context so that every user-facing
     * string comes from res/values-*, keeping the app fully localisable.
     */
    private fun postMessage(@StringRes resId: Int, vararg args: Any, isError: Boolean = false) {
        val text = TvShortcutApplication.instance.getString(resId, *args)
        _uiState.update { it.copy(message = UiMessage(text, isError)) }
    }

    companion object {
        /** Factory wiring the hand-rolled service locator into the ViewModel. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppListViewModel(TvShortcutApplication.instance.container)
            }
        }
    }
}
