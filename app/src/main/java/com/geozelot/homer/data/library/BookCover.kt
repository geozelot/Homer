package com.geozelot.homer.data.library

import android.net.Uri
import com.geozelot.homer.data.auth.NextcloudCredentials
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.webdav.WebDavClient
import java.io.File

/** Resolves a book's cover to a Coil-loadable model: a WebDAV URL, a cached cover, or null. */
object BookCover {
    fun model(
        book: BookEntity,
        credentials: NextcloudCredentials?,
        webDavClient: WebDavClient,
        libraryRoot: String,
    ): Any? = when {
        // A user-chosen custom cover wins over everything detected.
        book.customCoverPath != null -> cachedCover(book.customCoverPath)
        // A locally cached copy comes BEFORE the remote folder image: it loads instantly, works
        // offline, and costs no traffic. The remote URL is only the fallback until the enricher
        // has cached that book's art (previously it was preferred, so folder covers were fetched
        // from the server on every single display and were blank with no connection).
        book.localCoverPath != null -> cachedCover(book.localCoverPath)
        book.coverFilePath != null && credentials != null ->
            webDavClient.urlFor(credentials, libraryRoot, book.coverFilePath).toString()
        else -> null
    }

    /**
     * A cached cover is stored as a Uri string (`file://` on the default backend, `content://`
     * on a SAF folder). Pre-relocation rows hold a bare filesystem path — wrap those in a File so
     * they still load.
     */
    private fun cachedCover(value: String): Any =
        if (value.startsWith("content://") || value.startsWith("file://")) Uri.parse(value) else File(value)
}
