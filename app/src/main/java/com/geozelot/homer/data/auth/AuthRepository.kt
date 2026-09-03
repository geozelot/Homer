package com.geozelot.homer.data.auth

import com.geozelot.homer.data.library.ShareResolver
import com.geozelot.homer.data.settings.LibrarySettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for authentication. Wraps the [CredentialStore] as observable
 * [AuthState] and owns the login lifecycle (initiate → poll → persist) for both an account
 * (Login Flow v2) and a public share link.
 *
 * Signing out is [SignOut]'s, not this class's: it clears the library and its settings as well as
 * the credentials, and doing that here would put Room and DataStore behind the auth repository.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val credentialStore: CredentialStore,
    private val loginFlowClient: LoginFlowClient,
    private val shareResolver: ShareResolver,
    private val librarySettings: LibrarySettings,
) {
    /** Current library backend (account or share), or `null` when logged out. */
    val credentials: StateFlow<NextcloudCredentials?> = credentialStore.credentials

    /** Account used for private progress sync (null = device-local). */
    val syncAccount: StateFlow<NextcloudCredentials?> = credentialStore.syncAccount

    /** Stops syncing progress to a separately-added account (share libraries → back to device-local). */
    fun unlinkSyncAccount() = credentialStore.setSyncAccount(null)

    /** Begins a browser login flow; returns the handshake to drive the Custom Tab + polling. */
    suspend fun beginLogin(serverUrl: String): LoginV2Init = loginFlowClient.initiate(serverUrl)

    /** One poll tick. Persists and returns credentials on success; `null` while pending. */
    suspend fun pollOnce(init: LoginV2Init): NextcloudCredentials? {
        val result = loginFlowClient.poll(init) ?: return null
        val credentials = NextcloudCredentials(
            serverUrl = LoginFlowClient.normalizeServerUrl(result.server),
            loginName = result.loginName,
            appPassword = result.appPassword,
        )
        // Nothing has probed this account's folder yet, so it starts from the default rather than
        // from whatever a previous share left behind. Setup probes the folder it settles on and
        // records the real answer — a folder shared INTO an account can be read-only.
        librarySettings.setLibraryWritable(true)
        credentialStore.save(credentials)
        return credentials
    }

    /**
     * Resolves a public share link and, on success, adopts it as the library backend (the share
     * root becomes the library; writability gates the shared cache). Progress stays device-local
     * until the user separately adds a sync account. Returns the resolver result for UI feedback.
     */
    suspend fun useShare(rawUrl: String, password: String): ShareResolver.Result {
        val link = ShareLink.parse(rawUrl) ?: return ShareResolver.Result.NotFound
        val result = shareResolver.resolve(link, password)
        if (result is ShareResolver.Result.Ok) {
            librarySettings.setLibraryRoot("") // the share root IS the library
            librarySettings.setLibraryWritable(result.writable)
            librarySettings.setSharedCatalogEnabled(true) // consume a prebuilt catalog if present
            credentialStore.save(result.credentials) // flips AuthState to LoggedIn
        }
        return result
    }

    /** One poll tick for adding a SYNC account to an existing share library (Flow 3). */
    suspend fun pollSyncAccount(init: LoginV2Init): NextcloudCredentials? {
        val result = loginFlowClient.poll(init) ?: return null
        val account = NextcloudCredentials(
            serverUrl = LoginFlowClient.normalizeServerUrl(result.server),
            loginName = result.loginName,
            appPassword = result.appPassword,
        )
        credentialStore.setSyncAccount(account)
        return account
    }

    /**
     * Maps credentials to a coarse [AuthState] for navigation gating. Stays [AuthState.Unknown]
     * (spinner) until the credential store has finished its initial load, so a cold start never
     * flashes the login screen before the persisted account is read.
     */
    fun authState(scope: CoroutineScope): StateFlow<AuthState> =
        combine(credentials, credentialStore.loaded) { creds, loaded ->
            when {
                !loaded -> AuthState.Unknown
                creds == null -> AuthState.LoggedOut
                else -> AuthState.LoggedIn(creds)
            }
        }.stateIn(scope, SharingStarted.Eagerly, AuthState.Unknown)
}
