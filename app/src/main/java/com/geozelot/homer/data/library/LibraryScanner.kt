package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.CrawlDirDao
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
        for (detected in books) {
            // Carry cached data across the rescan (upsert replaces the rows): the extracted
            // cover, and per-file durations matched by path — otherwise every rescan would
            // discard all measured durations and re-probe the whole library on next open.
            val existing = bookDao.findById(detected.book.id)
            val existingCover = existing?.localCoverPath
            val existingGenre = existing?.genre
            val existingDurations = audioFileDao.findForBook(detected.book.id)
                .associate { it.relativePath to it.durationMs }
            val files = detected.files.map { it.copy(durationMs = it.durationMs ?: existingDurations[it.relativePath]) }
            // Recompute the total from the current file set so removed/added files stay correct.
            val total = if (files.isNotEmpty() && files.all { it.durationMs != null }) {
                files.sumOf { it.durationMs!! }
            } else {
                null
            }
            bookDao.upsert(
                listOf(
                    detected.book.copy(
                        localCoverPath = existingCover,
                        coverAttempted = existing?.coverAttempted ?: false,
                        genre = existingGenre,
                        totalDurationMs = total,
                    ),
                ),
            )
            audioFileDao.deleteForBook(detected.book.id)
            audioFileDao.upsert(files)
        }

        // Keep the (re-)built books plus every book under an unchanged, skipped subtree; prune
        // the rest (a vanished book always sits under a visited parent, so it's excluded here).
        // Skipped roots are files-root paths; book ids are library-root-relative.
        val keepIds = buildSet {
            books.forEach { add(it.book.id) }
            for (skipped in skippedRoots) {
                addAll(bookDao.idsUnder(skipped.removePrefix(root).trim('/')))
            }
        }.toList()
        if (keepIds.isEmpty()) bookDao.deleteAll() else bookDao.deleteMissing(keepIds)

        return Result(bookDao.count())
    }
}
