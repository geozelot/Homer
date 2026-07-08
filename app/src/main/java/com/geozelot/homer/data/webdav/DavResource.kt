package com.geozelot.homer.data.webdav

/**
 * One entry from a PROPFIND multistatus response. [path] is normalized relative to the
 * user's WebDAV files root (no leading or trailing slash), e.g. `Audiobooks/Book/01.mp3`.
 */
data class DavResource(
    val path: String,
    val isCollection: Boolean,
    val contentLength: Long?,
    val lastModifiedMs: Long?,
    val contentType: String?,
    val etag: String?,
) {
    val name: String get() = path.substringAfterLast('/')
}

/** Thrown when a WebDAV call is attempted with no configured account. */
class NotAuthenticatedException : IllegalStateException("No Nextcloud account configured")
