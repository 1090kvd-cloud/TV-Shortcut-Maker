package com.tvshortcut.maker.data.model

import android.graphics.Bitmap

/**
 * A single installed application as presented by the UI.
 *
 * @param packageName        Unique package id, e.g. `com.spotify.music`.
 * @param label              Human readable name resolved through the PackageManager.
 * @param versionName        Version string shown in the detail panel (may be empty).
 * @param isSystemApp        True for apps living in /system (pre-installed).
 * @param hasLeanbackEntry   True when the app declares CATEGORY_LEANBACK_LAUNCHER,
 *                           i.e. it is already visible in the Android TV launcher.
 * @param hasMobileEntry     True when the app declares the classic CATEGORY_LAUNCHER.
 * @param icon               Icon extracted from the APK, already down-scaled for the grid.
 * @param accentColor        Dominant ARGB colour of [icon], used for the focus glow
 *                           and for the generated banner gradient.
 * @param isFavorite         Whether the user pinned this app inside our own drawer.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String = "",
    val isSystemApp: Boolean = false,
    val hasLeanbackEntry: Boolean = false,
    val hasMobileEntry: Boolean = false,
    val icon: Bitmap? = null,
    val accentColor: Int = 0xFF7C6BFF.toInt(),
    val isFavorite: Boolean = false
) {
    /**
     * The apps this project exists for: they *can* be launched, but the TV
     * launcher refuses to show them because there is no leanback entry point.
     */
    val isHiddenOnTv: Boolean get() = hasMobileEntry && !hasLeanbackEntry

    /** Anything we are able to start at all. */
    val isLaunchable: Boolean get() = hasMobileEntry || hasLeanbackEntry
}

/** Filters offered in the top bar of the drawer. */
enum class AppFilter(val titleResSuffix: String) {
    HIDDEN("hidden"),   // sideloaded / mobile-only apps  -> default
    ALL("all"),
    TV("tv"),
    FAVORITES("favorites");
}
