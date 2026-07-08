package com.geozelot.homer.data.library

import com.geozelot.homer.data.auth.NextcloudCredentials
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.webdav.WebDavClient
import java.io.File

/** Resolves a book's cover to a Coil-loadable model: a WebDAV URL, a cached file, or null. */
object BookCover {
    fun model(
        book: BookEntity,
        credentials: NextcloudCredentials?,
        webDavClient: WebDavClient,
    ): Any? = when {
        book.coverFilePath != null && credentials != null ->
            webDavClient.urlFor(credentials, book.coverFilePath).toString()
        book.localCoverPath != null -> File(book.localCoverPath)
        else -> null
    }
}
