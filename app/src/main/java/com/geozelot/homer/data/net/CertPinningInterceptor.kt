package com.geozelot.homer.data.net

import android.util.Log
import com.geozelot.homer.data.settings.LibrarySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Opt-in trust-on-first-use certificate pinning for the authenticated client (which only ever
 * talks to the user's own Nextcloud, so a single pin covers all its traffic). On the first
 * connection after the user enables pinning, the server's leaf certificate is captured; every
 * later connection must present the same certificate or the response is rejected.
 *
 * This is a *network* interceptor so it can read the established TLS handshake. It verifies the
 * pin BEFORE forwarding the request (unlike OkHttp's [CertificatePinner], which would require
 * rebuilding the singleton client when the pin changes), so on a certificate mismatch the request
 * — including its `Authorization: Basic` header — is never transmitted to the swapped peer.
 */
@Singleton
class CertPinningInterceptor @Inject constructor(
    private val librarySettings: LibrarySettings,
) : Interceptor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var enabled = false
    @Volatile private var pin: String? = null

    init {
        scope.launch { librarySettings.certPinningEnabled.collect { enabled = it } }
        scope.launch { librarySettings.pinnedServerCert.collect { pin = it } }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enabled) return chain.proceed(chain.request())

        // The connection/handshake is already established for a network interceptor, so we can
        // check the pin here — before proceed() sends the request (and its credentials).
        val leaf = chain.connection()?.handshake()?.peerCertificates?.firstOrNull()
        if (leaf != null) {
            val current = CertificatePinner.pin(leaf)
            val stored = pin
            when {
                stored == null -> {
                    // Trust on first use: remember this certificate as the pin.
                    Log.i(TAG, "pinning server certificate (first use)")
                    pin = current
                    scope.launch { librarySettings.setPinnedServerCert(current) }
                }
                stored != current -> throw SSLPeerUnverifiedException(
                    "Server certificate does not match the pinned certificate",
                )
            }
        }
        return chain.proceed(chain.request())
    }

    private companion object {
        const val TAG = "HomerNet"
    }
}
