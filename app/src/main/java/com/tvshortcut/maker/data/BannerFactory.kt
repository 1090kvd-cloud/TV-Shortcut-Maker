package com.tvshortcut.maker.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.min

/**
 * Everything related to turning an APK icon into something that looks good on a
 * 10-foot screen.
 *
 * Android TV expects launcher artwork with a 16:9 ratio (the reference size is
 * 320x180 dp). Mobile icons are square, so we compose them onto a generated
 * background instead of letting the launcher stretch them.
 */
class BannerFactory(private val context: Context) {

    companion object {
        /** Reference banner size defined by the Android TV design guidelines. */
        const val BANNER_WIDTH = 320
        const val BANNER_HEIGHT = 180

        /** Render at 3x and let the launcher downscale — keeps edges crisp on 4K panels. */
        private const val BANNER_SCALE = 3

        /** Size used for the icons displayed inside the grid. */
        private const val GRID_ICON_SIZE = 144

        private val FALLBACK_ACCENT = Color.parseColor("#7C6BFF")
        private val NEUTRAL_DARK = Color.parseColor("#0B0E14")
        private val NEUTRAL_SURFACE = Color.parseColor("#141922")
    }

    private val packageManager = context.packageManager

    // ---------------------------------------------------------------------
    //  Icon extraction
    // ---------------------------------------------------------------------

    /**
     * Pulls the launcher icon straight out of the installed APK and rasterises it.
     *
     * Handles the three shapes an icon can come in:
     *  - [BitmapDrawable]        — the common legacy case;
     *  - [AdaptiveIconDrawable]  — API 26+, rendered with its own background layer;
     *  - anything else (vector, layer-list) — drawn onto a fresh canvas.
     */
    fun extractIcon(appInfo: ApplicationInfo, size: Int = GRID_ICON_SIZE): Bitmap? = runCatching {
        val drawable: Drawable = packageManager.getApplicationIcon(appInfo)
        drawable.toBitmap(size)
    }.getOrNull()

    /** Same as above but resolved from a package name. */
    fun extractIcon(packageName: String, size: Int = GRID_ICON_SIZE): Bitmap? = runCatching {
        packageManager.getApplicationIcon(packageName).toBitmap(size)
    }.getOrNull()

    /** Converts any [Drawable] into a square bitmap of [size] px. */
    private fun Drawable.toBitmap(size: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(canvas)
        return bmp
    }

    // ---------------------------------------------------------------------
    //  Colour analysis
    // ---------------------------------------------------------------------

    /**
     * Picks a saturated, TV-friendly accent colour out of an icon.
     *
     * Palette is asked for a vibrant swatch first; if the icon is monochrome we
     * fall back to the dominant swatch and finally to the app's own purple.
     */
    fun accentColorOf(icon: Bitmap?): Int {
        if (icon == null) return FALLBACK_ACCENT
        return runCatching {
            val palette = Palette.from(icon).clearFilters().maximumColorCount(16).generate()
            val raw = palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.darkVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: FALLBACK_ACCENT
            raw.boostForDarkBackground()
        }.getOrDefault(FALLBACK_ACCENT)
    }

    /**
     * Nudges a colour into a range that reads well on a dark background:
     * saturation is raised, and extreme lightness values are clamped so that
     * white/black icons do not produce an unusable grey banner.
     */
    private fun Int.boostForDarkBackground(): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(this, hsv)
        hsv[1] = max(0.45f, min(hsv[1] * 1.25f, 0.9f))   // saturation
        hsv[2] = max(0.45f, min(hsv[2], 0.85f))          // value
        return Color.HSVToColor(hsv)
    }

    // ---------------------------------------------------------------------
    //  Banner rendering
    // ---------------------------------------------------------------------

    /**
     * Builds the 16:9 artwork for `android:banner`: the app's own icon, centred,
     * aspect ratio preserved, on a transparent canvas.
     *
     * No gradients, no overlaid text. The launcher paints its own card behind
     * the transparent area and draws the title itself, which is what makes a
     * generated shortcut indistinguishable from a normally installed app.
     */
    fun createBanner(icon: Bitmap?): Bitmap {
        val w = BANNER_WIDTH * BANNER_SCALE
        val h = BANNER_HEIGHT * BANNER_SCALE
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        if (icon != null) {
            val canvas = Canvas(bitmap)
            val side = h * 0.78f
            val left = (w - side) / 2f
            val top = (h - side) / 2f
            canvas.drawBitmap(
                icon,
                null,
                RectF(left, top, left + side, top + side),
                antiAliasPaint()
            )
        }
        return bitmap
    }

    /**
     * Square artwork for `android:icon`, used by launchers that draw a round
     * tile (Google TV does). The icon is passed through untouched — cropping or
     * recolouring it here is exactly what makes a shortcut look "not native".
     */
    fun createIconArtwork(icon: Bitmap?, size: Int = 512): Bitmap =
        icon ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

    /** Applies a 22 % corner radius, matching Material You icon shapes. */
    private fun roundCorners(source: Bitmap, size: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(source, size, size, true)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = antiAliasPaint()
        val radius = size * 0.22f
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        return output
    }

    private fun antiAliasPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    /** Linear interpolation between two ARGB colours. */
    private fun blend(from: Int, to: Int, ratio: Float): Int = Color.argb(
        255,
        (Color.red(from) * (1 - ratio) + Color.red(to) * ratio).toInt(),
        (Color.green(from) * (1 - ratio) + Color.green(to) * ratio).toInt(),
        (Color.blue(from) * (1 - ratio) + Color.blue(to) * ratio).toInt()
    )

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /**
     * Adaptive icons ship with their own mask; on API 26+ we can therefore keep
     * the original background layer instead of re-rounding the icon ourselves.
     */
    fun isAdaptive(packageName: String): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            runCatching {
                packageManager.getApplicationIcon(packageName) is AdaptiveIconDrawable
            }.getOrDefault(false)
}
