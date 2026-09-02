package com.tvshortcut.maker.data.apk

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Installs a generated shortcut APK.
 *
 * Uses [PackageInstaller] rather than the older ACTION_INSTALL_PACKAGE intent:
 * it needs no FileProvider, works with a file we wrote to our own cache, and
 * reports progress back through a broadcast.
 *
 * The user still confirms every install — that is enforced by the system for
 * non-privileged apps and cannot (and should not) be bypassed.
 */
class ShortcutInstaller(private val context: Context) {

    companion object {
        /** Action delivered to [InstallResultReceiver] with the session outcome. */
        const val ACTION_INSTALL_RESULT = "com.tvshortcut.maker.INSTALL_RESULT"

        /** Human-readable name of the app being installed, echoed back to the receiver. */
        const val EXTRA_APP_LABEL = "com.tvshortcut.maker.extra.APP_LABEL"

        private const val BUFFER_SIZE = 64 * 1024
    }

    class InstallException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Whether the app is allowed to install packages.
     *
     * Below API 26 the install-time permission is enough; from API 26 the user
     * grants "install unknown apps" per source app.
     */
    fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /**
     * Opens the system screen where the user enables installing from this app.
     * On Android TV this lands in Settings; there is no in-app equivalent.
     */
    fun requestInstallPermission() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(intent) }
            .onFailure {
                // Some TV firmwares ship without that settings screen at all.
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
    }

    /**
     * Streams [apk] into a new install session and commits it.
     *
     * The call returns as soon as the session is committed; the actual outcome
     * arrives later at [InstallResultReceiver], including the confirmation
     * dialog the system shows to the user.
     *
     * @param packageName package the APK declares — lets the installer show a
     *                    replace-instead-of-add dialog for an existing shortcut.
     * @param appLabel    name of the target app, used for the result message.
     */
    fun install(apk: File, packageName: String, appLabel: String) {
        val installer = context.packageManager.packageInstaller

        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(packageName)
        }

        val sessionId = runCatching { installer.createSession(params) }
            .getOrElse { throw InstallException("Could not open an install session", it) }

        runCatching {
            installer.openSession(sessionId).use { session ->
                session.openWrite("shortcut.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { input ->
                        input.copyTo(output, BUFFER_SIZE)
                    }
                    // Required: without fsync the session may be committed with
                    // a partially written APK on some devices.
                    session.fsync(output)
                }
                session.commit(buildStatusIntentSender(sessionId, appLabel))
            }
        }.getOrElse {
            runCatching { installer.abandonSession(sessionId) }
            throw InstallException("Could not write the shortcut into the install session", it)
        }
    }

    /** PendingIntent the system fires with the session status. */
    private fun buildStatusIntentSender(sessionId: Int, appLabel: String) =
        PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(ACTION_INSTALL_RESULT)
                .setPackage(context.packageName)
                .putExtra(EXTRA_APP_LABEL, appLabel),
            pendingIntentFlags()
        ).intentSender

    /**
     * FLAG_MUTABLE is mandatory here: the system writes the session status into
     * this intent before delivering it, which an immutable PendingIntent forbids.
     */
    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
}
