package com.geozelot.homer.data.metadata

import android.util.Log
import com.geozelot.homer.di.Bootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in cover lookup against Open Library (openlibrary.org), a FOSS book database with a public
 * cover API. Searches by title (+ author) and returns the best cover's JPEG bytes, or null.
 *
 * Uses the [Bootstrap] (unauthenticated) client on purpose: only the title and author are sent,
 * and never the Nextcloud credentials the [com.geozelot.homer.di.Authed] client would attach.
 * Gated behind [com.geozelot.homer.data.settings.LibrarySettings.onlineCoverLookup].
 */
@Singleton
class OnlineCoverClient @Inject constructor(
    @Bootstrap private val client: OkHttpClient,
    private val json: Json,
) {
    @Serializable
    private data class SearchResult(val docs: List<Doc> = emptyList())

    @Serializable
    private data class Doc(val cover_i: Long? = null)

    /** Best cover for [title]/[author], or null if nothing matched or the network failed. */
    suspend fun fetchCover(title: String, author: String?): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val searchUrl = "https://openlibrary.org/search.json".toHttpUrl().newBuilder()
                .addQueryParameter("title", title)
                .apply { if (!author.isNullOrBlank()) addQueryParameter("author", author) }
                .addQueryParameter("limit", "1")
                .addQueryParameter("fields", "cover_i")
                .build()
            val body = client.newCall(Request.Builder().url(searchUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string()
            } ?: return@withContext null

            val coverId = json.decodeFromString<SearchResult>(body).docs.firstOrNull()?.cover_i
                ?: return@withContext null

            // "-L" is the large edition; the cover service returns a 1x1 placeholder for unknown
            // ids, so request ?default=false to get a 404 instead and treat it as "no cover".
            val coverUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg?default=false"
            client.newCall(Request.Builder().url(coverUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
        }.onFailure { Log.w(TAG, "online cover lookup failed for \"$title\"", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "HomerMeta"
    }
}
