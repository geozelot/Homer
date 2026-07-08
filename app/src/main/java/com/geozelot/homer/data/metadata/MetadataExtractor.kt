package com.geozelot.homer.data.metadata

import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * On-device metadata extraction via the platform [MediaMetadataRetriever], reading
 * over HTTP with an auth header so it works against WebDAV. Documented as
 * potentially slow and prone to native crashes, so every call runs off the main
 * thread and swallows failures (returning null).
 */
class MetadataExtractor @Inject constructor() {

    /** Embedded cover art (ID3 APIC / MP4 covr), or null if none / on failure. */
    suspend fun extractEmbeddedPicture(url: String, headers: Map<String, String>): ByteArray? =
        withRetriever(url, headers) { it.embeddedPicture }

    /** Track duration in ms, or null if unknown / on failure. */
    suspend fun extractDurationMs(url: String, headers: Map<String, String>): Long? =
        withRetriever(url, headers) {
            it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }

    private suspend fun <T> withRetriever(
        url: String,
        headers: Map<String, String>,
        block: (MediaMetadataRetriever) -> T?,
    ): T? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(url, headers)
            block(retriever)
        } catch (e: Exception) {
            Log.w(TAG, "metadata extract failed for $url", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val TAG = "HomerMeta"
    }
}
