package com.geozelot.homer.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where offline audio lives: app-private internal storage (sandboxed, auto-removed on
 * uninstall). Files mirror the server layout under a `downloads/` root, so a book's files
 * all sit beneath `downloads/<bookId>` (bookId is the book folder path).
 */
@Singleton
class DownloadStorage @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val root = File(context.filesDir, "downloads")

    /** Local destination for a file given its library-relative path. */
    fun fileFor(relativePath: String): File = File(root, relativePath)

    /** Removes all downloaded files for a book. */
    fun deleteBook(bookId: String) {
        File(root, bookId).deleteRecursively()
    }
}
