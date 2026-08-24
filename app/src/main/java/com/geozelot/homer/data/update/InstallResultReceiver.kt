package com.geozelot.homer.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives the outcome of a [PackageInstaller] session started by [UpdateInstaller].
 *
 * The first callback is normally `STATUS_PENDING_USER_ACTION`, carrying the system's own
 * confirmation dialog for us to launch. Starting an activity from a receiver is restricted on
 * Android 12+, but the installer grants this particular intent the privilege to do it — and in
 * practice Homer is in the foreground anyway, because installing is something the user just tapped.
 *
 * A successful install replaces the app, so the process is about to be killed: do not expect any
 * work queued after `STATUS_SUCCESS` to run.
 */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {

    @Inject lateinit var updateManager: UpdateManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    Log.w(TAG, "pending user action with no intent to launch")
                    updateManager.onInstallFinished(UpdateFailure.INSTALL_FAILED)
                    return
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }.onFailure {
                    Log.w(TAG, "could not show the install confirmation", it)
                    updateManager.onInstallFinished(UpdateFailure.INSTALL_FAILED)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "update installed")
                updateManager.onInstallFinished(null)
            }

            // ABORTED is the user declining the dialog — not an error worth shouting about, but
            // the UI must leave the "installing" state either way.
            else -> {
                Log.w(TAG, "install failed: status=$status message=$message")
                updateManager.onInstallFinished(UpdateFailure.INSTALL_FAILED)
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.geozelot.homer.action.INSTALL_RESULT"
        private const val TAG = "HomerUpdate"
    }
}
