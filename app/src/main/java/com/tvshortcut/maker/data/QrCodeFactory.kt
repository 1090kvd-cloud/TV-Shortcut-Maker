package com.tvshortcut.maker.data

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders a URL as a QR code bitmap.
 *
 * A TV has no keyboard worth typing a link on, so the donation page is offered
 * as a code the viewer scans with a phone. Two details matter for that to work
 * across a living room:
 *
 *  - **white background, black modules** — cameras cope badly with inverted or
 *    low-contrast codes, so the code stays light even though the app is dark;
 *  - **generous module size** — the bitmap is rendered at the requested pixel
 *    size with no smoothing, keeping the edges of each module crisp when the
 *    launcher scales it up.
 */
object QrCodeFactory {

    /**
     * @param content the text to encode, normally an https:// URL
     * @param sizePx  side of the square bitmap in pixels
     * @return the rendered code, or `null` if the content could not be encoded
     */
    fun render(content: String, sizePx: Int = 512): Bitmap? = runCatching {
        val hints = mapOf(
            // A quiet zone of 1 module: the panel adds visual padding around it.
            EncodeHintType.MARGIN to 1,
            // Level M survives a slightly out-of-focus phone camera.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }.getOrNull()
}
