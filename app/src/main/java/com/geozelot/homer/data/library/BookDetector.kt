package com.geozelot.homer.data.library

import android.util.Log
import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.webdav.DavResource
import java.security.MessageDigest
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

    /**
     * [folderImages] carries the image files of every crawled folder, keyed by path — including
     * folders that hold no audio themselves. That's how a cover sitting at the book level beside
     * part subfolders (`Book/cover.jpg` with the audio under `Book/CD1/`) is found: the book folder
     * isn't an [AudioFolder] at all, so its images reach us only through this map.
     */
    fun buildBooks(
        folders: List<AudioFolder>,
        folderImages: Map<String, List<DavResource>>,
        libraryRoot: String,
        now: Long,
    ): List<Detected> {
        val root = libraryRoot.trim('/')
        // Group audio folders by the book they belong to: a part folder folds into its
        // parent; everything else is its own book.
        Log.i(TAG, "buildBooks: ${folders.size} audio folders under root='$root'")
        val byBook = LinkedHashMap<String, MutableList<AudioFolder>>()
        for (folder in folders) {
            val name = folder.path.substringAfterLast('/')
            val part = isPartName(name)
            val bookPath = if (part) {
                folder.path.substringBeforeLast('/', missingDelimiterValue = folder.path)
            } else {
                folder.path
            }
            byBook.getOrPut(bookPath) { mutableListOf() }.add(folder)
        }
        Log.i(TAG, "buildBooks: grouped ${folders.size} folders into ${byBook.size} books")
        return byBook.map { (bookPath, members) ->
            buildBook(bookPath, members, folderImages[bookPath].orEmpty(), root, now)
        }
    }

    private fun buildBook(
        bookPath: String,
        members: List<AudioFolder>,
        bookFolderImages: List<DavResource>,
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
        // The book folder's own images rank first: a cover sitting next to the part folders is the
        // book's cover, whereas an image inside `CD2/` is more likely disc-specific art.
        val images = mutableListOf<DavResource>()
        images += bookFolderImages
        for (member in orderedMembers) {
            orderedAudio += member.audioFiles.sortedWith { a, b ->
                AudioFormats.naturalCompare(a.name, b.name)
            }
            images += member.imageFiles
        }

        // Ids and stored paths are LIBRARY-ROOT-RELATIVE (not files-root), so they're stable
        // across users who mount the same shared folder at different paths (the shared index). The
        // library-root prefix is re-attached when building fetch URLs (see WebDavClient.urlFor).
        fun strip(path: String) = path.removePrefix(root).trim('/')

        val relToRoot = strip(bookPath)
        val segments = relToRoot.split('/').filter { it.isNotEmpty() }
        val title = segments.lastOrNull() ?: bookPath.substringAfterLast('/')
        // Top-level folder under the library root is the author; any folder between
        // author and book is the series (e.g. "Harry Potter - Heptalogie").
        val author = if (segments.size >= 2) segments.first() else null
        val series = if (segments.size >= 3) segments[segments.size - 2] else null
        val cover = images.minByOrNull { coverRank(it.name) }?.path?.let(::strip)

        val fileEntities = orderedAudio.mapIndexed { index, resource ->
            AudioFileEntity(
                relativePath = strip(resource.path),
                bookId = relToRoot,
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
            id = relToRoot,
            contentHash = contentHash(fileEntities),
            title = title,
            author = author,
            series = series,
            seriesIndex = null,
            relativePath = relToRoot,
            coverFilePath = cover,
            localCoverPath = null,
            chapterTier = CHAPTER_TIER_UNDETERMINED,
            isMultiFile = fileEntities.size > 1,
            fileCount = fileEntities.size,
            totalDurationMs = null,
            addedAt = now,
            updatedAt = now,
        )
        return Detected(book, fileEntities)
    }

    /**
     * A stable, path-independent fingerprint of a book: SHA-1 over its audio files' names and
     * sizes, sorted by name so file ordering doesn't affect it. Uses neither the folder path
     * (changes on move/rename) nor mtime (changes on copy), so the same set of files hashes the
     * same wherever it lives — that's what lets a scan recognise a moved book. Two distinct books
     * with byte-identical file names *and* sizes would collide, which is negligible in practice.
     *
     * The name/size separator is NUL: the one byte a filename cannot contain, so no name can be
     * crafted to hash the same as a different name/size pair. Don't "tidy" it into a space —
     * every stored contentHash would change at once and move-relinking would stop recognising
     * books it had already fingerprinted. It is written as the `\u0000` escape deliberately: a
     * literal NUL byte in the source makes this file binary to git, grep and every other text
     * tool, which is precisely what it did until it was escaped.
     */
    private fun contentHash(files: List<AudioFileEntity>): String? {
        if (files.isEmpty()) return null
        val canonical = files
            .map { "${it.fileName}\u0000${it.sizeBytes}" }
            .sorted()
            .joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-1").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
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
        private const val TAG = "HomerScan"

        /** Chapter tier is resolved in P3 (embedded/sidecar/none); 0 = not yet determined. */
        const val CHAPTER_TIER_UNDETERMINED = 0

        // A part/disc/volume marker followed by a number, matched ANYWHERE in the
        // folder name (handles "CD4", "Eden CD 6", "… - Teil 2"). A word boundary
        // before the keyword avoids matching inside words, and the required trailing
        // digit keeps "… - CD Version" or "… Band 6" from being treated as parts.
        private val PART_REGEX = Regex(
            "\\b(teil|part|cd|dvd|disc|disk|vol|volume|folge)[\\s._#-]*\\d+",
            RegexOption.IGNORE_CASE,
        )

        // A folder named just "1", "02", "003" — commonly a part/disc number.
        private val PURE_NUMBER_REGEX = Regex("^\\d{1,3}$")
    }
}
