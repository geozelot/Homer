package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.webdav.DavResource
import javax.inject.Inject

/**
 * Groups the audio-bearing folders discovered by a crawl into books.
 *
 * Library layout assumed: `Author / Book / [optional Part] / chapter files`, where
 * a Book may split its audio across "part" subfolders (`Part 1`, `CD2`, `Disc 3`,
 * `Vol 1`, or a bare number). Such part folders are merged back into their parent
 * book so a multi-part book counts once, with files ordered part-by-part then by
 * natural filename order. Non-part audio folders are standalone books.
 *
 * Title = book folder name; author = its parent folder name (relative to the library
 * root). Ambiguity-flagging and series handling remain future work.
 */
class BookDetector @Inject constructor() {

    /** A folder found to directly contain audio files, plus any sibling images. */
    data class AudioFolder(
        val path: String,
        val audioFiles: List<DavResource>,
        val imageFiles: List<DavResource>,
    )

    data class Detected(val book: BookEntity, val files: List<AudioFileEntity>)

    fun buildBooks(folders: List<AudioFolder>, libraryRoot: String, now: Long): List<Detected> {
        val root = libraryRoot.trim('/')
        // Group audio folders by the book they belong to: a part folder folds into its
        // parent; everything else is its own book.
        val byBook = LinkedHashMap<String, MutableList<AudioFolder>>()
        for (folder in folders) {
            val name = folder.path.substringAfterLast('/')
            val bookPath = if (isPartName(name)) {
                folder.path.substringBeforeLast('/', missingDelimiterValue = folder.path)
            } else {
                folder.path
            }
            byBook.getOrPut(bookPath) { mutableListOf() }.add(folder)
        }
        return byBook.map { (bookPath, members) -> buildBook(bookPath, members, root, now) }
    }

    private fun buildBook(
        bookPath: String,
        members: List<AudioFolder>,
        root: String,
        now: Long,
    ): Detected {
        // The book's own direct-audio folder (if any) leads, then part folders in
        // natural order.
        val orderedMembers = members.sortedWith(
            compareBy<AudioFolder> { if (it.path == bookPath) 0 else 1 }
                .thenComparator { a, b ->
                    AudioFormats.naturalCompare(
                        a.path.substringAfterLast('/'),
                        b.path.substringAfterLast('/'),
                    )
                },
        )

        val orderedAudio = mutableListOf<DavResource>()
        val images = mutableListOf<DavResource>()
        for (member in orderedMembers) {
            orderedAudio += member.audioFiles.sortedWith { a, b ->
                AudioFormats.naturalCompare(a.name, b.name)
            }
            images += member.imageFiles
        }

        val relToRoot = bookPath.removePrefix(root).trim('/')
        val segments = relToRoot.split('/').filter { it.isNotEmpty() }
        val title = segments.lastOrNull() ?: bookPath.substringAfterLast('/')
        val author = if (segments.size >= 2) segments[segments.size - 2] else null
        val cover = images.minByOrNull { coverRank(it.name) }?.path

        val fileEntities = orderedAudio.mapIndexed { index, resource ->
            AudioFileEntity(
                relativePath = resource.path,
                bookId = bookPath,
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
            id = bookPath,
            title = title,
            author = author,
            series = null,
            seriesIndex = null,
            relativePath = bookPath,
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

    /** True if a folder name denotes a part/disc/volume of a larger book. */
    private fun isPartName(name: String): Boolean {
        val trimmed = name.trim()
        return PART_REGEX.containsMatchIn(trimmed) || PURE_NUMBER_REGEX.matches(trimmed)
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

        // "Part 1", "Part_01", "CD2", "Disc 3", "vol.1", etc.
        private val PART_REGEX =
            Regex("^(part|pt|disc|disk|cd|vol|volume)[\\s._-]*\\d+", RegexOption.IGNORE_CASE)

        // A folder named just "1", "02", "003" — commonly a part/disc number.
        private val PURE_NUMBER_REGEX = Regex("^\\d{1,3}$")
    }
}
