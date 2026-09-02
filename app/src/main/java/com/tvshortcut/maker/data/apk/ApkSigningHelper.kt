package com.tvshortcut.maker.data.apk

import android.content.Context
import com.android.apksig.ApkSigner
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Signs generated shortcut APKs so Android will install them.
 *
 * ABOUT THE KEY
 * -------------
 * The keystore lives in plain sight inside `assets/shortcut-signing.p12` and its
 * password is in this file. That is deliberate and harmless: the signature here
 * proves nothing about authorship, it only satisfies the installer. Anyone can
 * generate an equivalent stub with their own key — there is no secret to leak.
 *
 * One practical consequence worth knowing: all shortcuts share one signing
 * identity, so re-generating a shortcut cleanly replaces the previous version
 * of that package instead of failing with a signature mismatch.
 *
 * SIGNING SCHEMES
 * ---------------
 * The stub targets API 29, which keeps v1 (JAR) signatures acceptable on every
 * Android version. v2 is enabled as well because Android 11+ verifies it when
 * present and it makes installation faster. v3/v4 add rotation and streaming
 * support we have no use for.
 */
class ApkSigningHelper(private val context: Context) {

    companion object {
        private const val KEYSTORE_ASSET = "shortcut-signing.p12"
        private const val KEYSTORE_PASSWORD = "tvshortcut"
        private const val KEY_ALIAS = "shortcut"

        /** Matches `minSdk` of the :stub module. */
        private const val STUB_MIN_SDK = 21
    }

    class SigningException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Signs [unsignedApk] and writes the result to [outputFile].
     *
     * @return [outputFile] for chaining.
     */
    fun sign(unsignedApk: File, outputFile: File): File {
        val (privateKey, certificate) = loadKeyMaterial()

        val signerConfig = ApkSigner.SignerConfig.Builder(
            "shortcut",
            privateKey,
            listOf(certificate)
        ).build()

        runCatching {
            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(outputFile)
                .setMinSdkVersion(STUB_MIN_SDK)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(false)
                .build()
                .sign()
        }.getOrElse {
            throw SigningException("Failed to sign the generated shortcut", it)
        }

        return outputFile
    }

    /** Reads the bundled PKCS12 keystore out of assets. */
    private fun loadKeyMaterial(): Pair<PrivateKey, X509Certificate> = runCatching {
        val keyStore = KeyStore.getInstance("PKCS12")
        context.assets.open(KEYSTORE_ASSET).use { input ->
            keyStore.load(input, KEYSTORE_PASSWORD.toCharArray())
        }
        val key = keyStore.getKey(KEY_ALIAS, KEYSTORE_PASSWORD.toCharArray()) as PrivateKey
        val certificate = keyStore.getCertificate(KEY_ALIAS) as X509Certificate
        key to certificate
    }.getOrElse {
        throw SigningException("Signing key is missing or unreadable", it)
    }
}
