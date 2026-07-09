package com.geozelot.homer.data.metadata

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Stores extracted cover images under the app's private files dir, keyed by book id. */
@Singleton
class CoverCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir = File(context.filesDir, "covers").apply { mkdirs() }

    /** Writes [bytes] as this book's cover and returns the absolute file path. */
    suspend fun write(bookId: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val file = File(dir, coverName(bookId))
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
