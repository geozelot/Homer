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
 * Crawls the WebDAV tree from the library root via depth-1 PROPFINDs, detecting books
 * (folders that directly contain audio files) and persisting them to Room.
 *
 * Incremental scans prune unchanged subtrees using Nextcloud's propagated collection
 * ETags — a directory whose stored ETag matches is skipped, and the books already
 * indexed beneath it are preserved.
 *
 * Cancellable: honors coroutine cancellation between directories.
 */
class LibraryScanner @Inject constructor(
    private val webDavClient: WebDavClient,
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
    private val crawlDirDao: CrawlDirDao,
    private val detector: BookDetector,
) {
    data class Result(val bookCount: Int)

    suspend fun scan(
        libraryRoot: String,
        incremental: Boolean,
        now: Long,
        onProgress: (directoriesVisited: Int, booksFound: Int) -> Unit,
    ): Result {
        val root = libraryRoot.trim('/')
        val keepBookIds = mutableListOf<String>()
        var directoriesVisited = 0
        var booksFound = 0

        val stack = ArrayDeque<String>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = stack.removeLast()
            val normDir = dir.trim('/')

            val entries = webDavClient.propfind(dir, depth = 1)
            directoriesVisited++

            val selfEtag = entries.firstOrNull { it.path == normDir }?.etag
            val children = entries.filter { it.path != normDir }
            val audioFiles = children.filter { !it.isCollection && AudioFormats.isAudio(it.name) }
            val imageFiles = children.filter { !it.isCollection && AudioFormats.isImage(it.name) }
            val childDirs = children.filter { it.isCollection }

            if (audioFiles.isNotEmpty()) {
                val detected = detector.detect(normDir, audioFiles, imageFiles, root, now)
                bookDao.upsert(listOf(detected.book))
                audioFileDao.deleteForBook(detected.book.id)
                audioFileDao.upsert(detected.files)
                keepBookIds += detected.book.id
                booksFound++
            }

            for (childDir in childDirs) {
                val stored = crawlDirDao.findByPath(childDir.path)
                val unchanged = incremental &&
                    stored?.etag != null &&
                    childDir.etag != null &&
                    stored.etag == childDir.etag
                if (unchanged) {
                    // Preserve books already indexed beneath the skipped subtree.
                    keepBookIds += bookDao.idsUnder(childDir.path)
                } else {
                    stack.addLast(childDir.path)
                }
            }

            crawlDirDao.upsert(CrawlDirEntity(normDir, selfEtag, now))
            onProgress(directoriesVisited, booksFound)
        }

        // Prune books whose folders vanished. Guard the empty case — SQLite rejects
        // an empty `NOT IN ()`.
        if (keepBookIds.isEmpty()) bookDao.deleteAll() else bookDao.deleteMissing(keepBookIds)
        return Result(booksFound)
    }
}
