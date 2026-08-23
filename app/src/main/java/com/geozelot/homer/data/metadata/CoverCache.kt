package com.geozelot.homer.data.metadata

import com.geozelot.homer.data.storage.StorageLocation
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores cover images under the app's [StorageLocation] `covers/` area, keyed by book id.
 * Returns each cover's Uri (as a string) for storage in the DB — `file://` on the default
 * backend, `content://` on a SAF folder — which [com.geozelot.homer.data.library.BookCover]
 * resolves for display.
 */
@Singleton
class CoverCache @Inject constructor(
    private val storageLocation: StorageLocation,
) {
    /** Writes [bytes] as this book's cover; returns its Uri string. */
    suspend fun write(bookId: String, bytes: ByteArray): String =
        storageLocation.area().write("covers/${coverName(bookId)}", bytes).toString()

    /**
     * Writes a user-chosen custom cover under a fresh, timestamped name (so replacing one busts
     * any image cache), dropping this book's previous custom cover first. Returns its Uri string.
     */
    suspend fun writeCustom(bookId: String, bytes: ByteArray, stamp: Long): String {
        val area = storageLocation.area()
        area.deleteMatching("covers", "${hash(bookId)}-custom-")
        return area.write("covers/${hash(bookId)}-custom-$stamp.img", bytes).toString()
    }

    /** Reads a book's cached (auto-extracted) cover bytes, or null if absent — used to publish it
     *  to the shared cover cache. */
    suspend fun readBytes(bookId: String): ByteArray? =
        storageLocation.area().readBytes("covers/${coverName(bookId)}")

    /** Stable, safe filename for a book's cover — also used as its name in the shared cache. */
    fun coverName(bookId: String): String = "${hash(bookId)}.img"

    // Book ids are folder paths (slashes, spaces); hash to a safe, collision-resistant name.
    private fun hash(bookId: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bookId.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
