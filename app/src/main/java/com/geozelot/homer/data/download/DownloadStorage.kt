package com.geozelot.homer.data.download

import android.net.Uri
import com.geozelot.homer.data.storage.StorageLocation
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where offline audio lives: under the app's [StorageLocation] `downloads/` area. Files mirror
 * the server layout, so a book's files all sit beneath `downloads/<bookId>` (bookId is the book
 * folder path). Backend-agnostic (plain files or a SAF tree) via [StorageLocation.area].
 */
@Singleton
class DownloadStorage @Inject constructor(
    private val storageLocation: StorageLocation,
) {
    private fun path(relativePath: String) = "downloads/$relativePath"

    /** Streams a file into place (atomically where the backend allows); returns its playable Uri. */
    suspend fun writeStream(relativePath: String, block: (OutputStream) -> Unit): Uri =
        storageLocation.area().writeStream(path(relativePath), block)

    /** Playable Uri of a downloaded file, or null if it isn't present. */
    suspend fun uri(relativePath: String): Uri? = storageLocation.area().uri(path(relativePath))

    /** Removes all downloaded files for a book. */
    suspend fun deleteBook(bookId: String) {
        // A blank id would target `downloads/` itself and recursively wipe EVERY book's files;
        // a dot segment could escape the area. Refuse both.
        if (bookId.isBlank() || bookId.split('/').any { it == ".." || it == "." }) return
        storageLocation.area().delete(path(bookId))
    }
}
