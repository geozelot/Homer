package com.geozelot.homer.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * [StorageArea] over a user-chosen SAF folder (a `content://` document tree from
 * `ACTION_OPEN_DOCUMENT_TREE`). Its contents survive an app uninstall and are user-accessible in
 * a file manager.
 *
 * Documents are resolved by **listing the parent's children and matching display names**, using
 * the real document ids the provider returns. This is provider-agnostic — unlike constructing a
 * document id by string-joining the tree id with the relative path, which only works for the
 * primary "externalstorage" provider and silently mis-resolves elsewhere (creating duplicate,
 * numbered folders that the app can then never find again). A small per-instance cache keeps a
 * multi-file book from re-listing the same directories.
 */
class SafStorageArea(context: Context, private val treeUri: Uri) : StorageArea {

    private val resolver = context.contentResolver
    private val treeDocId: String = DocumentsContract.getTreeDocumentId(treeUri)
    private val rootUri: Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

    /** rel (POSIX dir path) → its resolved document Uri, to avoid re-walking within one instance. */
    private val dirCache = HashMap<String, Uri>()

    /** The child document named [name] directly under [parent], or null if absent. */
    private fun findChild(parent: Uri, name: String): Uri? {
        val parentDocId = DocumentsContract.getDocumentId(parent)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        return runCatching {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) == name) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
                    }
                }
                null
            }
        }.onFailure { Log.w(TAG, "SAF children query failed under $name", it) }.getOrNull()
    }

    /** Resolves the directory at [rel] (empty = the tree root), creating missing levels if [create]. */
    private fun resolveDir(rel: String, create: Boolean): Uri? {
        if (rel.isEmpty()) return rootUri
        dirCache[rel]?.let { return it }
        val segs = rel.split('/').filter { it.isNotEmpty() }
        var current = rootUri
        val built = StringBuilder()
        for (seg in segs) {
            // Address the provider by a volume-legal name (same mapping as FileStorageArea), so a
            // FUSE/vfat-backed tree that rewrites illegal chars doesn't leave a file we can't find
            // again. Cache keys stay the raw path so callers keep addressing by the original rel.
            val safe = safeStorageSegment(seg)
            var child = findChild(current, safe)
            if (child == null) {
                if (!create) return null
                child = DocumentsContract.createDocument(
                    resolver, current, DocumentsContract.Document.MIME_TYPE_DIR, safe,
                ) ?: throw IOException("SAF: could not create directory $seg (in $rel)")
            }
            current = child
            if (built.isNotEmpty()) built.append('/')
            built.append(seg)
            dirCache[built.toString()] = current
        }
        return current
    }

    private fun parentRel(rel: String) = rel.substringBeforeLast('/', "")
    private fun nameOf(rel: String) = rel.substringAfterLast('/')

    /** The existing file document at [rel], or null. */
    private fun resolveFile(rel: String): Uri? {
        val parent = resolveDir(parentRel(rel), create = false) ?: return null
        return findChild(parent, safeStorageSegment(nameOf(rel)))
    }

    /** Creates the file at [rel] fresh (creating parents, deleting any existing so no `name (1)`). */
    private fun createFile(rel: String): Uri {
        val parent = resolveDir(parentRel(rel), create = true)
            ?: throw IOException("SAF: could not resolve parent of $rel")
        val name = safeStorageSegment(nameOf(rel))
        findChild(parent, name)?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        return DocumentsContract.createDocument(resolver, parent, mimeFor(name), name)
            ?: throw IOException("SAF: could not create file $rel")
    }

    override suspend fun write(rel: String, bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
        val uri = createFile(rel)
        (resolver.openOutputStream(uri, "w") ?: throw IOException("SAF: open output $rel"))
            .use { it.write(bytes) }
        uri
    }

    override suspend fun writeStream(rel: String, block: (OutputStream) -> Unit): Uri =
        withContext(Dispatchers.IO) {
            val uri = createFile(rel)
            (resolver.openOutputStream(uri, "w") ?: throw IOException("SAF: open output $rel")).use(block)
            uri
        }

    override suspend fun uri(rel: String): Uri? = withContext(Dispatchers.IO) { resolveFile(rel) }

    override suspend fun exists(rel: String): Boolean = withContext(Dispatchers.IO) {
        resolveFile(rel) != null || resolveDir(rel, create = false) != null
    }

    override suspend fun readBytes(rel: String): ByteArray? = withContext(Dispatchers.IO) {
        resolveFile(rel)?.let { uri -> resolver.openInputStream(uri)?.use { it.readBytes() } }
    }

    override suspend fun openInputStream(rel: String): InputStream? = withContext(Dispatchers.IO) {
        resolveFile(rel)?.let { resolver.openInputStream(it) }
    }

    override suspend fun delete(rel: String) {
        withContext(Dispatchers.IO) {
            val uri = resolveFile(rel) ?: resolveDir(rel, create = false) ?: return@withContext
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            dirCache.keys.filter { it == rel || it.startsWith("$rel/") }.forEach { dirCache.remove(it) }
        }
    }

    override suspend fun deleteMatching(dirRel: String, namePrefix: String) {
        withContext(Dispatchers.IO) {
            val dir = resolveDir(dirRel, create = false) ?: return@withContext
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, DocumentsContract.getDocumentId(dir),
            )
            runCatching {
                resolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(1)?.startsWith(namePrefix) == true) {
                            DocumentsContract.deleteDocument(
                                resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0)),
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "HomerStore"
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "m4b", "mp4", "aac" -> "audio/mp4"
        "ogg", "oga", "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }
}
