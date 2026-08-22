package com.geozelot.homer.data.library

import android.util.Log
import androidx.room.withTransaction
import com.geozelot.homer.data.db.HomerDatabase
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.BookmarkMetaDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.CrawlDirDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.CrawlDirEntity
import com.geozelot.homer.data.webdav.DavResource
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Books to remove after a scan: everything indexed that the scan didn't account for. The prune has
 * to be expressed this way round — a single `DELETE … WHERE id NOT IN (:keepIds)` binds one SQL
 * host parameter per kept id and blows SQLite's 999-parameter cap on any real library, aborting the
 * prune entirely. The delete list is then chunked instead.
 */
internal fun idsToPrune(allIds: Collection<String>, keepIds: Set<String>): List<String> =
    allIds.filterNot { it in keepIds }

/**
 * Rewrites a saved chapter path for a book that moved from [oldId] to [newId]. `currentMediaId` is
 * `‹bookId›/‹file›`, so re-pointing only the row's `bookId` leaves the path at the old folder and
 * the saved chapter can no longer be resolved. Unrelated paths are returned unchanged.
 */
internal fun relinkMediaId(mediaId: String, oldId: String, newId: String): String = when {
    mediaId == oldId -> newId
    mediaId.startsWith("$oldId/") -> newId + mediaId.removePrefix(oldId)
    else -> mediaId
}

/** Coarse progress/result of a library scan, surfaced to the UI. */
sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val directoriesVisited: Int, val booksFound: Int) : ScanState
    data class Done(val bookCount: Int) : ScanState
    data class Error(val message: String) : ScanState
}

/**
 * Crawls the WebDAV tree from the library root via depth-1 PROPFINDs, collecting every
 * folder that directly contains audio, then hands the set to [BookDetector] to group into
 * books (merging multi-part books) and persists the result to Room.
 *
 * With [scan]'s `incremental = true`, unchanged subtrees are skipped: Nextcloud propagates a
 * collection's ETag up the tree, so a directory whose stored ETag still matches has an
 * unchanged subtree and is left untouched (its books preserved via [BookDao.idsUnder]). A book
 * folder that *has* changed is always fully re-read (so all its parts are gathered). A full
 * (`incremental = false`) scan re-reads everything — the safe fallback if ETags can't be
 * trusted. Cancellable between directories.
 */
class LibraryScanner @Inject constructor(
    private val db: HomerDatabase,
    private val webDavClient: WebDavClient,
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
    private val crawlDirDao: CrawlDirDao,
    private val playbackStateDao: PlaybackStateDao,
    private val bookOverrideDao: BookOverrideDao,
    private val bookmarkDao: BookmarkDao,
    private val bookmarkMetaDao: BookmarkMetaDao,
    private val downloadDao: DownloadDao,
    private val detector: BookDetector,
) {
    data class Result(val bookCount: Int)

    private data class Frame(val path: String, val forced: Boolean)

    suspend fun scan(
        libraryRoot: String,
        incremental: Boolean,
        now: Long,
        onProgress: (directoriesVisited: Int, audioFoldersFound: Int) -> Unit,
    ): Result {
        val root = libraryRoot.trim('/')
        val storedEtags = if (incremental) {
            crawlDirDao.getAll().associate { it.path to it.etag }
        } else {
            emptyMap()
        }
        val audioFolders = mutableListOf<BookDetector.AudioFolder>()
        /** Every visited folder's image files, so a book-level cover beside part folders is found. */
        val folderImages = HashMap<String, List<DavResource>>()
        val skippedRoots = mutableListOf<String>()
        var directoriesVisited = 0

        val stack = ArrayDeque<Frame>()
        stack.addLast(Frame(root, forced = false))

        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val frame = stack.removeLast()
            val dir = frame.path.trim('/')

            val entries = webDavClient.propfind(dir, depth = 1)
            directoriesVisited++

            val selfEtag = entries.firstOrNull { it.path == dir }?.etag
            val children = entries.filter { it.path != dir }
            val audioFiles = children.filter { !it.isCollection && AudioFormats.isAudio(it.name) }
            val imageFiles = children.filter { !it.isCollection && AudioFormats.isImage(it.name) }
            val childDirs = children.filter { it.isCollection }
            val isBookFolder = audioFiles.isNotEmpty()

            if (isBookFolder) {
                audioFolders += BookDetector.AudioFolder(dir, audioFiles, imageFiles)
            }
            // Remember images for EVERY folder, not just audio-bearing ones. A very common layout
            // puts the cover at the book level with the audio in part subfolders
            // (`Book/cover.jpg` + `Book/CD1/*.mp3`); that book folder holds no audio, so its cover
            // used to be discarded and the book showed a placeholder.
            if (imageFiles.isNotEmpty()) folderImages[dir] = imageFiles

            for (childDir in childDirs) {
                val childPath = childDir.path.trim('/')
                // Skip an unchanged subtree only from a plain container folder — never while
                // rebuilding a book (all its parts must be re-read). An unchanged collection
                // ETag means the whole subtree is unchanged (Nextcloud propagates ETags up).
                val stored = storedEtags[childPath]
                val unchanged = incremental && !frame.forced && !isBookFolder &&
                    stored != null && childDir.etag != null && stored == childDir.etag
                if (unchanged) {
                    skippedRoots += childPath
                } else {
                    stack.addLast(Frame(childPath, forced = isBookFolder || frame.forced))
                }
            }

            crawlDirDao.upsert(CrawlDirEntity(dir, selfEtag, now))
            onProgress(directoriesVisited, audioFolders.size)
        }

        val books = detector.buildBooks(audioFolders, folderImages, root, now)

        // Everything past this point mutates the index, and it belongs together: the upserts, the
        // moved-book re-links and the prune used to be independent writes, so a process death
        // between them could leave books re-linked but not pruned (duplicated) or pruned but not
        // re-linked (progress orphaned). The crawl above deliberately stays outside — it is network
        // work and can take minutes.
        db.withTransaction { applyScan(books, root, skippedRoots) }

        return Result(bookDao.count())
    }

    /** The database half of a scan: carry-over upserts, moved-book re-links, prune, orphan sweep. */
    private suspend fun applyScan(
        books: List<BookDetector.Detected>,
        root: String,
        skippedRoots: List<String>,
    ) {
        // Books to keep: the (re-)built ones plus every book under an unchanged, skipped subtree.
        // Anything else is pruned (a vanished book always sits under a visited parent). Skipped
        // roots are files-root paths; book ids are library-root-relative.
        val keepIds = buildSet {
            books.forEach { add(it.book.id) }
            for (skipped in skippedRoots) {
                addAll(bookDao.idsUnder(skipped.removePrefix(root).trim('/')))
            }
        }

        // Detect moved/renamed books by content hash: a freshly detected book at a previously
        // unknown path whose fingerprint matches an existing book that won't be kept = the same
        // book that moved. Map its new id back to the old one so we can re-link user data.
        val existingBooks = bookDao.getAll()
        val existingIds = existingBooks.mapTo(HashSet()) { it.id }
        val lostByHash = existingBooks
            .filter { it.contentHash != null && it.id !in keepIds }
            .associate { it.contentHash!! to it.id } // duplicate hashes: last wins (rare)
        val movedFrom = HashMap<String, String>() // newId -> oldId
        for (detected in books) {
            val hash = detected.book.contentHash ?: continue
            if (detected.book.id in existingIds) continue // path already existed → not a move target
            val oldId = lostByHash[hash] ?: continue
            movedFrom[detected.book.id] = oldId
        }
        if (movedFrom.isNotEmpty()) Log.i(TAG, "scan: re-linking ${movedFrom.size} moved book(s)")

        for (detected in books) {
            // Carry cached data across the rescan (upsert replaces the rows): the extracted cover,
            // genre, and per-file durations — otherwise every rescan would discard all measured
            // durations and re-probe the whole library on next open. For a moved book the source is
            // the old (pre-move) row, and durations match by file name since the path changed.
            val movedOldId = movedFrom[detected.book.id]
            val source = bookDao.findById(detected.book.id) ?: movedOldId?.let { bookDao.findById(it) }
            val existingFiles = audioFileDao.findForBook(detected.book.id).associateBy { it.relativePath }
            val movedFiles = movedOldId
                ?.let { old -> audioFileDao.findForBook(old).associateBy { it.fileName } }
                ?: emptyMap()
            val files = detected.files.map { file ->
                val atPath = existingFiles[file.relativePath]
                val moved = movedFiles[file.fileName]
                file.copy(
                    durationMs = file.durationMs ?: atPath?.durationMs ?: moved?.durationMs,
                    // Carried like the durations themselves: dropping it would make an ordinary
                    // rescan re-probe every file that has already proven unmeasurable.
                    durationAttempted = atPath?.durationAttempted ?: moved?.durationAttempted ?: false,
                )
            }
            // Recompute the total from the current file set so removed/added files stay correct.
            val total = if (files.isNotEmpty() && files.all { it.durationMs != null }) {
                files.sumOf { it.durationMs!! }
            } else {
                null
            }
            // A folder cover we can see but haven't cached yet earns a fresh attempt, even if an
            // earlier pass gave up on this book: caching it is one cheap GET, and it's what makes
            // the cover load instantly and work offline instead of being fetched every display.
            val uncachedFolderCover = detected.book.coverFilePath != null && source?.localCoverPath == null
            bookDao.upsert(
                listOf(
                    detected.book.copy(
                        localCoverPath = source?.localCoverPath,
                        customCoverPath = source?.customCoverPath,
                        coverAttempted = if (uncachedFolderCover) false else source?.coverAttempted ?: false,
                        metadataAttempted = source?.metadataAttempted ?: false,
                        genre = source?.genre,
                        chapterTier = source?.chapterTier ?: detected.book.chapterTier,
                        totalDurationMs = total,
                    ),
                ),
            )
            audioFileDao.deleteForBook(detected.book.id)
            audioFileDao.upsert(files)
        }

        // Re-link user data onto the moved books' new ids BEFORE pruning — the new book rows now
        // exist (so the bookmark FK holds) and the old rows aren't gone yet (bookmarks would
        // otherwise cascade away with them). Downloads are intentionally not re-linked: their bytes
        // on disk are keyed by the old relative path, so a moved book cleanly re-downloads.
        for ((newId, oldId) in movedFrom) {
            // The saved chapter path has to follow the id, or the resumed position can't be
            // resolved to a file any more (see [relinkMediaId]). Read it before the row moves.
            val savedMediaId = playbackStateDao.findByBookId(oldId)?.currentMediaId
            playbackStateDao.relink(oldId, newId)
            savedMediaId?.let { old ->
                val rewritten = relinkMediaId(old, oldId, newId)
                if (rewritten != old) playbackStateDao.updateCurrentMediaId(newId, rewritten)
            }
            bookOverrideDao.relink(oldId, newId)
            bookmarkMetaDao.relink(oldId, newId)
            bookmarkDao.relink(oldId, newId)
        }

        // Prune vanished books — but NEVER wipe a non-empty library on an empty keep-set. A
        // crawl can complete "successfully" yet come back empty (a transient 207 with no usable
        // entries from a reverse proxy, a momentarily-unreachable-but-not-erroring server, or a
        // mis-set root), and blindly running deleteAll() there deletes the whole library. Offline
        // is already safe (PROPFIND throws and aborts the scan before this point); this guards the
        // connected-but-empty case. A genuinely emptied library just keeps its stale rows until a
        // real crawl finds books again.
        val currentCount = bookDao.count()
        when {
            keepIds.isNotEmpty() -> {
                idsToPrune(bookDao.allIds(), keepIds)
                    .chunked(PRUNE_CHUNK)
                    .forEach { bookDao.deleteByIds(it) }
                // Tables keyed by bookId without a foreign key (kept that way so a rescan can't
                // cascade progress away) don't lose their rows with the book, and a row nothing can
                // match is a row nothing can read either — the user's progress simply vanishes from
                // view while still occupying the table. Sweep them here, and ONLY here: this branch
                // is the one where the prune was judged safe, so an empty or glitchy crawl can never
                // reach the sweep either. Bookmarks cascade via their FK.
                playbackStateDao.deleteOrphans()
                bookOverrideDao.deleteOrphans()
                bookmarkMetaDao.deleteOrphans()
                downloadDao.deleteOrphans()
            }
            currentCount == 0 -> Unit // already empty — nothing to prune
            else -> Log.w(
                TAG,
                "scan found no books but $currentCount are indexed; skipping prune to avoid " +
                    "wiping the library on an empty or failed crawl",
            )
        }
    }

    private companion object {
        /** Ids per DELETE — one SQL host parameter each, well under SQLite's 999 cap. */
        const val PRUNE_CHUNK = 500

        const val TAG = "HomerScan"
    }
}
