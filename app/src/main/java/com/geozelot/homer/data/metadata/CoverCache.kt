package com.geozelot.homer.data.metadata

import com.geozelot.homer.data.storage.StorageLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Stores extracted cover images under the app's [StorageLocation] `covers/` root, keyed by book id. */
@Singleton
class CoverCache @Inject constructor(
    storageLocation: StorageLocation,
) {
    private val dir = storageLocation.coversDir

    /** Writes [bytes] as this book's cover and returns the absolute file path. */
    suspend fun write(bookId: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val file = File(dir, coverName(bookId))
        file.writeBytes(bytes)
        file.absolutePath
    }

    /**
     * Writes a user-chosen custom cover to a separate file (so the extracted-cover pass can't
     * overwrite it) and returns its absolute path. A fresh filename each call busts Coil's
     * on-disk cache so replacing a custom cover shows the new image immediately.
     */
    suspend fun writeCustom(bookId: String, bytes: ByteArray, stamp: Long): String = withContext(Dispatchers.IO) {
        val file = File(dir, "${hash(bookId)}-custom-$stamp.img")
        // Drop any previous custom cover for this book so they don't accumulate.
        dir.listFiles { f -> f.name.startsWith("${hash(bookId)}-custom-") && f != file }?.forEach { it.delete() }
        file.writeBytes(bytes)
        file.absolutePath
    }

    /** Stable, safe filename for a book's cover — also used as its name in the shared cache. */
    fun coverName(bookId: String): String = "${hash(bookId)}.img"

    // Book ids are folder paths (slashes, spaces); hash to a safe, collision-resistant name.
    private fun hash(bookId: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bookId.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
