package com.geozelot.homer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.download.DownloadStorage
import com.geozelot.homer.data.library.BookCover
import com.geozelot.homer.data.library.applyOverride
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Resolves a book into an ordered playlist of streamable [MediaItem]s. */
class PlaylistResolver @Inject constructor(
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
    private val credentialStore: CredentialStore,
    private val webDavClient: WebDavClient,
    private val downloadDao: DownloadDao,
    private val downloadStorage: DownloadStorage,
    private val bookOverrideDao: BookOverrideDao,
    private val librarySettings: LibrarySettings,
) {
    data class Playlist(
        val bookTitle: String,
        val coverModel: Any?,
        val items: List<MediaItem>,
        /** True when items point at downloaded local files rather than streamed URLs. */
        val offline: Boolean,
    )

    suspend fun resolve(bookId: String): Playlist? {
        val credentials = credentialStore.credentials.value ?: return null
        // Apply user overrides so the now-playing title/author match the library.
        val book = bookDao.findById(bookId)?.applyOverride(bookOverrideDao.findById(bookId)) ?: return null
        val files = audioFileDao.findForBook(bookId)
        if (files.isEmpty()) return null

        val libraryRoot = librarySettings.libraryRoot.first()
        val coverModel = BookCover.model(book, credentials, webDavClient, libraryRoot)
        // Local cached cover (custom preferred) as a file://|content:// URI so the media
        // notification shows it on every chapter (its loader can't authenticate remote WebDAV).
        val artworkUri = (book.customCoverPath ?: book.localCoverPath)?.let { value ->
            if (value.startsWith("content://") || value.startsWith("file://")) {
                Uri.parse(value)
            } else {
                Uri.fromFile(java.io.File(value)) // legacy pre-relocation path
            }
        }

        // Play from local files when the book is fully downloaded; otherwise stream.
        val offline = downloadDao.findByBookId(bookId)?.status == DownloadStatus.DONE

        val items = files.map { file ->
            // Play the downloaded copy when present (file:// or content:// depending on the
            // storage backend); otherwise stream. `map` is inline, so the suspend uri() is fine.
            val localUri = if (offline) downloadStorage.uri(file.relativePath) else null
            val url = localUri?.toString()
                ?: webDavClient.urlFor(credentials, libraryRoot, file.relativePath).toString()
            val chapterTitle = file.fileName.substringBeforeLast('.')
            MediaItem.Builder()
                .setMediaId(file.relativePath)
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(chapterTitle)
                        .setAlbumTitle(book.title)
                        .setArtist(book.author ?: "Unknown author")
                        .setArtworkUri(artworkUri)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build(),
                )
                .build()
        }
        return Playlist(book.title, coverModel, items, offline)
    }
}
