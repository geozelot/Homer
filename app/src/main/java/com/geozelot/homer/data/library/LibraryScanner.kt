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
import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.CrawlDirEntity
import com.geozelot.homer.data.download.DownloadStorage
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

/**
 * Books that moved or were renamed: a freshly detected book at a previously unknown path whose
 * content fingerprint matches an indexed book the scan did NOT account for is the same book in a
 * new place. Returns newId -> oldId, which is what lets progress, bookmarks and hand-typed
 * overrides follow it instead of being pruned away with the old row.
 */
internal fun detectMoves(
    detected: List<BookDetector.Detected>,
    existingBooks: List<BookEntity>,
    keepIds: Set<String>,
): Map<String, String> {
    val existingIds = existingBooks.mapTo(HashSet()) { it.id }
    val lostByHash = existingBooks
        .filter { it.contentHash != null && it.id !in keepIds }
        .associate { it.contentHash!! to it.id } // duplicate hashes: last wins (rare)
    val moved = HashMap<String, String>()
    for (book in detected) {
        val hash = book.book.contentHash ?: continue
        if (book.book.id in existingIds) continue // path already existed → not a move target
        moved[book.book.id] = lostByHash[hash] ?: continue
    }
    return moved
}

/** The rows a scan will write: book rows and their files, with everything device-local carried. */
internal data class PlannedWrites(val books: List<BookEntity>, val files: List<AudioFileEntity>)

/**
 * Merges a scan's findings with what the index already holds.
 *
 * The carry-over is guarded by SIZE, deliberately not by ETag. A re-encode that lands on exactly
 * the same byte count is not a thing, and an edit that preserves the size to the byte cannot have
 * changed the duration either — whereas a server that changes its ETag scheme (an upgrade, a
 * different storage backend) would invalidate every file at once and re-measure a whole library.
 *
 * This is the carry-over: a crawl only sees names, sizes and ETags, so everything *measured* —
 * per-file durations, the extracted or user-chosen cover, the probed genre, the chapter tier — is
 * known only to the existing row and has to be copied onto the replacement. Getting it wrong is
 * quiet and expensive: dropping a duration re-probes the file (a full stream) and meanwhile costs
 * the book its time-left, progress ring and auto-finish.
 *
 * Pure, and separate from [LibraryScanner], so those rules are testable without a database.
 */
internal fun planWrites(
    detected: List<BookDetector.Detected>,
    existingById: Map<String, BookEntity>,
    filesByBook: Map<String, List<AudioFileEntity>>,
    movedFrom: Map<String, String>,
): PlannedWrites {
    val books = ArrayList<BookEntity>(detected.size)
    val files = ArrayList<AudioFileEntity>()
    for (book in detected) {
        // For a moved book the carry-over source is the old (pre-move) row, and its durations match
        // by file NAME — the relative path changed with the folder.
        val movedOldId = movedFrom[book.book.id]
        val source = existingById[book.book.id] ?: movedOldId?.let { existingById[it] }
        val atPath = filesByBook[book.book.id].orEmpty().associateBy { it.relativePath }
        val byName = movedOldId?.let { old -> filesByBook[old].orEmpty().associateBy { it.fileName } }.orEmpty()

        val merged = book.files.map { file ->
            // Only from a file that is still the same bytes. A file replaced in place — re-encoded,
            // re-tagged, swapped for a better rip — keeps its path and its name, so without this
            // the old duration was carried on to new audio and the book's time-left was quietly
            // wrong for ever, with nothing to trigger a re-measure.
            val previous = atPath[file.relativePath]?.takeIf { it.sizeBytes == file.sizeBytes }
            val moved = byName[file.fileName]?.takeIf { it.sizeBytes == file.sizeBytes }
            file.copy(
                durationMs = file.durationMs ?: previous?.durationMs ?: moved?.durationMs,
                // Carried like the durations themselves: dropping it would make an ordinary rescan
                // re-probe every file that has already proven unmeasurable.
                durationAttempted = previous?.durationAttempted ?: moved?.durationAttempted ?: false,
            )
        }
        // Recomputed from the current file set, so removing or adding a file stays correct — and
        // left null unless EVERY file is measured, because a partial total reads as "finished" and
        // hides the book.
        val total = if (merged.isNotEmpty() && merged.all { it.durationMs != null }) {
            merged.sumOf { it.durationMs!! }
        } else {
            null
        }
        // A folder cover we can see but haven't cached yet earns a fresh attempt, even if an
        // earlier pass gave up on this book: caching it is one cheap GET, and it's what makes the
        // cover load instantly and work offline instead of being fetched on every display.
        val uncachedFolderCover = book.book.coverFilePath != null && source?.localCoverPath == null
        books += book.book.copy(
            localCoverPath = source?.localCoverPath,
            customCoverPath = source?.customCoverPath,
            coverAttempted = if (uncachedFolderCover) false else (source?.coverAttempted ?: false),
            metadataAttempted = source?.metadataAttempted ?: false,
            genre = source?.genre,
            chapterTier = source?.chapterTier ?: book.book.chapterTier,
            totalDurationMs = total,
        )
        files += merged
    }
    return PlannedWrites(books, files)
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
    private val downloadStorage: DownloadStorage,
    private val detector: BookDetector,
) {
    /**
     * [complete] means the crawl saw the WHOLE tree — no subtree was skipped on an unchanged ETag.
     *
     * It is not the same question as "was this a full crawl". A full crawl has no stored ETags to
     * skip on, so it is always complete; an *incremental* crawl is complete whenever it happened to
     * skip nothing, which is every first crawl of a library and any crawl where everything changed.
     * That distinction is the whole point: the marker this feeds is what authorises another device
     * to delete a book, and until one is stamped a book removed on the server stays on every shelf
     * for ever. Keying it on the incremental flag meant only "Rebuild the library" could ever
     * stamp it.
     */
    data class Result(val bookCount: Int, val complete: Boolean)

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
        // A full crawl saw the whole library, so anything unaccounted for is genuinely gone and
        // its leftovers can be swept. An incremental scan skips unchanged subtrees and can lose a
        // subtree to a transient server error, so it must never sweep — see applyScan.
        // NOTE the deliberate asymmetry with the completeness flag above. The crawl marker asks
        // "did this pass see everything", and an incremental pass that skipped nothing did. The
        // orphan sweep is held to the stricter "was this a full crawl", because what it deletes —
        // a hand-typed title, a saved position — is not re-derivable from anything, and the cost
        // of being wrong is therefore not symmetric. Leftover rows are harmless; a lost title is
        // not. Do not "tidy" these into one condition.
        val orphanedDownloads = db.withTransaction {
            applyScan(books, root, skippedRoots, sweepOrphans = !incremental)
        }
        // Outside the transaction: this is storage IO, and it must not hold a write lock. Files
        // first, rows second — dropping the row first would leave the bytes with nothing pointing
        // at them if the process died in between, which is the leak this is meant to close.
        if (orphanedDownloads.isNotEmpty()) {
            for (bookId in orphanedDownloads) {
                runCatching { downloadStorage.deleteBook(bookId) }
                    .onFailure { Log.w(TAG, "could not remove downloaded files for pruned book $bookId", it) }
            }
            downloadDao.deleteOrphans()
        }

        // Nothing skipped means every folder under the root was listed in this pass.
        return Result(bookDao.count(), complete = skippedRoots.isEmpty())
    }

    /**
     * The database half of a scan: carry-over upserts, moved-book re-links, prune, orphan sweep.
     *
     * Split into a **read phase** and a **write phase**, both inside the caller's single
     * transaction. Two things follow from that split.
     *
     * It used to interleave reads and writes per book — `findById` plus `findForBook` (each twice
     * for a moved book), then three writes — so a 300-book library issued something like 1,500
     * statements where a handful of bulk queries do. Everything the write phase needs is now
     * gathered up front and the writes themselves are batched.
     *
     * And in WAL a Room transaction is DEFERRED: SQLite doesn't take the write lock at
     * `beginTransaction`, it takes it at the first *write*. Doing every read before that point
     * means the lock is held only for the writes rather than for the whole apply — which matters
     * because the writer it blocks is the playback position save, and a scan can run while the
     * user is listening.
     *
     * The reads stay INSIDE the transaction deliberately, though. Moving them out would let
     * [com.geozelot.homer.data.metadata.DurationEnricher] or
     * [com.geozelot.homer.data.metadata.CoverEnricher] commit a freshly measured duration or a
     * newly cached cover in between, and the carry-over upsert would then write the value it read
     * earlier straight back over it — losing exactly the measurements the carry-over exists to
     * preserve.
     */
    private suspend fun applyScan(
        books: List<BookDetector.Detected>,
        root: String,
        skippedRoots: List<String>,
        sweepOrphans: Boolean,
    ): List<String> {
        // ── Read phase — no write lock held ──────────────────────────────────────────────

        // Books to keep: the (re-)built ones plus every book under an unchanged, skipped subtree.
        // Anything else is pruned (a vanished book always sits under a visited parent). Skipped
        // roots are files-root paths; book ids are library-root-relative.
        //
        // Still one query per skipped root rather than a filter over `existingBooks`: idsUnder
        // matches with SQL LIKE, which is ASCII-case-insensitive, and `startsWith` is not. Two
        // sibling folders differing only in case would drop out of keepIds and be pruned. There
        // are only a handful of skipped roots, so the queries are not what this is about.
        val keepIds = buildSet {
            books.forEach { add(it.book.id) }
            for (skipped in skippedRoots) {
                addAll(bookDao.idsUnder(skipped.removePrefix(root).trim('/')))
            }
        }

        val existingBooks = bookDao.getAll()
        val existingById = existingBooks.associateBy { it.id }
        val movedFrom = detectMoves(books, existingBooks, keepIds)
        if (movedFrom.isNotEmpty()) Log.i(TAG, "scan: re-linking ${movedFrom.size} moved book(s)")

        // Existing files for exactly the books being rewritten, plus the old rows of any moved
        // book. Scoped to those ids rather than the whole table on purpose: an incremental scan
        // usually rebuilds one book, and loading every file row in the library to service it would
        // cost more than the per-book queries this replaces.
        val fileIds = (books.map { it.book.id } + movedFrom.values).distinct()
        val filesByBook = fileIds
            .chunked(SQL_PARAM_CHUNK)
            .flatMap { audioFileDao.findForBooks(it) }
            .groupBy { it.bookId }

        // Resolve every row to be written. Reading the snapshot here is equivalent to the old
        // per-book queries: no book's carry-over source is ever a row another book in the same pass
        // writes (ids are unique per detected book, and a moved book's old id is by definition not
        // among them).
        val planned = planWrites(books, existingById, filesByBook, movedFrom)

        // Ids to prune, from the pre-write snapshot. Equivalent to reading them back after the
        // upserts: everything those insert is in keepIds by construction, so it could never have
        // been pruned anyway.
        val pruneIds = idsToPrune(existingBooks.map { it.id }, keepIds)
        // Likewise the guard's "how many are indexed" count. It is only *used* on the empty-keepIds
        // branches, and an empty keep-set means no books were detected and the write phase inserts
        // nothing — so the pre-write count is the post-write count there.
        val indexedBefore = existingBooks.size

        // ── Write phase — the transaction takes the write lock from here ─────────────────

        // Batched: books first so the audio_files foreign key holds, then the file rows swapped
        // wholesale. The intermediate state with a book's files deleted but not yet re-inserted is
        // invisible outside the transaction.
        if (planned.books.isNotEmpty()) {
            bookDao.upsert(planned.books)
            planned.books.map { it.id }.chunked(SQL_PARAM_CHUNK).forEach { audioFileDao.deleteForBooks(it) }
            audioFileDao.upsert(planned.files)
        }

        // Re-link user data onto the moved books' new ids BEFORE pruning — the new book rows now
        // exist (so the bookmark FK holds) and the old rows aren't gone yet (bookmarks would
        // otherwise cascade away with them). Downloads are intentionally not re-linked: their bytes
        // on disk are keyed by the old relative path, so a moved book cleanly re-downloads.
        for ((newId, oldId) in movedFrom) {
            // The saved chapter path has to follow the id, or the resumed position can't be
            // resolved to a file any more (see [relinkMediaId]). Read it before the row moves —
            // and read it HERE, not in the read phase: PositionSyncer could otherwise save a new
            // position for this book in between and we would re-link a stale path over it. One
            // query per moved book, and moved books are usually none.
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
        var orphanedDownloads = emptyList<String>()
        when {
            keepIds.isNotEmpty() -> {
                pruneIds.chunked(SQL_PARAM_CHUNK).forEach { bookDao.deleteByIds(it) }
                // Rows keyed by bookId carry no foreign key on purpose, so a rescan can't cascade
                // the user's data away — which also means a pruned book leaves them behind.
                // Sweeping them is only safe after a FULL crawl ([sweepOrphans]): an incremental
                // scan skips unchanged subtrees, a transient server error can silently drop one,
                // and these rows are exactly what lets that book's progress and hand-typed
                // corrections reattach when it comes back. Overrides especially — a title the user
                // typed is not re-derivable from anything. Bookmarks cascade via their FK.
                if (sweepOrphans) {
                    orphanedDownloads = downloadDao.orphanBookIds()
                    playbackStateDao.deleteOrphans()
                    bookOverrideDao.deleteOrphans()
                    bookmarkMetaDao.deleteOrphans()
                    // The `downloads` rows are deliberately NOT deleted here — the caller removes
                    // the files first and drops the rows afterwards, so a process death in between
                    // leaves a row still pointing at them for the next scan to retry.
                }
            }
            indexedBefore == 0 -> Unit // already empty — nothing to prune
            else -> Log.w(
                TAG,
                "scan found no books but $indexedBefore are indexed; skipping prune to avoid " +
                    "wiping the library on an empty or failed crawl",
            )
        }
        return orphanedDownloads
    }

    private companion object {
        /**
         * Ids per statement for any `IN (:ids)` query — one SQL host parameter each, well under
         * SQLite's 999 cap.
         */
        const val SQL_PARAM_CHUNK = 500

        const val TAG = "HomerScan"
    }
}
