package com.geozelot.homer.data.auth

import java.net.URI

/**
 * A parsed Nextcloud public share link: the server origin plus the share token. The token becomes
 * the Basic-auth username against `‹baseUrl›/public.php/dav/files/‹token›/` (see [WebDavKind.SHARE]);
 * any share password is supplied separately.
 */
data class ShareLink(val baseUrl: String, val token: String) {
    companion object {
        /**
         * Parses a Nextcloud share URL such as `https://host/s/<token>` or
         * `https://host/index.php/s/<token>` (with or without scheme; forced to https, matching
         * Homer's TLS-only stance). Trailing path/query after the token is ignored. Returns null if
         * there's no `/s/<token>` segment.
         */
        fun parse(raw: String): ShareLink? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
            val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
            val sIdx = segments.indexOf("s")
            if (sIdx < 0 || sIdx + 1 > segments.lastIndex) return null
            val token = segments[sIdx + 1].takeIf { it.isNotBlank() } ?: return null
            val port = if (uri.port != -1) ":${uri.port}" else ""
            return ShareLink(baseUrl = "https://$host$port", token = token)
        }
    }
}
