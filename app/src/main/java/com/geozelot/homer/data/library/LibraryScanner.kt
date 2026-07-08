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
 * folder that directly contains audio, then hands the set to [BookDetector] to group
 * into books (merging multi-part books) and persists the result to Room.
 *
 * The crawl is a full traversal for now so book grouping always sees the whole tree;
 * ETag-based incremental pruning (stored per directory) is a later optimization.
 * Cancellable between directories.
 */
class LibraryScanner @Inject constructor(
    private val webDavClient: WebDavClient,
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
    private val crawlDirDao: CrawlDirDao,
    private val detector: BookDetector,
) {
    data class Result(val bookCount: Int)

    @Suppress("UNUSED_PARAMETER")
    suspend fun scan(
        libraryRoot: String,
        incremental: Boolean,
        now: Long,
        onProgress: (directoriesVisited: Int, audioFoldersFound: Int) -> Unit,
    ): Result {
        val root = libraryRoot.trim('/')
        val audioFolders = mutableListOf<BookDetector.AudioFolder>()
        var directoriesVisited = 0

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
                audioFolders += BookDetector.AudioFolder(normDir, audioFiles, imageFiles)
            }
            childDirs.forEach { stack.addLast(it.path) }

            // Record the directory ETag for future incremental scans.
            crawlDirDao.upsert(CrawlDirEntity(normDir, selfEtag, now))
            onProgress(directoriesVisited, audioFolders.size)
        }

        val books = detector.buildBooks(audioFolders, root, now)
        for (detected in books) {
            bookDao.upsert(listOf(detected.book))
            audioFileDao.deleteForBook(detected.book.id)
            audioFileDao.upsert(detected.files)
        }

        // Prune books whose folders vanished. Guard the empty case — SQLite rejects
        // an empty `NOT IN ()`.
        val keepIds = books.map { it.book.id }
        if (keepIds.isEmpty()) bookDao.deleteAll() else bookDao.deleteMissing(keepIds)

        return Result(books.size)
    }
}
