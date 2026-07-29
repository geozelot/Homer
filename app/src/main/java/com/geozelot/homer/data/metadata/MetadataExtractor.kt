package com.geozelot.homer.data.metadata

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.flac.PictureFrame
import androidx.media3.extractor.metadata.id3.ApicFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Extracts embedded metadata through Media3, driven by the authenticated OkHttp data
 * source — the same transport that plays audio. This avoids the platform
 * MediaMetadataRetriever, whose HTTP path fails against authenticated WebDAV.
 */
@OptIn(UnstableApi::class)
class MetadataExtractor @Inject constructor(
    private val dataSourceFactory: DataSource.Factory,
) {
    /** Embedded cover art (ID3 APIC / FLAC picture), or null if none / on failure. */
    suspend fun extractEmbeddedPicture(mediaUri: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val mediaItem = MediaItem.fromUri(mediaUri)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            // Bounded wait: a stalled authed stream must not tie up the (sequential) cover
            // enrichment pass indefinitely. TimeoutException is handled by the catch below.
            val trackGroups = MetadataRetriever.retrieveMetadata(mediaSourceFactory, mediaItem)
                .get(EXTRACT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            for (i in 0 until trackGroups.length) {
                val group = trackGroups.get(i)
                for (j in 0 until group.length) {
                    val metadata = group.getFormat(j).metadata ?: continue
                    for (k in 0 until metadata.length()) {
                        when (val entry = metadata.get(k)) {
                            is ApicFrame -> return@withContext entry.pictureData
                            is PictureFrame -> return@withContext entry.pictureData
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            // Log.d (stripped from release by R8): the URL carries the account + book path.
            Log.d(TAG, "cover extract failed for $mediaUri", e)
            null
        }
    }

    private companion object {
        const val TAG = "HomerMeta"
        const val EXTRACT_TIMEOUT_SECONDS = 30L
    }
}
