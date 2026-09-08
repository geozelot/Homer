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
 * talks to the user's own Nextcloud, so one capture covers all its traffic). On the first
 * connection after the user enables pinning, the server's certificate CHAIN is captured; every
 * later connection must present at least one certificate matching it, or the response is rejected.
 *
 * This is a *network* interceptor so it can read the established TLS handshake. It verifies the
 * pin BEFORE forwarding the request (unlike OkHttp's [CertificatePinner], which would require
 * rebuilding the singleton client when the pin changes), so on a certificate mismatch the request
 * — including its `Authorization: Basic` header — is never transmitted to the swapped peer.
 *
 * ## The chain, not the leaf
 *
 * Pinning the leaf alone is pinning to a clock. Let's Encrypt — which is what a self-hosted
 * Nextcloud almost always runs — issues a fresh key every sixty to ninety days, so the stored hash
 * stopped matching on renewal and every request failed from then on. Not degraded: refused, in
 * both directions, forever, and only in logcat.
 *
 * Accepting a match anywhere in the presented chain survives a renewal, because the issuing
 * intermediate does not change when the leaf does. What still blocks is a different issuer — a CA
 * rotating its intermediate, or somebody actually sitting in the middle — which is the case this
 * feature exists for, and it is now SHOWN rather than logged (see [LibrarySettings.pinningBlocked]).
 */
/** What the pin says about a connection. */
internal sealed interface PinVerdict {
    /** Nothing is pinned yet — take what is presented as the pin. */
    data object Capture : PinVerdict

    /** At least one presented certificate is one we pinned. */
    data object Verified : PinVerdict

    /** Nothing presented was pinned. Refuse, and say so. */
    data object Blocked : PinVerdict
}

/**
 * The whole rule, as a function, so the thing that used to expire on a timer can be tested.
 *
 * A match ANYWHERE in the presented chain verifies. That is the difference between pinning a server
 * and pinning a certificate: the leaf is replaced on every renewal, the issuing intermediate is
 * not, and a chain that still contains something we pinned is still the server we pinned.
 */
internal fun pinVerdict(stored: List<String>, offered: List<String>): PinVerdict = when {
    stored.isEmpty() -> PinVerdict.Capture
    // An empty chain cannot be judged. Unreachable over TLS — a handshake without certificates is
    // not a handshake — and treated as a refusal rather than a pass, because the alternative is a
    // pin that anything gets past by presenting nothing.
    offered.isEmpty() -> PinVerdict.Blocked
    offered.any { it in stored } -> PinVerdict.Verified
    else -> PinVerdict.Blocked
}

@Singleton
class CertPinningInterceptor @Inject constructor(
    private val librarySettings: LibrarySettings,
) : Interceptor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var enabled = false
    @Volatile private var pins: List<String> = emptyList()

    /**
     * Whether a block is already recorded, so a refused connection writes once rather than on
     * every retry. Cleared by a connection that verifies, which is how accepting a new certificate
     * puts the warning away without anything else having to notice.
     */
    @Volatile private var blocked = false

    init {
        scope.launch { librarySettings.certPinningEnabled.collect { enabled = it } }
        scope.launch { librarySettings.pinnedServerCerts.collect { pins = it } }
        scope.launch { librarySettings.pinningBlocked.collect { blocked = it != null } }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enabled) return chain.proceed(chain.request())

        // The connection/handshake is already established for a network interceptor, so we can
        // check the pin here — before proceed() sends the request (and its credentials).
        val presented = chain.connection()?.handshake()?.peerCertificates.orEmpty()
        // No handshake to read — a cleartext connection, or a call that never reached one. There is
        // nothing to verify and nothing to capture; the request is not about this interceptor.
        if (presented.isEmpty()) return chain.proceed(chain.request())

        val offered = presented.map { CertificatePinner.pin(it) }
        when (pinVerdict(pins, offered)) {
            PinVerdict.Capture -> {
                // Trust on first use: remember the whole chain.
                Log.i(TAG, "pinning server certificate chain (first use, ${offered.size} certs)")
                pins = offered
                scope.launch { librarySettings.setPinnedServerCerts(offered) }
            }
            PinVerdict.Blocked -> {
                val host = chain.request().url.host
                // Recorded once, not on every retry — and the offered chain travels with it, so
                // accepting it later is a write rather than another handshake.
                if (!blocked) {
                    blocked = true
                    Log.w(TAG, "certificate for '$host' matches no pinned certificate")
                    scope.launch { librarySettings.setPinningBlocked(host, offered) }
                }
                throw SSLPeerUnverifiedException(
                    "Server certificate does not match the pinned certificate",
                )
            }
            // Verified. If something was being refused before this, it no longer is — which is what
            // puts the warning away after the user accepts a renewed certificate.
            PinVerdict.Verified -> if (blocked) {
                blocked = false
                scope.launch { librarySettings.setPinningBlocked(null) }
            }
        }
        return chain.proceed(chain.request())
    }

    private companion object {
        const val TAG = "HomerNet"
    }
}
