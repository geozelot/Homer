package com.geozelot.homer.data.library

import android.util.Log
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.BookmarkMetaDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.CrawlDirDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.CrawlDirEntity
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

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
    private val webDavClient: WebDavClient,
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
    private val crawlDirDao: CrawlDirDao,
    private val playbackStateDao: PlaybackStateDao,
    private val bookOverrideDao: BookOverrideDao,
    private val bookmarkDao: BookmarkDao,
    private val bookmarkMetaDao: BookmarkMetaDao,
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

        val books = detector.buildBooks(audioFolders, root, now)

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
            val existingDurations = audioFileDao.findForBook(detected.book.id)
                .associate { it.relativePath to it.durationMs }
            val movedDurations = movedOldId
                ?.let { old -> audioFileDao.findForBook(old).associate { it.fileName to it.durationMs } }
                ?: emptyMap()
            val files = detected.files.map {
                it.copy(durationMs = it.durationMs ?: existingDurations[it.relativePath] ?: movedDurations[it.fileName])
            }
            // Recompute the total from the current file set so removed/added files stay correct.
            val total = if (files.isNotEmpty() && files.all { it.durationMs != null }) {
                files.sumOf { it.durationMs!! }
            } else {
                null
            }
            bookDao.upsert(
                listOf(
                    detected.book.copy(
                        localCoverPath = source?.localCoverPath,
                        coverAttempted = source?.coverAttempted ?: false,
                        genre = source?.genre,
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
            playbackStateDao.relink(oldId, newId)
            bookOverrideDao.relink(oldId, newId)
            bookmarkMetaDao.relink(oldId, newId)
            bookmarkDao.relink(oldId, newId)
        }

        val keepList = keepIds.toList()
        if (keepList.isEmpty()) bookDao.deleteAll() else bookDao.deleteMissing(keepList)

        return Result(bookDao.count())
    }

    private companion object {
        const val TAG = "HomerScan"
    }
}
