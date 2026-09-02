package com.tvshortcut.maker.data.apk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import com.tvshortcut.maker.R

/**
 * Receives the outcome of an install session started by [ShortcutInstaller].
 *
 * The important case is [PackageInstaller.STATUS_PENDING_USER_ACTION]: the
 * system is not refusing anything, it is handing back an intent that shows the
 * confirmation dialog. If we ignore it, the install silently never happens —
 * which is the single most common bug in code that uses PackageInstaller.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val label = intent.getStringExtra(ShortcutInstaller.EXTRA_APP_LABEL).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Show the system confirmation dialog.
                @Suppress("DEPRECATION")
                val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { confirmation?.let { context.startActivity(it) } }
            }

            PackageInstaller.STATUS_SUCCESS ->
                context.toast(context.getString(R.string.msg_shortcut_installed, label))

            PackageInstaller.STATUS_FAILURE_ABORTED ->
                Unit // The user cancelled — no need to nag.

            else -> {
                val reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                context.toast(
                    context.getString(
                        R.string.msg_shortcut_install_failed,
                        reason ?: status.toString()
                    )
                )
            }
        }
    }

    private fun Context.toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
