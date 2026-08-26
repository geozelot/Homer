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
 * Maps a single path segment to a name that's legal on public/FUSE/vfat volumes (illegal and
 * control chars → `_`, trailing dots/spaces trimmed). Deterministic, so the same input always maps
 * to the same on-disk name and can be found again. Shared by [FileStorageArea] and the SAF backend.
 */
internal fun safeStorageSegment(seg: String): String =
    buildString {
        for (c in seg) append(if (c.code < 0x20 || c in ILLEGAL_NAME_CHARS) '_' else c)
    }.trimEnd(' ', '.').ifBlank { "_" }

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

    /**
     * A stable string identifying the *root* this area writes to, or null if it can't be
     * determined. Only comparable between areas of the same backend (a canonical filesystem path
     * and a SAF tree Uri never match even when they address the same directory), so a cross-backend
     * comparison needs the probe in `StorageMigrator`.
     */
    suspend fun identity(): String?
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
        val f = if (!sanitize) {
            File(root, rel)
        } else {
            val safe = rel.split('/').filter { it.isNotEmpty() }.joinToString("/") { safeStorageSegment(it) }
            File(root, safe)
        }
        // Containment guard (defense in depth): never resolve outside root, even if a caller
        // somehow passed a `..` path. sanitize=true already neutralizes dot segments; this backs
        // up the unsanitized default area against a traversal path slipping through.
        val rootPath = root.canonicalPath
        val resolved = f.canonicalPath
        require(resolved == rootPath || resolved.startsWith(rootPath + File.separator)) {
            "path escapes storage root: $rel"
        }
        return f
    }

    override suspend fun write(rel: String, bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
        val f = file(rel)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        Uri.fromFile(f)
    }

    /**
     * Streams into a `.part` sibling and moves it into place, so a truncated write can never be
     * mistaken for a finished file.
     *
     * The move is a rename where it can be — atomic and free — and a copy where it cannot. A rename
     * fails for reasons `File.renameTo` will not name: it returns a bare `false`, which is how a
     * download failure came to read `could not finalize …` and say nothing more. On FUSE-backed
     * shared storage it can also refuse a change of extension that the same call makes without
     * complaint on an app-private volume. The copy costs a second pass over the bytes and is only
     * ever paid on that failure.
     */
    override suspend fun writeStream(rel: String, block: (OutputStream) -> Unit): Uri =
        withContext(Dispatchers.IO) {
            val f = file(rel)
            f.parentFile?.mkdirs()
            val part = File(f.parentFile, f.name + ".part")
            part.outputStream().use(block)
            if (f.exists()) f.delete()
            if (!part.renameTo(f)) finalizeByCopy(rel, part, f)
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

    // Canonical, so two tokens for the same directory (a symlinked /sdcard vs
    // /storage/emulated/0 prefix, a `.` segment) resolve to one identity.
    override suspend fun identity(): String? =
        withContext(Dispatchers.IO) { runCatching { root.canonicalPath }.getOrNull() }
}

/**
 * The fallback for a rename that would not go: copy the bytes, then drop the `.part`.
 *
 * Top-level and internal so it can be tested for what it does to a real directory, the way
 * [safeStorageSegment] is — the enclosing [FileStorageArea.writeStream] returns a `Uri`, which a
 * JVM test cannot make.
 *
 * One case is deliberately not recovered. A destination folder that disappeared while the write was
 * in flight is what a CANCELLED download looks like: the blocking copy cannot be interrupted, so the
 * worker writes on into an unlinked file while `DownloadManager.delete` removes the folder underneath
 * it. Re-creating that folder to complete the copy would resurrect exactly what the user asked to be
 * rid of, so this reports the folder is gone and leaves it gone.
 */
internal fun finalizeByCopy(rel: String, part: File, target: File) {
    val parent = target.parentFile
    if (parent != null && !parent.isDirectory) {
        part.delete()
        throw IOException("could not finalize $rel: the destination folder was removed while it was being written")
    }
    try {
        part.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
    } catch (e: IOException) {
        // Read the state BEFORE cleaning up, or the diagnostics describe the cleanup rather than the
        // failure — an empty directory in the way deletes successfully and then reports as "absent".
        val found = describeFinalizeFailure(target, part)
        // A half-finished copy is the one thing the .part dance exists to prevent.
        target.delete()
        throw IOException("could not finalize $rel: $found", e)
    }
    part.delete()
}

/** What can be observed about a failed finalize, since the rename itself will not say. */
// UsableSpace: lint wants StorageManager#getAllocatableBytes, which counts space it could free by
// evicting caches. That is the right number for deciding whether to start a write; this is a
// diagnostic for one that already failed, where what matters is what the volume reported at the
// time, and it has no Context to ask with.
@Suppress("UsableSpace")
private fun describeFinalizeFailure(target: File, part: File): String {
    val parent = target.parentFile
    val state = when {
        target.isDirectory -> "a directory"
        target.exists() -> "exists (${target.length()} bytes)"
        else -> "absent"
    }
    return "part=${if (part.exists()) "${part.length()} bytes" else "gone"}, target=$state, " +
        "folderWritable=${parent?.canWrite()}, free=${parent?.usableSpace ?: 0} bytes"
}
