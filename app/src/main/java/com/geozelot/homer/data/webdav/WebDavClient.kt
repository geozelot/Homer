package com.geozelot.homer.data.webdav

import android.util.Xml
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.auth.NextcloudCredentials
import com.geozelot.homer.di.Authed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * Minimal Nextcloud WebDAV client: PROPFIND (directory listing) and ranged GET URL
 * construction. Uses the authenticated OkHttp client so Basic auth is injected.
 *
 * Paths passed in and returned are relative to the user's files root
 * (`/remote.php/dav/files/<user>/`). Streaming/seek during playback is handled later
 * by Media3's OkHttpDataSource, which honors HTTP Range on these same URLs.
 */
class WebDavClient @Inject constructor(
    @Authed private val client: OkHttpClient,
    private val credentialStore: CredentialStore,
) {
    /** Lists the immediate children of [relativePath] (plus the collection itself). */
    suspend fun propfind(relativePath: String, depth: Int = 1): List<DavResource> =
        withContext(Dispatchers.IO) {
            val credentials = credentialStore.awaitCredentials() ?: throw NotAuthenticatedException()
            val url = urlFor(credentials, relativePath)
            val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("Depth", depth.toString())
                .method("PROPFIND", body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401) throw IOException("Unauthorized (401) — app password may be revoked")
                if (response.code != 207) throw IOException("PROPFIND failed: HTTP ${response.code}")
                val stream = response.body?.byteStream() ?: throw IOException("Empty PROPFIND response")
                parseMultistatus(stream, credentials)
            }
        }

    /** Downloads a small text file (e.g. the `.homer` manifest). Null if it doesn't exist. */
    suspend fun getText(relativePath: String): DavFile? = withContext(Dispatchers.IO) {
        val credentials = credentialStore.awaitCredentials() ?: throw NotAuthenticatedException()
        val request = Request.Builder()
            .url(urlFor(credentials, relativePath))
            // Force an uncompressed response: gzip proxies (Apache mod_deflate) mangle the
            // ETag (append "-gzip" / mark it weak), which then fails every If-Match write.
            .header("Accept-Encoding", "identity")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                response.isSuccessful -> DavFile(response.body?.string().orEmpty(), response.header("ETag"))
                else -> throw IOException("GET failed: HTTP ${response.code}")
            }
        }
    }

    /**
     * Uploads [content] to [relativePath]. When [ifMatch] is set, the write only succeeds if
     * the server's ETag still matches (optimistic concurrency) — otherwise [PreconditionFailedException].
     * Returns the new ETag if the server reported one.
     */
    suspend fun putText(relativePath: String, content: String, ifMatch: String? = null): String? =
        withContext(Dispatchers.IO) {
            val credentials = credentialStore.awaitCredentials() ?: throw NotAuthenticatedException()
            val body = content.toRequestBody("application/json; charset=utf-8".toMediaType())
            val builder = Request.Builder().url(urlFor(credentials, relativePath)).put(body)
            ifMatch?.let { builder.header("If-Match", it) }
            client.newCall(builder.build()).execute().use { response ->
                when {
                    response.code == 412 -> throw PreconditionFailedException()
                    response.isSuccessful -> response.header("ETag")
                    else -> throw IOException("PUT failed: HTTP ${response.code}")
                }
            }
        }

    /**
     * Best-effort Nextcloud owner login of [relativePath] via the `oc:owner-id` DAV property
     * (Depth 0). Null if the server doesn't expose it or on any error — callers fall back to a
     * claim-based owner. Nextcloud-specific; other backends simply return null.
     */
    suspend fun fetchOwnerId(relativePath: String): String? = withContext(Dispatchers.IO) {
        val credentials = credentialStore.awaitCredentials() ?: return@withContext null
        try {
            val body = OWNER_PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(urlFor(credentials, relativePath))
                .header("Depth", "0")
                .method("PROPFIND", body)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 207) {
                    android.util.Log.w(TAG, "owner probe '$relativePath': HTTP ${response.code}")
                    return@use null
                }
                val text = response.body?.string().orEmpty()
                // Match any namespace prefix: <oc:owner-id>login</oc:owner-id>.
                val owner = Regex("owner-id[^>]*>([^<]+)<").find(text)
                    ?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
                if (owner != null) {
                    android.util.Log.i(TAG, "owner-id for '$relativePath' = $owner")
                } else {
                    // Nextcloud often omits owner-id on a non-shared (own) folder — expected.
                    android.util.Log.i(TAG, "owner-id absent for '$relativePath'; body: ${text.take(400)}")
                }
                owner
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "owner probe failed", e)
            null
        }
    }

    /** Downloads a binary file (e.g. a cached cover). Null if it doesn't exist. */
    suspend fun getBytes(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val credentials = credentialStore.awaitCredentials() ?: throw NotAuthenticatedException()
        val request = Request.Builder()
            .url(urlFor(credentials, relativePath))
            .header("Accept-Encoding", "identity")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                response.isSuccessful -> response.body?.bytes()
                else -> throw IOException("GET failed: HTTP ${response.code}")
            }
        }
    }

    /** Uploads binary [bytes] to [relativePath] (overwrites). */
    suspend fun putBytes(
        relativePath: String,
        bytes: ByteArray,
        contentType: String = "application/octet-stream",
    ): Unit = withContext(Dispatchers.IO) {
        val credentials = credentialStore.awaitCredentials() ?: throw NotAuthenticatedException()
        val body = bytes.toRequestBody(contentType.toMediaType())
        val request = Request.Builder().url(urlFor(credentials, relativePath)).put(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("PUT failed: HTTP ${response.code}")
        }
    }

    /** Creates a collection (directory); a no-op if it already exists. */
    suspend fun mkcol(relativePath: String): Unit = withContext(Dispatchers.IO) {
        val credentials = credentialStore.awaitCredentials() ?: throw NotAuthenticatedException()
        val request = Request.Builder().url(urlFor(credentials, relativePath)).method("MKCOL", null).build()
        client.newCall(request).execute().use { response ->
            // 201 = created, 405 = already exists; both are fine.
            if (response.code != 201 && response.code != 405) {
                throw IOException("MKCOL failed: HTTP ${response.code}")
            }
        }
    }

    /** Absolute URL for [relativePath], correctly percent-encoded per segment. */
    fun urlFor(credentials: NextcloudCredentials, relativePath: String): HttpUrl {
        val builder = credentials.serverUrl.toHttpUrl().newBuilder()
            .addPathSegments("remote.php/dav/files")
            .addPathSegment(credentials.loginName)
        relativePath.split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    /**
     * Builds a file URL from a **library-root-relative** path by prepending [libraryRoot]
     * (the current account's mount path of the library). Empty segments are dropped, so a
     * blank root or stray slashes are harmless.
     */
    fun urlFor(credentials: NextcloudCredentials, libraryRoot: String, relativePath: String): HttpUrl =
        urlFor(credentials, "$libraryRoot/$relativePath")

    private fun parseMultistatus(
        stream: java.io.InputStream,
        credentials: NextcloudCredentials,
    ): List<DavResource> {
        val results = mutableListOf<DavResource>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(stream, null)

        var href: String? = null
        var isCollection = false
        var length: Long? = null
        var lastModified: Long? = null
        var contentType: String? = null
        var etag: String? = null

        // Per-propstat scratch, committed only when its status is 2xx.
        var propStatus = 0
        var tCollection = false
        var tLength: Long? = null
        var tLastModified: Long? = null
        var tContentType: String? = null
        var tEtag: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "response" -> {
                        href = null; isCollection = false
                        length = null; lastModified = null; contentType = null; etag = null
                    }
                    "propstat" -> {
                        propStatus = 0; tCollection = false
                        tLength = null; tLastModified = null; tContentType = null; tEtag = null
                    }
                    "collection" -> tCollection = true
                    "href" -> href = parser.nextText().trim()
                    "getcontentlength" -> tLength = parser.nextText().trim().toLongOrNull()
                    "getlastmodified" -> tLastModified = parseHttpDate(parser.nextText().trim())
                    "getcontenttype" -> tContentType = parser.nextText().trim().ifEmpty { null }
                    "getetag" -> tEtag = parser.nextText().trim().trim('"').ifEmpty { null }
                    "status" -> propStatus = parseStatusCode(parser.nextText())
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "propstat" -> if (propStatus in 200..299) {
                        if (tCollection) isCollection = true
                        tLength?.let { length = it }
                        tLastModified?.let { lastModified = it }
                        tContentType?.let { contentType = it }
                        tEtag?.let { etag = it }
                    }
                    "response" -> {
                        val relative = href?.let { relativePathFromHref(it, credentials) }
                        if (relative != null) {
                            results += DavResource(
                                path = relative,
                                isCollection = isCollection,
                                contentLength = length,
                                lastModifiedMs = lastModified,
                                contentType = contentType,
                                etag = etag,
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }
        return results
    }

    companion object {
        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?>""" +
                """<d:propfind xmlns:d="DAV:"><d:prop>""" +
                """<d:resourcetype/><d:getcontentlength/><d:getlastmodified/>""" +
                """<d:getcontenttype/><d:getetag/>""" +
                """</d:prop></d:propfind>"""

        private const val OWNER_PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?>""" +
                """<d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">""" +
                """<d:prop><oc:owner-id/></d:prop></d:propfind>"""

        private const val TAG = "HomerDav"
        private const val FILES_MARKER = "/remote.php/dav/files/"

        /**
         * Converts a (percent-encoded, server-absolute) href into a path relative to the
         * files root. Returns `null` for the marker not being present, or empty string
         * for the files root itself.
         */
        fun relativePathFromHref(href: String, credentials: NextcloudCredentials): String? {
            val decoded = runCatching { URI(href).path }.getOrNull() ?: href
            val idx = decoded.indexOf(FILES_MARKER)
            if (idx < 0) return null
            // Skip the marker and the username segment.
            val afterFiles = decoded.substring(idx + FILES_MARKER.length)
            val rel = afterFiles.substringAfter('/', missingDelimiterValue = "")
            return rel.trim('/')
        }

        private fun parseStatusCode(status: String): Int =
            status.trim().split(' ').getOrNull(1)?.toIntOrNull() ?: 0

        private fun parseHttpDate(value: String): Long? = runCatching {
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            format.timeZone = TimeZone.getTimeZone("GMT")
            format.parse(value)?.time
        }.getOrNull()
    }
}
