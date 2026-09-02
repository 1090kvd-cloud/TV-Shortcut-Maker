package com.tvshortcut.maker.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.tvshortcut.maker.data.model.AppInfo
import com.tvshortcut.maker.launch.LaunchProxyActivity

/**
 * Creates home-screen shortcuts for apps that the TV launcher hides.
 *
 * Two mechanisms are used, in this order:
 *
 *  1. **Pinned shortcuts** ([ShortcutManagerCompat.requestPinShortcut], API 26+).
 *     Modern Google TV / Android TV launchers surface these in the "Apps" row.
 *  2. **Dynamic shortcuts** — always published as a side effect so that
 *     launchers and assistants that read `getShortcuts()` can still find the
 *     entry even when pinning is unsupported.
 *
 * Every shortcut targets [LaunchProxyActivity] rather than the third-party app
 * directly. That indirection means the shortcut keeps working after the target
 * app is updated (its launcher activity may be renamed) and lets us fall back
 * between the leanback and the phone entry point at launch time.
 */
class ShortcutHelper(
    private val context: Context,
    private val bannerFactory: BannerFactory
) {

    /** True when the current launcher accepts pinned shortcuts. */
    fun isPinningSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * Builds a banner for [app] and asks the launcher to pin it.
     *
     * @return `true` when the request was accepted (the launcher may still show
     *         a confirmation dialog), `false` when pinning is unavailable.
     */
    fun pinShortcut(app: AppInfo): Boolean {
        val shortcut = buildShortcut(app)

        // Publish as a dynamic shortcut regardless — harmless, and it makes the
        // entry discoverable through the launcher's shortcut APIs.
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }

        if (!isPinningSupported()) return false
        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.getOrDefault(false)
    }

    /** Removes any shortcut previously created for [packageName]. */
    fun removeShortcut(packageName: String) {
        runCatching {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId(packageName)))
        }
    }

    private fun buildShortcut(app: AppInfo): ShortcutInfoCompat {
        // A high-resolution icon is extracted specifically for the banner so the
        // artwork is not built from the small grid thumbnail.
        val hiResIcon = bannerFactory.extractIcon(app.packageName, size = 512) ?: app.icon
        val banner = bannerFactory.createBanner(hiResIcon)

        return ShortcutInfoCompat.Builder(context, shortcutId(app.packageName))
            .setShortLabel(app.label.take(SHORT_LABEL_MAX))
            .setLongLabel(app.label)
            .setIcon(IconCompat.createWithBitmap(banner))
            .setIntent(createLaunchIntent(app.packageName))
            .build()
    }

    /** The intent stored inside the shortcut; handled by [LaunchProxyActivity]. */
    private fun createLaunchIntent(packageName: String): Intent =
        Intent(context, LaunchProxyActivity::class.java).apply {
            action = LaunchProxyActivity.ACTION_LAUNCH_APP
            putExtra(LaunchProxyActivity.EXTRA_PACKAGE_NAME, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

    private fun shortcutId(packageName: String) = "$SHORTCUT_ID_PREFIX$packageName"

    private companion object {
        const val SHORTCUT_ID_PREFIX = "sideload_"
        /** Launchers truncate anything longer; keeping it short avoids ellipsis surprises. */
        const val SHORT_LABEL_MAX = 20
    }
}
