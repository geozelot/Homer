package com.geozelot.homer.data.sync.facet

import android.util.Log
import com.geozelot.homer.data.webdav.DavRead
import com.geozelot.homer.data.webdav.PreconditionFailedException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes one facet file, with its own ETag.
 *
 * Per file, not per library: a title correction rewrites kilobytes of `corrections.json` and leaves
 * `structure.json` alone. Under the single catalog every edit rewrote the whole thing — megabytes,
 * on every change — which was both the cost and the corruption risk.
 */
@Singleton
class FacetStore @Inject constructor(
    private val transport: FacetTransport,
    private val libraryRoot: LibraryRootSource,
    private val json: Json,
) {
    /** What the server had. [Damaged] is deliberately distinct from [Missing]: see [save]. */
    sealed interface Load<out T> {
        data class Present<T>(val value: T) : Load<T>

        /** The cached ETag still matches — the caller's copy is current. */
        data object Unchanged : Load<Nothing>

        data object Missing : Load<Nothing>

        /** Present but unparseable. Someone must decide whether to replace it. */
        data class Damaged(val message: String) : Load<Nothing>

        /** Offline, unauthenticated, or the request failed. Nothing was learned either way. */
        data class Unavailable(val message: String) : Load<Nothing>
    }

    // Per file, because the three facets change at completely different rates: a duration sweep
    // touches derived.json a hundred times while structure.json sits still for days.
    private val etags = ConcurrentHashMap<String, String>()

    /**
     * Fetches [file], conditionally on the ETag last seen for it.
     *
     * **The ETag is remembered only after a successful parse.** Caching it earlier is what made a
     * single damaged catalog permanent: every later read sent If-None-Match, got a 304, and
     * reported success for a file that had never been applied.
     */
    suspend fun <T> load(file: String, serializer: KSerializer<T>): Load<T> {
        val path = pathOf(file)
        val read = try {
            transport.read(path, etags[file])
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Load.Unavailable(e.message ?: e::class.simpleName.orEmpty())
        }

        return when (read) {
            is DavRead.NotModified -> Load.Unchanged
            is DavRead.Absent -> {
                // Gone from the server; a stale ETag would otherwise resurrect it on the next read.
                etags.remove(file)
                Load.Missing
            }
            is DavRead.Body -> {
                if (read.content.isBlank()) return Load.Missing
                try {
                    val value = json.decodeFromString(serializer, read.content)
                    read.etag?.let { etags[file] = it } ?: etags.remove(file)
                    Load.Present(value)
                } catch (e: Exception) {
                    Log.w(TAG, "$file is unreadable (${read.content.length} chars); will re-read", e)
                    etags.remove(file)
                    Load.Damaged(e.message ?: "unparseable")
                }
            }
        }
    }

    /**
     * Reads [file], hands what was there to [merge], and writes the result back if it changed.
     *
     * [merge] receives the remote state rather than a plain value so it can tell a file that is
     * absent from one that is damaged: replacing a damaged file is usually right, but replacing it
     * with an empty local view turns a broken index into an empty one, which is worse for the next
     * device along. Returning null declines the write.
     *
     * Concurrency is optimistic: the write carries If-Match, and a 412 means someone else got there
     * first, so the whole read-merge-write runs again against what is now there. The final attempt
     * drops the condition rather than losing the change outright.
     */
    suspend fun <T> save(
        file: String,
        serializer: KSerializer<T>,
        merge: (Load<T>) -> T?,
    ): SaveResult {
        val path = pathOf(file)
        for (attempt in 0 until MAX_ATTEMPTS) {
            val current = load(file, serializer)
            if (current is Load.Unavailable) return SaveResult.Unavailable(current.message)

            // Unchanged means our cached ETag matched, so what we already hold IS the remote — but
            // the merge needs the value, not the fact. Re-read unconditionally to get it.
            val state = if (current is Load.Unchanged) reloadUnconditionally(file, serializer) else current
            if (state is Load.Unavailable) return SaveResult.Unavailable(state.message)

            val merged = merge(state) ?: return SaveResult.Declined
            if (state is Load.Present && merged == state.value) return SaveResult.AlreadyCurrent

            // Only the last attempt gives up the guard; before that a 412 is worth another round.
            val ifMatch = etags[file].takeUnless { attempt == MAX_ATTEMPTS - 1 }
            try {
                ensureDir()
                val newEtag = transport.write(path, json.encodeToString(serializer, merged), ifMatch)
                newEtag?.let { etags[file] = it } ?: etags.remove(file)
                return SaveResult.Written
            } catch (e: PreconditionFailedException) {
                Log.i(TAG, "$file changed under us; retry ${attempt + 1}/$MAX_ATTEMPTS")
                etags.remove(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                return SaveResult.Unavailable(e.message ?: "write failed")
            }
        }
        return SaveResult.Contended
    }

    /** Drops every cached ETag, so the next read fetches in full. */
    fun forgetEtags() = etags.clear()

    private suspend fun <T> reloadUnconditionally(file: String, serializer: KSerializer<T>): Load<T> {
        etags.remove(file)
        return load(file, serializer)
    }

    private suspend fun ensureDir() {
        runCatching { transport.ensureDir(dirOf()) }
    }

    private suspend fun dirOf(): String {
        val root = libraryRoot.root().trim('/')
        return listOf(root, LibraryFacets.DIR).filter { it.isNotBlank() }.joinToString("/")
    }

    private suspend fun pathOf(file: String): String = "${dirOf()}/$file"

    sealed interface SaveResult {
        data object Written : SaveResult

        /** The merge produced exactly what is already there. */
        data object AlreadyCurrent : SaveResult

        /** [merge] returned null — nothing worth writing. */
        data object Declined : SaveResult

        /** Lost the race [MAX_ATTEMPTS] times; the change is still local and can be retried later. */
        data object Contended : SaveResult

        data class Unavailable(val message: String) : SaveResult
    }

    private companion object {
        const val TAG = "HomerSync"
        const val MAX_ATTEMPTS = 3
    }
}
