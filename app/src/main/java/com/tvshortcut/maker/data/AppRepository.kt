package com.tvshortcut.maker.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import com.tvshortcut.maker.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for the list of installed applications.
 *
 * The interesting bit is the classification: an app is "hidden on TV" when it
 * exposes `CATEGORY_LAUNCHER` but not `CATEGORY_LEANBACK_LAUNCHER`. Those are
 * exactly the sideloaded phone apps the Android TV home screen refuses to show.
 */
class AppRepository(
    private val context: Context,
    private val bannerFactory: BannerFactory,
    private val favoritesStore: FavoritesStore
) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Scans the device and builds the UI model.
     *
     * Runs on [Dispatchers.IO]: reading + rasterising a few hundred icons is
     * far too slow for the main thread and would drop D-Pad input frames.
     */
    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val ownPackage = context.packageName

        // 1. Which packages have a classic phone launcher entry?
        val mobileEntries = queryLaunchable(Intent.CATEGORY_LAUNCHER)
        // 2. Which packages are already visible on the TV home screen?
        val leanbackEntries = queryLaunchable(Intent.CATEGORY_LEANBACK_LAUNCHER)

        val favorites = favoritesStore.getFavorites()

        // 3. Enumerate everything, then keep what we can actually start.
        val installed: List<ApplicationInfo> = runCatching {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())

        installed.asSequence()
            .filter { it.packageName != ownPackage }
            .filter { it.packageName in mobileEntries || it.packageName in leanbackEntries }
            .map { info ->
                val icon = bannerFactory.extractIcon(info)
                AppInfo(
                    packageName = info.packageName,
                    label = runCatching { packageManager.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName),
                    versionName = versionNameOf(info.packageName),
                    isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                    hasLeanbackEntry = info.packageName in leanbackEntries,
                    hasMobileEntry = info.packageName in mobileEntries,
                    icon = icon,
                    accentColor = bannerFactory.accentColorOf(icon),
                    isFavorite = info.packageName in favorites
                )
            }
            // Case-insensitive alphabetical order — easiest to scan with a remote.
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Returns the set of package names owning an activity for [category]. */
    private fun queryLaunchable(category: String): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val resolved: List<ResolveInfo> = runCatching {
            packageManager.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())
        return resolved.mapNotNull { it.activityInfo?.packageName }.toSet()
    }

    private fun versionNameOf(packageName: String): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    // ---------------------------------------------------------------------
    //  Launching
    // ---------------------------------------------------------------------

    /**
     * Builds the intent that actually starts [packageName].
     *
     * Order matters: we prefer the leanback entry point when one exists (it is
     * the TV-optimised activity), otherwise we fall back to the phone launcher
     * activity, which is what every sideloaded app provides.
     *
     * Returns `null` when the package exposes no launchable activity at all.
     */
    fun resolveLaunchIntent(packageName: String): Intent? {
        val leanback = runCatching {
            packageManager.getLeanbackLaunchIntentForPackage(packageName)
        }.getOrNull()
        val standard = runCatching {
            packageManager.getLaunchIntentForPackage(packageName)
        }.getOrNull()

        return (leanback ?: standard)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
    }

    /** Opens the system "App info" screen for [packageName]. */
    fun openSystemSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /** Asks the system to uninstall [packageName] (shows the standard dialog). */
    fun requestUninstall(packageName: String) {
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    // ---------------------------------------------------------------------
    //  Favourites
    // ---------------------------------------------------------------------

    fun toggleFavorite(packageName: String): Boolean = favoritesStore.toggle(packageName)
}
