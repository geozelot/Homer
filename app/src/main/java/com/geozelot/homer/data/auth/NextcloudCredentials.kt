package com.geozelot.homer.data.auth

/** How Homer reaches a WebDAV library: a signed-in account, or a public share link. */
enum class WebDavKind {
    /** Login Flow v2 account: files root at `/remote.php/dav/files/<login>/`. */
    ACCOUNT,

    /** Public share link: files root at `/public.php/dav/files/<token>/`, [loginName] = the share
     *  token and [appPassword] = the share password (empty when the link has none). */
    SHARE,
}

/**
 * A resolved WebDAV library session. For an [WebDavKind.ACCOUNT] this is a Login Flow v2 result;
 * for a [WebDavKind.SHARE] the [loginName] holds the share token and [appPassword] the share
 * password (or empty). Basic auth (`loginName:appPassword`) authenticates both.
 *
 * [appPassword] is a scoped, revocable secret — never the user's real password — and is only ever
 * persisted in Keystore-backed encrypted storage.
 */
data class NextcloudCredentials(
    val serverUrl: String,
    val loginName: String,
    val appPassword: String,
    val kind: WebDavKind = WebDavKind.ACCOUNT,
)

/** Observable authentication state for the app. */
sealed interface AuthState {
    data object Unknown : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val credentials: NextcloudCredentials) : AuthState
}
