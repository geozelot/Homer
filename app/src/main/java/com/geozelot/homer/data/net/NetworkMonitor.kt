package com.geozelot.homer.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cheap "is there usable internet right now" check, so the `.homer` sync/catalog network calls
 * can fail fast when the device is offline (airplane mode, no signal) instead of burning the
 * OkHttp connect timeout on every attempt. It's an optimization, not a guarantee — a request can
 * still fail after this returns true (server down, captive portal), so callers stay resilient to
 * thrown errors regardless.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    /**
     * True when the active network can carry traffic. Deliberately does NOT require
     * NET_CAPABILITY_VALIDATED: a self-hosted Nextcloud is often reached over a LAN, a VPN, or a
     * network whose internet-validation probe hasn't (or won't) pass, and requiring VALIDATED
     * wrongly reported such setups as offline — blocking discovery/sync. This is only a fast-fail
     * hint; callers stay resilient to thrown errors, so a false positive just lets the real request
     * try and fail normally.
     */
    fun isOnline(): Boolean {
        val cm = connectivityManager ?: return true // can't tell → don't block, let the call try
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
