package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.webdav.DavResource
import javax.inject.Inject

/**
 * Turns a directory that directly contains audio files into a book plus its ordered
 * file list.
 *
 * v1 heuristics (deliberately conservative; richer series/ambiguity handling is P3):
 * - A folder holding ≥1 audio file is a book.
 * - Files are ordered by natural filename sort → chapter order for multi-file books.
 * - Title = folder name; author = the parent folder name (relative to the library
 *   root), if any.
 */
class BookDetector @Inject constructor() {

    data class Detected(val book: BookEntity, val files: List<AudioFileEntity>)

    fun detect(
        dirPath: String,
        audioFiles: List<DavResource>,
        imageFiles: List<DavResource>,
        libraryRoot: String,
        now: Long,
    ): Detected {
        val sorted = audioFiles.sortedWith { a, b -> AudioFormats.naturalCompare(a.name, b.name) }

        val relToRoot = dirPath.removePrefix(libraryRoot.trim('/')).trim('/')
        val segments = relToRoot.split('/').filter { it.isNotEmpty() }
        val title = segments.lastOrNull() ?: dirPath.substringAfterLast('/')
        val author = if (segments.size >= 2) segments[segments.size - 2] else null

        val cover = imageFiles.minByOrNull { coverRank(it.name) }?.path

        val fileEntities = sorted.mapIndexed { index, resource ->
            AudioFileEntity(
                relativePath = resource.path,
                bookId = dirPath,
                fileName = resource.name,
                sortIndex = index,
                sizeBytes = resource.contentLength ?: 0L,
                etag = resource.etag,
                lastModified = resource.lastModifiedMs,
                contentType = resource.contentType,
                durationMs = null,
            )
        }

        val book = BookEntity(
            id = dirPath,
            title = title,
            author = author,
            series = null,
            seriesIndex = null,
            relativePath = dirPath,
            coverFilePath = cover,
            chapterTier = CHAPTER_TIER_UNDETERMINED,
            isMultiFile = fileEntities.size > 1,
            fileCount = fileEntities.size,
            totalDurationMs = null,
            addedAt = now,
            updatedAt = now,
        )
        return Detected(book, fileEntities)
    }

    /** Lower rank = more likely to be the cover. */
    private fun coverRank(name: String): Int {
        val base = name.substringBeforeLast('.').lowercase()
        return when {
            base == "cover" -> 0
            base == "folder" -> 1
            base == "front" -> 2
            base.contains("cover") -> 3
            else -> 10
        }
    }

    companion object {
        /** Chapter tier is resolved in P3 (embedded/sidecar/none); 0 = not yet determined. */
        const val CHAPTER_TIER_UNDETERMINED = 0
    }
}
