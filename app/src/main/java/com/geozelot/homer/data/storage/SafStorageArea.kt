package com.geozelot.homer.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

/**
 * [StorageArea] over a user-chosen SAF folder (a `content://` document tree from
 * `ACTION_OPEN_DOCUMENT_TREE`). Its contents survive an app uninstall; the folder is
 * user-accessible in a file manager.
 *
 * Resolution is path-based: for the on-device storage providers the tree document id encodes the
 * path (e.g. `primary:Homer`), so a child is addressed as `‹treeDocId›/‹rel›` and built directly
 * with [DocumentsContract.buildDocumentUriUsingTree] — O(1), exact names, no directory listing.
 * This targets folders on the phone/SD storage (the realistic pick); opaque cloud DocumentsProviders
 * that don't use path-based ids aren't supported.
 */
class SafStorageArea(context: Context, private val treeUri: Uri) : StorageArea {

    private val resolver = context.contentResolver
    private val treeDocId: String = DocumentsContract.getTreeDocumentId(treeUri)

    private fun docId(rel: String): String = if (rel.isEmpty()) treeDocId else "$treeDocId/$rel"

    private fun docUri(rel: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId(rel))

    private fun existsUri(uri: Uri): Boolean = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    /** Creates any missing parent directories for [rel] and returns the parent's document Uri. */
    private fun ensureParentDirs(rel: String): Uri {
        val parents = rel.split('/').filter { it.isNotEmpty() }.dropLast(1)
        var parentRel = ""
        for (seg in parents) {
            val childRel = if (parentRel.isEmpty()) seg else "$parentRel/$seg"
            if (!existsUri(docUri(childRel))) {
                DocumentsContract.createDocument(
                    resolver, docUri(parentRel), DocumentsContract.Document.MIME_TYPE_DIR, seg,
                ) ?: throw IOException("SAF: could not create dir $childRel")
            }
            parentRel = childRel
        }
        return docUri(parentRel)
    }

    /** Creates (replacing any existing) the file at [rel]; returns its actual document Uri. */
    private fun createFile(rel: String): Uri {
        val name = rel.substringAfterLast('/')
        val parentUri = ensureParentDirs(rel)
        val target = docUri(rel)
        if (existsUri(target)) runCatching { DocumentsContract.deleteDocument(resolver, target) }
        return DocumentsContract.createDocument(resolver, parentUri, mimeFor(name), name)
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

    override suspend fun uri(rel: String): Uri? = withContext(Dispatchers.IO) {
        docUri(rel).takeIf { existsUri(it) }
    }

    override suspend fun exists(rel: String): Boolean = withContext(Dispatchers.IO) { existsUri(docUri(rel)) }

    override suspend fun readBytes(rel: String): ByteArray? = withContext(Dispatchers.IO) {
        val uri = docUri(rel)
        if (!existsUri(uri)) null else resolver.openInputStream(uri)?.use { it.readBytes() }
    }

    override suspend fun delete(rel: String) {
        withContext(Dispatchers.IO) {
            val uri = docUri(rel)
            if (existsUri(uri)) runCatching { DocumentsContract.deleteDocument(resolver, uri) }
        }
    }

    override suspend fun deleteMatching(dirRel: String, namePrefix: String) {
        withContext(Dispatchers.IO) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId(dirRel))
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
