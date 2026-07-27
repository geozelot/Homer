package com.geozelot.homer.data.auth

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for authentication. Wraps the [CredentialStore] as observable
 * [AuthState] and owns the login lifecycle (initiate → poll → persist, and logout).
 */
@Singleton
class AuthRepository @Inject constructor(
    private val credentialStore: CredentialStore,
    private val loginFlowClient: LoginFlowClient,
) {
    /** Current credentials, or `null` when logged out. */
    val credentials: StateFlow<NextcloudCredentials?> = credentialStore.credentials

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
        credentialStore.save(credentials)
        return credentials
    }

    fun logout() = credentialStore.clear()

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
