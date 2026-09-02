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
import com.tvshortcut.maker.data.model.BannerStyle
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
     * Draws the final 16:9 banner.
     *
     * @param icon      Square app icon.
     * @param accent    Accent colour, normally produced by [accentColorOf].
     * @param label     App name printed next to the icon (ignored for [BannerStyle.ICON_ONLY]).
     * @param style     Background treatment.
     * @param showLabel Set to false when the launcher already renders the title itself.
     */
    fun createBanner(
        icon: Bitmap?,
        accent: Int,
        label: String,
        style: BannerStyle = BannerStyle.GRADIENT_ACCENT,
        showLabel: Boolean = true
    ): Bitmap {
        val w = BANNER_WIDTH * BANNER_SCALE
        val h = BANNER_HEIGHT * BANNER_SCALE
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (style) {
            BannerStyle.GRADIENT_ACCENT -> drawAccentBackground(canvas, w, h, accent)
            BannerStyle.DARK_MINIMAL -> drawDarkBackground(canvas, w, h)
            BannerStyle.ICON_ONLY -> Unit // fully transparent, icon fills the frame
        }

        if (icon != null) {
            if (style == BannerStyle.ICON_ONLY) {
                drawIconAsIs(canvas, icon, w, h)
            } else {
                drawIconTile(canvas, icon, w, h, showLabel)
            }
        }

        if (showLabel && style != BannerStyle.ICON_ONLY) {
            drawLabel(canvas, label, w, h)
        }
        return bitmap
    }

    /**
     * Draws the icon untouched: centred, aspect ratio preserved, on a fully
     * transparent 16:9 canvas.
     *
     * This is what makes a generated shortcut look like a normally installed
     * app — the launcher paints its own card behind the transparent area, so
     * the tile is indistinguishable from the real thing. Nothing is cropped,
     * stretched or overlaid with text.
     */
    private fun drawIconAsIs(canvas: Canvas, icon: Bitmap, w: Int, h: Int) {
        // Occupy most of the banner height, leaving a small breathing margin.
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

    /** Diagonal gradient tinted by the icon's accent plus a soft radial glow. */
    private fun drawAccentBackground(canvas: Canvas, w: Int, h: Int, accent: Int) {
        val top = blend(accent, NEUTRAL_DARK, 0.55f)
        val bottom = blend(accent, NEUTRAL_DARK, 0.9f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                top, bottom, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // Glow behind the icon so the composition has a focal point.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.26f, h * 0.5f, h * 0.75f,
                intArrayOf(withAlpha(accent, 140), withAlpha(accent, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glow)
    }

    /** Neutral dark card, matching the in-app surface colour. */
    private fun drawDarkBackground(canvas: Canvas, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                NEUTRAL_SURFACE, NEUTRAL_DARK, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    /** Rounded "app tile" holding the icon on the left third of the banner. */
    private fun drawIconTile(canvas: Canvas, icon: Bitmap, w: Int, h: Int, compact: Boolean) {
        val tile = h * if (compact) 0.56f else 0.62f
        val cx = if (compact) w * 0.24f else w * 0.5f
        val cy = h * 0.5f - if (compact) 0f else h * 0.06f
        val rect = RectF(cx - tile / 2, cy - tile / 2, cx + tile / 2, cy + tile / 2)

        // Soft shadow under the tile.
        canvas.drawRoundRect(
            RectF(rect.left, rect.top + tile * 0.06f, rect.right, rect.bottom + tile * 0.06f),
            tile * 0.22f, tile * 0.22f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(Color.BLACK, 90) }
        )
        canvas.drawBitmap(
            roundCorners(icon, rect.width().toInt().coerceAtLeast(1)),
            null, rect, antiAliasPaint()
        )
    }

    /** App title, truncated with an ellipsis so it never overflows the banner. */
    private fun drawLabel(canvas: Canvas, label: String, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = h * 0.13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, withAlpha(Color.BLACK, 160))
        }
        val left = w * 0.42f
        val available = w - left - w * 0.06f
        var text = label
        while (paint.measureText(text) > available && text.length > 3) {
            text = text.dropLast(2)
        }
        if (text != label) text = "$text…"
        val baseline = h * 0.5f + paint.textSize * 0.35f
        canvas.drawText(text, left, baseline, paint)
    }

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
