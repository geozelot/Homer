package com.geozelot.homer.data.auth

/**
 * A resolved Nextcloud session obtained via Login Flow v2.
 *
 * [appPassword] is a scoped, revocable application password — never the user's real
 * password — and is only ever persisted in Keystore-backed encrypted storage.
 */
data class NextcloudCredentials(
    val serverUrl: String,
    val loginName: String,
    val appPassword: String,
)

/** Observable authentication state for the app. */
sealed interface AuthState {
    data object Unknown : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val credentials: NextcloudCredentials) : AuthState
}
