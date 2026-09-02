package com.tvshortcut.maker.data.apk

import android.content.Context
import com.android.apksig.ApkSigner
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Signs generated shortcut APKs so Android will install them.
 *
 * ABOUT THE KEY
 * -------------
 * The key material lives in plain sight inside assets. That is deliberate and
 * harmless: the signature here proves nothing about authorship, it only
 * satisfies the installer. Anyone can generate an equivalent stub with their own
 * key, so there is no secret to leak.
 *
 * It is stored as a raw PKCS#8 key plus a DER certificate rather than a PKCS12
 * keystore, because modern keytool encrypts PKCS12 files with PBES2/AES-256 and
 * Android's PKCS12 provider cannot read that — it fails with "unreadable
 * keystore". The raw form is parsed by plain java.security classes and is
 * exactly what AOSP's own signapk tool uses.
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
        private const val KEY_ASSET = "shortcut-key.pk8"      // PKCS#8, DER, unencrypted
        private const val CERT_ASSET = "shortcut-cert.der"    // X.509, DER

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

    /** Reads the raw key and certificate out of assets. */
    private fun loadKeyMaterial(): Pair<PrivateKey, X509Certificate> {
        val privateKey = runCatching {
            val der = context.assets.open(KEY_ASSET).use { it.readBytes() }
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
        }.getOrElse {
            throw SigningException("Signing key ($KEY_ASSET) is missing or unreadable", it)
        }

        val certificate = runCatching {
            context.assets.open(CERT_ASSET).use { input ->
                CertificateFactory.getInstance("X.509")
                    .generateCertificate(input) as X509Certificate
            }
        }.getOrElse {
            throw SigningException("Certificate ($CERT_ASSET) is missing or unreadable", it)
        }

        return privateKey to certificate
    }
}
