package com.geozelot.homer.data.library

import com.geozelot.homer.data.auth.NextcloudCredentials
import com.geozelot.homer.data.auth.ShareLink
import com.geozelot.homer.data.auth.WebDavKind
import com.geozelot.homer.data.webdav.WebDavClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates a Nextcloud public share link and determines whether it's writable, producing a
 * [WebDavKind.SHARE] credential ready to use as the library backend.
 *
 * A share is reached at `‹baseUrl›/public.php/dav/files/‹token›/` with Basic auth (token +
 * optional share password). Read vs read-write only governs whether this device can update the
 * shared library cache (`‹root›/.homer/`) — progress is always private (account or device-local),
 * so a read-only share is fully usable.
 */
@Singleton
class ShareResolver @Inject constructor(
    private val webDavClient: WebDavClient,
) {
    sealed interface Result {
        /** The link works. [writable] gates publishing to the shared library cache. */
        data class Ok(val credentials: NextcloudCredentials, val writable: Boolean) : Result

        /** The share needs a password, or the one given is wrong. */
        data object PasswordRequired : Result

        /** No share at that link (bad token / not a share URL). */
        data object NotFound : Result

        /** Network/server error — couldn't reach the share. */
        data object Unreachable : Result
    }

    suspend fun resolve(link: ShareLink, password: String): Result {
        val credentials = NextcloudCredentials(
            serverUrl = link.baseUrl,
            loginName = link.token,
            appPassword = password,
            kind = WebDavKind.SHARE,
        )
        return when (webDavClient.statusOf("", credentials)) {
            207 -> Result.Ok(credentials, writable = probeWritable(credentials))
            401, 403 -> Result.PasswordRequired
            404, 405 -> Result.NotFound
            else -> Result.Unreachable // 0 (network) or an unexpected status
        }
    }

    /**
     * Write-probe: MKCOL the shared-cache dir. Success (created / already exists) = read-write;
     * a 403 (or any failure) = read-only. On a writable share this also pre-creates `.homer`,
     * which is exactly where the shared catalog + covers live.
     */
    private suspend fun probeWritable(credentials: NextcloudCredentials): Boolean =
        runCatching { webDavClient.mkcol(SHARED_CACHE_DIR, credentials); true }.getOrDefault(false)

    private companion object {
        const val SHARED_CACHE_DIR = ".homer"
    }
}
