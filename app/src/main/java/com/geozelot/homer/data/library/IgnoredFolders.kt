package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.dao.CrawlDirDao
import com.geozelot.homer.data.settings.LibrarySettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Folders of the library Homer does not read.
 *
 * ## What ignoring a folder does
 *
 * The crawl does not go into it, so nothing new from it reaches the library — which is the point:
 * a folder of fresh additions, still named however they arrived, stays out until somebody has
 * tidied it. Books the library ALREADY has from an ignored folder are hidden, not deleted: no scan
 * prunes them, and every screen leaves them out. Ignoring is therefore safe to undo — the books come
 * back exactly as they were, with their progress, bookmarks and corrections.
 *
 * ## Why it travels with the shared index
 *
 * Kept on one device only, it would hold only until another device that maintains the library
 * crawled the folder and published what it found. So the list is published beside the path
 * templates in `corrections.json` and adopted, newest edit winning, like everything else there.
 *
 * Paths are library-root-relative, the same form as a book's id, so "is this book under an ignored
 * folder" is a prefix test on the id.
 */
@Singleton
class IgnoredFolders @Inject constructor(
    private val librarySettings: LibrarySettings,
    private val crawlDirDao: CrawlDirDao,
) {
    val folders: Flow<List<String>> = librarySettings.ignoredFolders

    /** When the list was last deliberately set — what the shared index compares. Zero for never. */
    val editedAt: Flow<Long> = librarySettings.ignoredFoldersEditedAt

    suspend fun add(folder: String) = set(librarySettings.ignoredFolders.first() + folder)

    suspend fun remove(folder: String) = set(librarySettings.ignoredFolders.first() - normalise(folder))

    /**
     * Replaces the list, stamped [editedAt] — now for a local edit, the publisher's stamp when one
     * is adopted from the shared index.
     *
     * The crawl ETags above every folder whose state changed are dropped on the way. Without that,
     * UN-ignoring did nothing: the folder's parents had not changed on the server, so an ordinary
     * scan skipped their whole subtree on its stored ETag and never went back into the folder.
     */
    suspend fun set(folders: List<String>, editedAt: Long = System.currentTimeMillis()) {
        val before = librarySettings.ignoredFolders.first().toSet()
        val after = tidy(folders)
        librarySettings.setIgnoredFolders(after, editedAt)
        val changed = (before - after.toSet()) + (after.toSet() - before)
        if (changed.isEmpty()) return
        val root = librarySettings.libraryRoot.first().trim('/')
        val paths = changed.flatMap { ancestorsOf(it) }.distinct().map { if (root.isEmpty()) it else "$root/$it" }
        crawlDirDao.deleteByPaths(paths)
    }

    companion object {
        /** A folder as it is stored: trimmed, no leading or trailing slash. */
        fun normalise(folder: String): String = folder.trim().trim('/')

        /** One entry per folder, none inside another — the outer one already covers it — sorted. */
        fun tidy(folders: Collection<String>): List<String> {
            val clean = folders.map(::normalise).filter { it.isNotEmpty() }.distinct()
            return clean.filterNot { folder -> clean.any { it != folder && covers(listOf(it), folder) } }.sorted()
        }

        /**
         * Whether [path] — a book id or a folder, library-relative — is an ignored folder or inside
         * one. Exact by segment: ignoring `Author/Book` does not touch `Author/Book 2`.
         */
        fun covers(ignored: Collection<String>, path: String): Boolean {
            val p = normalise(path)
            return ignored.any { folder -> p == folder || p.startsWith("$folder/") }
        }

        /** [folder] and every folder above it, outermost first: `A/B/C` → `A`, `A/B`, `A/B/C`. */
        fun ancestorsOf(folder: String): List<String> {
            val segments = normalise(folder).split('/').filter { it.isNotEmpty() }
            return segments.indices.map { segments.subList(0, it + 1).joinToString("/") }
        }
    }
}
