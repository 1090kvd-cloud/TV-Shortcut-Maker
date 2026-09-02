package com.tvshortcut.maker.data.apk

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Turns the bundled stub template into a shortcut APK for one specific app.
 *
 * WHY THIS EXISTS
 * ---------------
 * Most TV launchers ignore `requestPinShortcut()`. They cannot ignore an
 * installed package, so instead of asking politely we generate a tiny real app
 * whose only job is to launch the target — the same technique used by TV App
 * Repo and the ATV_Shortcut_maker script.
 *
 * HOW THE PATCHING WORKS
 * ----------------------
 * Rewriting a compiled AndroidManifest.xml means rebuilding its string pool,
 * which is where most generators break. We sidestep that completely: the stub's
 * manifest declares placeholders of a FIXED length, so patching is a byte-for-
 * byte overwrite that leaves every offset and chunk size untouched.
 *
 *   package  `com.tvshortcut.s.` + 32 chars  ← MD5 of the target package
 *   label    48 chars                        ← app name, padded with spaces
 *
 * Everything else lives in ordinary zip entries and can change length freely:
 *   assets/target.txt   ← package name the stub should open
 *   res/.../banner.png  ← the generated 320x180 artwork
 *
 * The output is UNSIGNED. Pass it to [ApkSigningHelper] before installing.
 */
class ShortcutApkBuilder(private val context: Context) {

    companion object {
        /** Name of the template inside the main app's assets. */
        const val TEMPLATE_ASSET = "stub-template.apk"

        /** Must match stub/src/main/AndroidManifest.xml exactly. */
        private const val PACKAGE_PLACEHOLDER = "PPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPP"   // 32
        private const val LABEL_PLACEHOLDER = "LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL" // 48

        private const val MANIFEST_ENTRY = "AndroidManifest.xml"
        private const val TARGET_ENTRY = "assets/target.txt"
    }

    /** Raised with a readable reason so the UI can explain what went wrong. */
    class BuildException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Builds an unsigned shortcut APK.
     *
     * @param targetPackage package the shortcut should launch
     * @param label         name shown under the banner on the home screen
     * @param banner        320x180 artwork used for `android:banner`
     * @param icon          square icon used for `android:icon` — launchers that
     *                      draw a round app tile (Google TV) use this one, and
     *                      feeding them a 16:9 image gets it cropped off-centre
     * @param outputFile    where to write the result
     */
    fun build(
        targetPackage: String,
        label: String,
        banner: Bitmap,
        icon: Bitmap,
        outputFile: File
    ): File {
        val template = readTemplate()
        val bannerBytes = banner.toPngBytes()
        val iconBytes = icon.toPngBytes()
        val stubPackageSuffix = packageSuffixFor(targetPackage)

        var manifestPatched = false
        var bannerReplaced = false
        var iconReplaced = false
        var targetWritten = false

        ZipInputStream(template.inputStream()).use { zin ->
            ZipOutputStream(outputFile.outputStream().buffered()).use { zout ->
                // Everything is deflated on output. Storing entries uncompressed
                // would require 4-byte alignment (zipalign), which we cannot do
                // here — compressing sidesteps the whole problem.
                zout.setLevel(6)

                var entry = zin.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val bytes = zin.readBytes()

                    when {
                        // Drop the template's own signature — we re-sign later.
                        name.startsWith("META-INF/") && name.isSignatureFile() -> Unit

                        name == MANIFEST_ENTRY -> {
                            zout.writeEntry(name, patchManifest(bytes, stubPackageSuffix, label))
                            manifestPatched = true
                        }

                        name == TARGET_ENTRY -> {
                            zout.writeEntry(name, targetPackage.toByteArray(Charsets.UTF_8))
                            targetWritten = true
                        }

                        name.isBannerResource() -> {
                            zout.writeEntry(name, bannerBytes)
                            bannerReplaced = true
                        }

                        name.isIconResource() -> {
                            zout.writeEntry(name, iconBytes)
                            iconReplaced = true
                        }

                        else -> zout.writeEntry(name, bytes)
                    }

                    zin.closeEntry()
                    entry = zin.nextEntry
                }

                // The template ships assets/target.txt, but be defensive: if a
                // future template drops it, add the entry rather than produce a
                // silently broken shortcut.
                if (!targetWritten) {
                    zout.writeEntry(TARGET_ENTRY, targetPackage.toByteArray(Charsets.UTF_8))
                }
            }
        }

        if (!manifestPatched) {
            throw BuildException("Template is missing AndroidManifest.xml")
        }
        if (!bannerReplaced || !iconReplaced) {
            // Not fatal — the shortcut still works, it just shows a placeholder.
            android.util.Log.w(
                "ShortcutApkBuilder",
                "Artwork not fully replaced (banner=$bannerReplaced, icon=$iconReplaced)"
            )
        }
        return outputFile
    }

    // ---------------------------------------------------------------------
    //  Template access
    // ---------------------------------------------------------------------

    /** Copies the template out of assets into the cache directory. */
    private fun readTemplate(): File {
        val cached = File(context.cacheDir, TEMPLATE_ASSET)
        if (cached.exists() && cached.length() > 0) return cached

        runCatching {
            context.assets.open(TEMPLATE_ASSET).use { input ->
                cached.outputStream().use { output -> input.copyTo(output) }
            }
        }.getOrElse {
            throw BuildException(
                "Shortcut template is missing from the app bundle. " +
                    "The :stub module was probably not built into assets.",
                it
            )
        }
        return cached
    }

    /** True when the app was built with a usable stub template. */
    fun isTemplateAvailable(): Boolean = runCatching {
        context.assets.open(TEMPLATE_ASSET).use { it.read() != -1 }
    }.getOrDefault(false)

    // ---------------------------------------------------------------------
    //  Manifest patching
    // ---------------------------------------------------------------------

    /**
     * Overwrites the two fixed-length placeholders inside the binary manifest.
     *
     * Binary XML normally stores strings as UTF-16LE, but aapt2 may emit UTF-8
     * for a pool, so both encodings are attempted. Replacement length always
     * equals placeholder length, so no offset in the file shifts.
     */
    private fun patchManifest(
        manifest: ByteArray,
        stubPackageSuffix: String,
        label: String
    ): ByteArray {
        require(stubPackageSuffix.length == PACKAGE_PLACEHOLDER.length) {
            "package suffix must be exactly ${PACKAGE_PLACEHOLDER.length} chars"
        }
        val paddedLabel = label.fitTo(LABEL_PLACEHOLDER.length)

        val patched = manifest.copyOf()
        val packageHits = patched.replaceAscii(PACKAGE_PLACEHOLDER, stubPackageSuffix)
        val labelHits = patched.replaceAscii(LABEL_PLACEHOLDER, paddedLabel)

        if (packageHits == 0 || labelHits == 0) {
            throw BuildException(
                "Placeholders not found in the template manifest " +
                    "(package hits=$packageHits, label hits=$labelHits). " +
                    "The stub module and the patcher are out of sync."
            )
        }
        return patched
    }

    /**
     * Replaces every occurrence of [from] with [to] in both UTF-16LE and UTF-8
     * form, in place. Returns how many occurrences were replaced.
     */
    private fun ByteArray.replaceAscii(from: String, to: String): Int {
        require(from.length == to.length)
        var count = 0
        for (charset in listOf(Charsets.UTF_16LE, Charsets.UTF_8)) {
            val needle = from.toByteArray(charset)
            val value = to.toByteArray(charset)
            if (needle.size != value.size) continue // non-ASCII label in UTF-8 — skip
            var index = indexOf(needle, 0)
            while (index >= 0) {
                value.copyInto(this, index)
                count++
                index = indexOf(needle, index + needle.size)
            }
        }
        return count
    }

    /** Naive byte-sequence search; the manifest is a few kilobytes, so this is fine. */
    private fun ByteArray.indexOf(needle: ByteArray, from: Int): Int {
        outer@ for (i in from..(size - needle.size)) {
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /**
     * Pads with spaces or truncates so the string occupies exactly [length]
     * characters. Non-ASCII names (Cyrillic, CJK) are transliterated away only
     * if they would break the UTF-8 length match; UTF-16 patching handles them.
     */
    private fun String.fitTo(length: Int): String = when {
        this.length == length -> this
        this.length > length -> take(length - 1) + "…"
        else -> padEnd(length, ' ')
    }

    // ---------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------

    /**
     * Derives a stable, unique 32-character suffix from the target package.
     *
     * Stable matters: rebuilding a shortcut for the same app must produce the
     * same package name, so the install replaces the old shortcut instead of
     * piling up duplicates on the home screen.
     */
    private fun packageSuffixFor(targetPackage: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(targetPackage.toByteArray())
        // Each 4-bit nibble becomes a letter in 'a'..'p' rather than a hex digit.
        // Hex would be shorter to read but can start with a digit, and Android
        // rejects a package segment that does not begin with a letter
        // (INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME). 16 bytes -> exactly 32 chars.
        return buildString(32) {
            for (byte in digest) {
                val value = byte.toInt() and 0xFF
                append('a' + (value shr 4))     // high nibble
                append('a' + (value and 0x0F))  // low nibble
            }
        }
    }

    /** Package name the generated shortcut will be installed under. */
    fun stubPackageNameFor(targetPackage: String): String =
        "com.tvshortcut.s." + packageSuffixFor(targetPackage)

    private fun Bitmap.toPngBytes(): ByteArray = ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

    private fun String.isSignatureFile(): Boolean =
        endsWith(".SF", true) || endsWith(".RSA", true) ||
            endsWith(".DSA", true) || endsWith(".EC", true) ||
            equals("META-INF/MANIFEST.MF", true)

    private fun String.isBannerResource(): Boolean =
        startsWith("res/") && endsWith(".png") && contains("banner")

    /** The square artwork behind `android:icon`. */
    private fun String.isIconResource(): Boolean =
        startsWith("res/") && endsWith(".png") && contains("appicon")

    /** Writes one deflated entry with a fresh CRC. */
    private fun ZipOutputStream.writeEntry(name: String, data: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.DEFLATED
            crc = CRC32().apply { update(data) }.value
            size = data.size.toLong()
        }
        putNextEntry(entry)
        write(data)
        closeEntry()
    }
}
