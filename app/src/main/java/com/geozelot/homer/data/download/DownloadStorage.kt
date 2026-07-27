package com.geozelot.homer.data.download

import com.geozelot.homer.data.storage.StorageLocation
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where offline audio lives: under the app's [StorageLocation] `downloads/` root. Files mirror
 * the server layout, so a book's files all sit beneath `downloads/<bookId>` (bookId is the book
 * folder path).
 */
@Singleton
class DownloadStorage @Inject constructor(
    storageLocation: StorageLocation,
) {
    private val root = storageLocation.downloadsDir

    /** Local destination for a file given its library-relative path. */
    fun fileFor(relativePath: String): File = File(root, relativePath)

    /** Removes all downloaded files for a book. */
    fun deleteBook(bookId: String) {
        File(root, bookId).deleteRecursively()
    }
}
