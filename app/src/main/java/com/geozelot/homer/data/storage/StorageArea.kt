package com.geozelot.homer.data.storage

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Characters that public shared storage (FUSE/vfat) rejects in a file or folder name. */
private const val ILLEGAL_NAME_CHARS = ":*?\"<>|\\"

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

    /** Opens [rel] for streaming reads (caller closes), or null if absent — for large-file copies. */
    suspend fun openInputStream(rel: String): InputStream?

    /** Deletes the file or directory subtree at [rel] (best-effort). */
    suspend fun delete(rel: String)

    /** Deletes files directly inside [dirRel] whose name starts with [namePrefix]. */
    suspend fun deleteMatching(dirRel: String, namePrefix: String)
}

/**
 * [StorageArea] over a plain filesystem [root]. When [sanitize] is set, each path segment is
 * mapped to a name that's legal on public shared storage (a FUSE/vfat volume forbids `: * ? " < >
 * | \\` and control chars, and dislikes trailing dots/spaces). The app-private default dir
 * tolerates those characters, so it uses [sanitize] = false to keep existing file names intact;
 * a user-chosen public folder uses [sanitize] = true. Translation is deterministic, so every
 * read/write/delete for the same relative path maps to the same on-disk file.
 */
class FileStorageArea(private val root: File, private val sanitize: Boolean = false) : StorageArea {

    private fun file(rel: String): File {
        if (!sanitize) return File(root, rel)
        val safe = rel.split('/').filter { it.isNotEmpty() }.joinToString("/") { safeSegment(it) }
        return File(root, safe)
    }

    private fun safeSegment(seg: String): String =
        buildString {
            for (c in seg) append(if (c.code < 0x20 || c in ILLEGAL_NAME_CHARS) '_' else c)
        }.trimEnd(' ', '.').ifBlank { "_" }

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

    override suspend fun openInputStream(rel: String): InputStream? = withContext(Dispatchers.IO) {
        file(rel).takeIf { it.exists() }?.inputStream()
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
