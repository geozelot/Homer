package com.geozelot.homer.data.update

/** Which releases the updater is willing to offer. */
enum class UpdateChannel(val pref: String) {
    /** Finished releases only — what a tag like `v1.1.0` produces. */
    STABLE("stable"),

    /** Also the betas (`v1.1.0-BETA18`), which CI marks as pre-releases. */
    BETA("beta"),
    ;

    companion object {
        fun fromPref(value: String?): UpdateChannel =
            entries.firstOrNull { it.pref == value } ?: STABLE
    }
}

/** A release on GitHub that carries an installable APK. */
data class UpdateRelease(
    val version: AppVersion,
    val tag: String,
    val prerelease: Boolean,
    /** The release body, as written on the tag. Null or blank when the release has no notes. */
    val notes: String?,
    val apkUrl: String,
    val apkSizeBytes: Long,
    /** The human page, for "view the release" rather than installing in-app. */
    val pageUrl: String,
)

/** Why an update attempt stopped. The UI turns each of these into its own sentence. */
enum class UpdateFailure {
    /** No connection, DNS failure, timeout — nothing was learned either way. */
    NETWORK,

    /** GitHub's unauthenticated API allows 60 requests an hour per address. */
    RATE_LIMITED,

    /** A newer release exists but has no APK attached — CI failed to upload, or it is a source tag. */
    NO_ASSET,

    /** The download did not complete, or completed at the wrong size. */
    DOWNLOAD,

    /**
     * The downloaded APK is signed by a different key than the installed app. Android would refuse
     * the install anyway; catching it here is the difference between a sentence the user can act on
     * and an opaque installer error. The ordinary cause is a locally-built debug install.
     */
    SIGNATURE_MISMATCH,

    /** Homer is not allowed to install apps, and the user has not granted it. */
    INSTALL_NOT_ALLOWED,

    /** The system installer refused or the user declined. */
    INSTALL_FAILED,
}

/** Where the updater is right now. One flow drives both the settings screen and the notification. */
sealed interface UpdateState {
    /** Nothing has been checked yet this run. */
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** Checked, and this build is current. [checkedAtMs] is wall-clock, for "checked 5 min ago". */
    data class UpToDate(val checkedAtMs: Long) : UpdateState

    data class Available(val release: UpdateRelease) : UpdateState

    /** [fraction] is null while the server has not told us how big the download is. */
    data class Downloading(val release: UpdateRelease, val fraction: Float?) : UpdateState

    /** The APK is on disk and its signature matches; waiting for the user to confirm the install. */
    data class ReadyToInstall(val release: UpdateRelease) : UpdateState

    /** Handed to the system installer; the app is about to be replaced. */
    data class Installing(val release: UpdateRelease) : UpdateState

    data class Failed(val reason: UpdateFailure) : UpdateState
}
