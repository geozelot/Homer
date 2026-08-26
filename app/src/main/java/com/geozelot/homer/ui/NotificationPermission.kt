package com.geozelot.homer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks for permission to post notifications, once, on the way into the library.
 *
 * It used to be asked by the player screen, which is far too late: a scan and a download both run as
 * foreground services with a progress notification, and both happen long before anybody opens a
 * book — on a first run the setup flow starts a scan immediately. Without the permission those
 * services still run, but silently, so a library crawl looks like nothing happening at all.
 *
 * Asked here rather than at launch because there is nothing to notify about until an account exists,
 * and a permission dialog in front of the login screen is a question about a feature the user has
 * not reached yet.
 *
 * Only ever launched when the permission is not already held. Android itself stops showing the
 * dialog after two refusals, so this cannot become a prompt on every start — and for the user who
 * did refuse, [notificationsEnabled] plus the row on the device settings page is the way back,
 * because a permission dialog is in practice a one-time offer.
 */
@Composable
fun NotificationPermissionRequest() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.notificationsEnabled()
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Whether Homer may post notifications.
 *
 * True below Android 13, where the permission does not exist and posting is always allowed. Note
 * this reads the *permission*, not whether the user has muted Homer's channels in system settings —
 * a muted channel is a deliberate choice and not something to nag about.
 */
fun Context.notificationsEnabled(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Opens Homer's own notification settings, for the user who refused and wants back in.
 *
 * The app-specific screen where it exists (Android 8+, which is every version Homer supports),
 * falling back to the app-info page — the same two-step shape the battery-optimisation row uses,
 * and for the same reason: some OEM builds refuse the direct intent.
 */
fun Context.openNotificationSettings() {
    val intents = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ),
    )
    for (intent in intents) {
        if (runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}
