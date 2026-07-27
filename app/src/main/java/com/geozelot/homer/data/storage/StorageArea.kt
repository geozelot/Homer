package com.geozelot.homer.data.storage

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Backend-agnostic file area rooted at the app's chosen storage location (`Homer/`). Relative
 * paths are POSIX-style, e.g. `downloads/Author/Book/01.mp3`. Two implementations back it: a plain
 * [FileStorageArea] (the app-external default) and a SAF [com.geozelot.homer.data.storage] tree
 * for a user-chosen folder. Consumers work in [Uri]s so the same code streams to either backend.
 */
interface StorageArea {
    /** Writes [bytes] at [rel] (creating parents, replacing any existing) and returns its Uri. */
    suspend fun write(rel: String, bytes: ByteArray): Uri

    /** Streams to [rel] (creating parents, replacing any existing) and returns its Uri. */
    suspend fun writeStream(rel: String, block: (OutputStream) -> Unit): Uri

    /** Uri of the existing file at [rel], or null if it isn't there. */
    suspend fun uri(rel: String): Uri?

    suspend fun exists(rel: String): Boolean

    suspend fun readBytes(rel: String): ByteArray?

    /** Deletes the file or directory subtree at [rel] (best-effort). */
    suspend fun delete(rel: String)

    /** Deletes files directly inside [dirRel] whose name starts with [namePrefix]. */
    suspend fun deleteMatching(dirRel: String, namePrefix: String)
}

/** [StorageArea] over a plain filesystem [root] (the app-external default). */
class FileStorageArea(private val root: File) : StorageArea {

    private fun file(rel: String) = File(root, rel)

    override suspend fun write(rel: String, bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
        val f = file(rel)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        Uri.fromFile(f)
    }

    override suspend fun writeStream(rel: String, block: (OutputStream) -> Unit): Uri =
        withContext(Dispatchers.IO) {
            val f = file(rel)
            f.parentFile?.mkdirs()
            // Write to a .part sibling and rename into place, so a truncated write can never be
            // mistaken for a finished file.
            val part = File(f.parentFile, f.name + ".part")
            part.outputStream().use(block)
            if (f.exists()) f.delete()
            if (!part.renameTo(f)) throw IOException("could not finalize $rel")
            Uri.fromFile(f)
        }

    override suspend fun uri(rel: String): Uri? = withContext(Dispatchers.IO) {
        file(rel).takeIf { it.exists() }?.let { Uri.fromFile(it) }
    }

    override suspend fun exists(rel: String): Boolean = withContext(Dispatchers.IO) { file(rel).exists() }

    override suspend fun readBytes(rel: String): ByteArray? = withContext(Dispatchers.IO) {
        file(rel).takeIf { it.exists() }?.readBytes()
    }

    override suspend fun delete(rel: String) {
        withContext(Dispatchers.IO) { file(rel).deleteRecursively() }
    }

    override suspend fun deleteMatching(dirRel: String, namePrefix: String) {
        withContext(Dispatchers.IO) {
            file(dirRel).listFiles { f -> f.name.startsWith(namePrefix) }?.forEach { it.delete() }
        }
    }
}
