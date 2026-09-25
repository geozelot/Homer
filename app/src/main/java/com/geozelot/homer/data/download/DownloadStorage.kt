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

    /**
     * A presence test bound to ONE resolved area, for asking the question many times over.
     *
     * [uri] resolves the area on every call, which is right for a one-off and ruinous for a sweep.
     * Resolving reads two settings and builds a fresh [com.geozelot.homer.data.storage.StorageArea]
     * — and a SAF area's whole path cache lives on that instance, so a per-call area throws the
     * cache away before it can ever be used. Adopting downloads probed the first file of every book
     * in the library that way: a new area, two settings reads and a walk from the tree root, per
     * book. On a 337-book library that measured THIRTEEN SECONDS on every app open.
     *
     * One area for the batch, so `downloads/` and each author folder are resolved once between them.
     * Returned rather than taking a block so the caller's loop stays readable; hold it no longer
     * than the sweep, because a cached path is only as true as the folder was when it was cached.
     */
    suspend fun presenceProbe(): suspend (String) -> Boolean {
        val area = storageLocation.area()
        return { relativePath -> area.uri(path(relativePath)) != null }
    }

    /**
     * Removes every downloaded file, by deleting the whole area.
     *
     * The blunt version on purpose. Reclaiming only the files that no book points at would need a
     * directory listing the storage backends do not offer — and after signing into a different
     * account, "everything" and "the orphans" are the same set anyway.
     */
    suspend fun deleteAll() {
        storageLocation.area().delete("downloads")
    }

    /** Removes all downloaded files for a book. */
    suspend fun deleteBook(bookId: String) {
        // A blank id would target `downloads/` itself and recursively wipe EVERY book's files;
        // a dot segment could escape the area. Refuse both.
        if (bookId.isBlank() || bookId.split('/').any { it == ".." || it == "." }) return
        storageLocation.area().delete(path(bookId))
    }
}
