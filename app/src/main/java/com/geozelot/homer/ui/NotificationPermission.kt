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
import androidx.core.app.NotificationManagerCompat
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
        if (!context.mayPostNotifications()) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Whether the *permission* is held — which below Android 13 it always is, there being no such
 * permission to hold.
 *
 * Deliberately not [notificationsEnabled]: this is the question "would the dialog do anything",
 * and the dialog cannot un-mute an app the user muted in system settings. Asking again there would
 * be a prompt that resolves instantly and changes nothing.
 */
private fun Context.mayPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Whether anything Homer posts will actually be seen.
 *
 * Not the permission: `minSdk` is 26, and on everything below Android 13 there is no permission to
 * read — but the user can still block Homer's notifications outright from system settings, and an
 * app-level block silences exactly as much as a refused permission does. Reading the permission
 * here would have reported "allowed" on every Android 8–12 device no matter what the user had
 * chosen, and the settings row would have hidden its own way back at the same time.
 *
 * `areNotificationsEnabled` answers the question on both sides of the version gate: the app-level
 * block from API 19, and the permission from 33 up.
 */
fun Context.notificationsEnabled(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled()

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
