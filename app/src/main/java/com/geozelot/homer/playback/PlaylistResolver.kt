package com.geozelot.homer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.webdav.WebDavClient
import javax.inject.Inject

/** Resolves a book into an ordered playlist of streamable [MediaItem]s. */
class PlaylistResolver @Inject constructor(
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
    private val credentialStore: CredentialStore,
    private val webDavClient: WebDavClient,
) {
    data class Playlist(val bookTitle: String, val items: List<MediaItem>)

    suspend fun resolve(bookId: String): Playlist? {
        val credentials = credentialStore.credentials.value ?: return null
        val book = bookDao.findById(bookId) ?: return null
        val files = audioFileDao.findForBook(bookId)
        if (files.isEmpty()) return null

        val items = files.map { file ->
            val url = webDavClient.urlFor(credentials, file.relativePath).toString()
            val chapterTitle = file.fileName.substringBeforeLast('.')
            MediaItem.Builder()
                .setMediaId(file.relativePath)
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(chapterTitle)
                        .setAlbumTitle(book.title)
                        .setArtist(book.author ?: "Unknown author")
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build(),
                )
                .build()
        }
        return Playlist(book.title, items)
    }
}
