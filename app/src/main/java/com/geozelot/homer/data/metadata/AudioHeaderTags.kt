package com.geozelot.homer.data.metadata

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a book's genre and embedded chapters from its ID3 tag, with small ranged requests.
 *
 * The companion to [AudioHeaderDuration], and the same argument. Once durations came from the
 * container header, the remaining cost of measuring a library was this one question per book:
 * [DurationExtractor.probeTags] opened a stream and took one to five seconds, usually to report
 * that the book has no genre. Both answers live in the tag at the front of the file.
 *
 * Only ID3 is handled here. MP4-family books fall through to the existing path, which already has
 * [Mp4ChapterParser] for their chapters — this is the format the library is actually made of, not
 * an attempt at every container.
 */
@OptIn(UnstableApi::class)
@Singleton
class AudioHeaderTags @Inject constructor(
    dataSourceFactory: DataSource.Factory,
) {
    private val reader = RangedReader(dataSourceFactory)

    /**
     * The tag's genre and chapters, or null when they could not be read from the header.
     *
     * Null means "ask something slower", never "this book has neither": an empty result is
     * returned only when the whole tag was walked and genuinely held nothing, because the caller
     * records that answer permanently.
     */
    suspend fun read(mediaUri: String): DurationExtractor.Probe? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(mediaUri)
            val head = reader.readAt(uri, 0, HEAD_BYTES) ?: return@withContext null
            val tags = Id3Tags.read(head) { position, length ->
                reader.readAt(uri, position, length.toLong())
            } ?: return@withContext null
            // Shaped as a Probe so the caller treats it exactly like the reader it replaces.
            // durationMs stays null: this path never speaks about duration.
            DurationExtractor.Probe(
                durationMs = null,
                genre = tags.genre,
                language = tags.language,
                chapters = tags.chapters,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Log.d (stripped from release by R8): the URL carries the account + book path.
            Log.d(TAG, "header tag read failed for $mediaUri", e)
            null
        }
    }

    private companion object {
        const val TAG = "HomerMeta"

        /**
         * Covers the tag header and the text frames that follow it, which is the whole answer for
         * a book without embedded chapters. Anything past this is reached by [Id3Tags] jumping.
         */
        const val HEAD_BYTES = 8L * 1024
    }
}
