package com.geozelot.homer.data.auth

import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Adds HTTP Basic auth (login name + scoped app password) to outbound requests when
 * an account is configured. Applied to the authenticated OkHttp client used for all
 * WebDAV traffic. Requests already carrying an Authorization header are left untouched.
 *
 * The header is only ever attached to the user's own Nextcloud host: even though today every
 * request on this client targets that server, host-scoping ensures a future code path that put a
 * third-party URL on the authenticated client could never leak the app password off-server.
 */
class AuthInterceptor @Inject constructor(
    private val credentialStore: CredentialStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val credentials = credentialStore.credentials.value
        val serverHost = credentials?.serverUrl?.toHttpUrlOrNull()?.host
        if (credentials == null || serverHost == null ||
            request.url.host != serverHost ||
            request.header("Authorization") != null
        ) {
            return chain.proceed(request)
        }
        val authed = request.newBuilder()
            .header("Authorization", Credentials.basic(credentials.loginName, credentials.appPassword))
            .header("User-Agent", LoginFlowClient.USER_AGENT)
            .build()
        return chain.proceed(authed)
    }
}
