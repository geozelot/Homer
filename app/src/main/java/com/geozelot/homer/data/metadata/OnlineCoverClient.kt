package com.geozelot.homer.data.metadata

import android.util.Log
import com.geozelot.homer.di.Bootstrap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in cover lookup against Open Library (openlibrary.org), a FOSS book database with a public
 * cover API. Searches by title (+ author) and returns the best cover's JPEG bytes, or null.
 *
 * Uses the [Bootstrap] (unauthenticated) client on purpose: only the title and author are sent,
 * and never the Nextcloud credentials the [com.geozelot.homer.di.Authed] client would attach.
 * Gated behind [com.geozelot.homer.data.settings.LibrarySettings.onlineCoverLookup].
 *
 * ## Three answers, not two
 *
 * It used to return `ByteArray?`, with null meaning both "there is no cover for this book" and
 * "openlibrary.org could not be reached". The caller could only read that as the first, so an
 * unreachable server was recorded as a settled "no art available" — for every book in the pass.
 * They are now different answers, because only one of them is a fact about the book.
 *
 * ## And a much shorter fuse
 *
 * The shared client waits 30 seconds to connect, which is right for the user's own server and
 * absurd for an optional lookup against a third party: a library of 313 art-less books facing an
 * unreachable openlibrary.org spent two and a half hours in a foreground worker timing out, one
 * book at a time. This call gets [CALL_TIMEOUT_SECONDS] and no more.
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

    /** Its own client, so a slow third party cannot hold a cover pass open for half a minute. */
    private val shortFused: OkHttpClient by lazy {
        client.newBuilder().callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    }

    /** Best cover for [title]/[author]. */
    suspend fun fetchCover(title: String, author: String?): Result = withContext(Dispatchers.IO) {
        try {
            val searchUrl = "https://openlibrary.org/search.json".toHttpUrl().newBuilder()
                .addQueryParameter("title", title)
                .apply { if (!author.isNullOrBlank()) addQueryParameter("author", author) }
                .addQueryParameter("limit", "1")
                .addQueryParameter("fields", "cover_i")
                .build()
            val body = shortFused.newCall(Request.Builder().url(searchUrl).build()).execute().use { resp ->
                // A refusal from the server IS an answer — it was reached. Only never reaching it
                // leaves the question open.
                if (!resp.isSuccessful) return@withContext Result.NotFound
                resp.body?.string()
            } ?: return@withContext Result.NotFound

            val coverId = json.decodeFromString<SearchResult>(body).docs.firstOrNull()?.cover_i
                ?: return@withContext Result.NotFound

            // "-L" is the large edition; the cover service returns a 1x1 placeholder for unknown
            // ids, so request ?default=false to get a 404 instead and treat it as "no cover".
            val coverUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg?default=false"
            shortFused.newCall(Request.Builder().url(coverUrl).build()).execute().use { resp ->
                val bytes = if (resp.isSuccessful) resp.body?.bytes() else null
                if (bytes == null || bytes.isEmpty()) Result.NotFound else Result.Found(bytes)
            }
        } catch (e: CancellationException) {
            // Was `runCatching`, which catches Throwable — so pressing Stop mid-lookup was
            // swallowed and the pass carried on into the next book.
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "could not reach openlibrary.org for \"$title\": ${e.javaClass.simpleName}")
            Result.Unavailable
        }
    }

    /** What the lookup established — see the class doc for why "nothing" is two answers. */
    sealed interface Result {
        class Found(val bytes: ByteArray) : Result

        /** Openlibrary answered, and has no cover for this book. A fact about the book. */
        data object NotFound : Result

        /** Openlibrary could not be reached. A fact about the network, and about nothing else. */
        data object Unavailable : Result
    }

    private companion object {
        const val TAG = "HomerMeta"

        /**
         * The whole call, connect to last byte.
         *
         * Short on purpose. This is an optional lookup against somebody else's free service, run
         * once per art-less book; the shared client's 30-second connect timeout turned an
         * unreachable host into hours of a foreground worker doing nothing.
         */
        const val CALL_TIMEOUT_SECONDS = 8L
    }
}
