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

/** A downloaded text resource together with its ETag (for optimistic-concurrency writes). */
data class DavFile(val content: String, val etag: String?)

/**
 * Outcome of a *conditional* read. [NotModified] is the point of the exercise: it means the caller
 * already holds the current content, so the server sent headers only and no body was transferred —
 * the difference between a few hundred bytes and re-downloading a whole manifest/catalog.
 */
sealed interface DavRead {
    data class Body(val content: String, val etag: String?) : DavRead

    /** The resource is unchanged since the supplied ETag (HTTP 304). No bytes transferred. */
    data object NotModified : DavRead

    /** The resource does not exist (HTTP 404). */
    data object Absent : DavRead
}

/** Thrown when a WebDAV call is attempted with no configured account. */
class NotAuthenticatedException : IllegalStateException("No Nextcloud account configured")

/** Thrown by a conditional PUT (If-Match) when the server's ETag no longer matches. */
class PreconditionFailedException : java.io.IOException("ETag precondition failed")
