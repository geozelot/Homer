package com.geozelot.homer.data.library

import android.util.Log
import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.metadata.BookLanguage
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
        /**
         * The patterns a path is read through, most specific first.
         *
         * Defaults to the conventional layout, which is what every library that has never said
         * otherwise gets. A caller supplying its own puts them BEFORE these, so a pattern written
         * for a folder beats the convention for that folder.
         */
        templates: List<ScopedTemplate> = ScopedTemplate.DEFAULTS,
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
            buildBook(bookPath, members, folderImages[bookPath].orEmpty(), root, now, templates)
        }
    }

    private fun buildBook(
        bookPath: String,
        members: List<AudioFolder>,
        bookFolderImages: List<DavResource>,
        root: String,
        now: Long,
        templates: List<ScopedTemplate>,
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
        // Read through templates rather than by counting segments in place. The rules are the same
        // ones — author at the top, then the last three levels — but written down in
        // PathTemplate.DEFAULTS instead of compiled into this expression, which is what lets a
        // library whose folders do not follow the convention supply its own and be read correctly
        // rather than guessed at.
        val parsed = ScopedTemplate.parseFirst(relToRoot, templates).orEmpty()
        val title = parsed[TemplateField.TITLE]
            ?: segments.lastOrNull()
            ?: bookPath.substringAfterLast('/')
        val author = parsed[TemplateField.AUTHOR]
        val series = parsed[TemplateField.SERIES]
        val collection = parsed[TemplateField.COLLECTION]
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
            // Free: the crawl already holds every name this looks at, so a library nobody has
            // tagged still gets a language out of "Kapitel 03.mp3". A tag read later wins over it.
            language = BookLanguage.fromNames(
                folderName = relToRoot.substringAfterLast('/'),
                fileNames = fileEntities.map { it.fileName },
            ),
            title = title,
            author = author,
            series = series,
            seriesIndex = null,
            collection = collection,
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
            // What other software leaves behind: Windows Media Player writes AlbumArt.jpg and a
            // small variant beside it, and various rippers write poster/thumb. Ranked below the
            // three conventional names but well above an arbitrary image, because a folder whose
            // only picture is AlbumArt.jpg has a cover and Homer used to show none.
            base == "albumart" || base == "album" -> 4
            base == "poster" || base == "thumb" || base == "thumbnail" -> 5
            base.contains("cover") || base.startsWith("albumart") -> 6
            else -> 10
        }
    }

    companion object {
        private const val TAG = "HomerScan"

        /** Chapter tier is resolved in P3 (embedded/sidecar/none); 0 = not yet determined. */
        const val CHAPTER_TIER_UNDETERMINED = 0

        // A part/disc marker followed by a number, matched ANYWHERE in the folder name (handles
        // "CD4", "Eden CD 6", "… - Teil 2"). A word boundary before the keyword avoids matching
        // inside words, and the required trailing digit keeps "… - CD Version" from being treated
        // as a part.
        //
        // **`folge` is deliberately absent.** It is German for an EPISODE — one whole work in a
        // series, exactly like `Band`, which the commit that introduced this list excluded for that
        // very reason. Included here it folded every episode of a radio-drama series into a single
        // book: `Die Drei Fragezeichen/Folge 1`, `/Folge 2` … became one entry holding forty
        // episodes' files, with forty titles lost and their listening positions merged into one.
        //
        // The two mistakes are not symmetrical, which is what settles the ambiguous cases. Failing
        // to fold a real part leaves two visible entries somebody can see and fix; folding two real
        // books together destroys information silently. So only markers that unambiguously name a
        // subdivision of one work belong here, and `teil`/`part`/`vol`/`volume` stay only because a
        // book split into "Teil 1"/"Teil 2" is at least as common as a series numbered that way.
        private val PART_REGEX = Regex(
            "\\b(teil|part|cd|dvd|disc|disk|vol|volume)[\\s._#-]*\\d+",
            RegexOption.IGNORE_CASE,
        )

        // A folder named just "1", "02", "003" — commonly a part/disc number.
        private val PURE_NUMBER_REGEX = Regex("^\\d{1,3}$")
    }
}
