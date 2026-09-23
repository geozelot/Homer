package com.geozelot.homer.data.library

import android.net.Uri
import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.download.DownloadStorage
import com.geozelot.homer.data.runCatchingUnlessCancelled
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.WebDavClient
import com.geozelot.homer.di.Authed
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Gets a supplementary PDF onto the device, and keeps it there.
 *
 * ## It lands in the downloads area, beside the audio
 *
 * A document's stored path is library-root-relative, so `Author/Book/Booklet.pdf` mirrors into
 * `downloads/Author/Book/Booklet.pdf` — the same tree the book's audio uses. Three things follow
 * from that and all three are wanted: removing a book's download takes its own booklet with it;
 * clearing every download clears these too; and a series-level map, which is not any one book's,
 * survives a single book being removed because it never lived under that book's folder.
 *
 * ## Fetched on open, not with the book
 *
 * The audio download counts its files and resumes from that count, so slipping an extra file into
 * that loop would shift the resume index of every part-downloaded book on the device the moment
 * the app updated. A booklet is one small file that arrives in a second; the book is a gigabyte.
 * They do not belong in the same progress bar.
 *
 * What a reader actually needs — the booklet being there on a plane — is covered instead by the
 * file STAYING once fetched. Open it once at home and it is offline from then on.
 */
@Singleton
class BookDocumentStore @Inject constructor(
    private val downloadStorage: DownloadStorage,
    private val credentialStore: CredentialStore,
    private val librarySettings: LibrarySettings,
    private val webDavClient: WebDavClient,
    @Authed private val client: OkHttpClient,
) {
    /** The local copy, or null if this document has never been opened on this device. */
    suspend fun localUri(path: String): Uri? = downloadStorage.uri(path)

    /**
     * The local copy, fetching it first if it isn't here yet.
     *
     * A [Result] rather than a nullable, because the reader has two different things to say: "not
     * on this device and the network is gone" is a sentence a reader can act on, and "we could not
     * open it" is not.
     */
    suspend fun obtain(path: String): Result<Uri> {
        downloadStorage.uri(path)?.let { return Result.success(it) }
        return runCatchingUnlessCancelled {
            val credentials = credentialStore.awaitCredentials()
                ?: throw IOException("not signed in")
            val libraryRoot = librarySettings.libraryRoot.first()
            val request = Request.Builder()
                .url(webDavClient.urlFor(credentials, libraryRoot, path))
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $path")
                    val body = response.body ?: throw IOException("empty body for $path")
                    // Streamed into place by the storage area, which writes a `.part` sibling and
                    // moves it — so a connection dropped halfway can never leave a truncated file
                    // that looks downloaded and then fails to open for ever.
                    downloadStorage.writeStream(path) { output ->
                        body.byteStream().use { it.copyTo(output) }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "document fetch failed for $path", it) }
    }

    private companion object {
        const val TAG = "HomerDocs"
    }
}
