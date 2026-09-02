package com.tvshortcut.maker.data.apk

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ties the three pieces of shortcut creation together:
 * build the APK from the template, sign it, hand it to the installer.
 *
 * Every failure mode is turned into a [ShortcutResult] so the UI can say
 * something specific instead of "something went wrong".
 */
class ShortcutApkService(
    private val context: Context,
    private val builder: ShortcutApkBuilder,
    private val signer: ApkSigningHelper,
    private val installer: ShortcutInstaller
) {

    /** True when the app was built with the :stub template bundled in. */
    fun isSupported(): Boolean = builder.isTemplateAvailable()

    /**
     * Generates and installs a shortcut for [targetPackage].
     *
     * Runs on [Dispatchers.IO]: zip rewriting and RSA signing take a noticeable
     * fraction of a second even on fast hardware, and far longer on a cheap TV box.
     */
    suspend fun createShortcut(
        targetPackage: String,
        label: String,
        banner: Bitmap
    ): ShortcutResult = withContext(Dispatchers.IO) {
        if (!builder.isTemplateAvailable()) {
            return@withContext ShortcutResult.TemplateMissing
        }
        if (!installer.canInstallPackages()) {
            // Not an error: the user simply has not granted the permission yet.
            return@withContext ShortcutResult.PermissionRequired
        }

        val workDir = File(context.cacheDir, "shortcuts").apply { mkdirs() }
        val unsigned = File(workDir, "unsigned.apk")
        val signed = File(workDir, "shortcut.apk")

        try {
            builder.build(targetPackage, label, banner, unsigned)
            signer.sign(unsigned, signed)
            installer.install(signed, builder.stubPackageNameFor(targetPackage), label)
            ShortcutResult.InstallStarted
        } catch (e: ShortcutApkBuilder.BuildException) {
            ShortcutResult.Failed(e.message ?: "Could not build the shortcut")
        } catch (e: ApkSigningHelper.SigningException) {
            ShortcutResult.Failed(e.message ?: "Could not sign the shortcut")
        } catch (e: ShortcutInstaller.InstallException) {
            ShortcutResult.Failed(e.message ?: "Could not start the installation")
        } catch (e: Exception) {
            ShortcutResult.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            // The unsigned intermediate is never needed again.
            runCatching { unsigned.delete() }
        }
    }

    /** Sends the user to the system screen for enabling unknown-source installs. */
    fun requestInstallPermission() = installer.requestInstallPermission()
}

/** Outcome of a shortcut creation attempt. */
sealed interface ShortcutResult {
    /** APK handed to the package installer; the user now confirms the dialog. */
    data object InstallStarted : ShortcutResult

    /** "Install unknown apps" is not granted to this app yet. */
    data object PermissionRequired : ShortcutResult

    /** The build did not bundle the :stub template — a packaging problem. */
    data object TemplateMissing : ShortcutResult

    /** Anything else, with a message worth showing. */
    data class Failed(val reason: String) : ShortcutResult
}
