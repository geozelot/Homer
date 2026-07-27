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
 * This is a *network* interceptor so it can read the TLS handshake. It verifies after the
 * handshake rather than during it (unlike OkHttp's [CertificatePinner], which would require
 * rebuilding the singleton client when the pin changes) — the mismatched response is closed and
 * never handed to the caller, which is enough to detect a swapped certificate for a personal app.
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
        val response = chain.proceed(chain.request())
        if (!enabled) return response

        val leaf = chain.connection()?.handshake()?.peerCertificates?.firstOrNull() ?: return response
        val current = CertificatePinner.pin(leaf)
        val stored = pin
        when {
            stored == null -> {
                // Trust on first use: remember this certificate as the pin.
                Log.i(TAG, "pinning server certificate (first use)")
                pin = current
                scope.launch { librarySettings.setPinnedServerCert(current) }
            }
            stored != current -> {
                response.close()
                throw SSLPeerUnverifiedException(
                    "Server certificate does not match the pinned certificate",
                )
            }
        }
        return response
    }

    private companion object {
        const val TAG = "HomerNet"
    }
}
