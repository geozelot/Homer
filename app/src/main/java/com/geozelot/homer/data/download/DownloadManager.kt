package com.geozelot.homer.data.download

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.entity.DownloadEntity
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.webdav.WebDavClient
import com.geozelot.homer.di.Authed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Downloads a book's audio files to app-private storage for offline playback, over the
 * authenticated OkHttp client. Sequential per book, progress tracked file-granular in Room.
 *
 * v1 scope: a plain background coroutine — downloads run while the app process is alive but
 * do not survive process death (WorkManager is a later hardening step), and files are not
 * encrypted at rest (internal storage is already sandboxed).
 */
@Singleton
class DownloadManager @Inject constructor(
    @Authed private val client: OkHttpClient,
    private val webDavClient: WebDavClient,
    private val credentialStore: CredentialStore,
    private val audioFileDao: AudioFileDao,
    private val downloadDao: DownloadDao,
    private val storage: DownloadStorage,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())

    /** Fire-and-forget; no-op if this book is already downloading. */
    fun download(bookId: String) {
        if (!inFlight.add(bookId)) return
        scope.launch {
            try {
                runDownload(bookId)
            } catch (e: Exception) {
                Log.w(TAG, "download failed for $bookId", e)
                storage.deleteBook(bookId) // drop the partial
                downloadDao.upsert(DownloadEntity(bookId, DownloadStatus.FAILED, 0, 0, now()))
            } finally {
                inFlight.remove(bookId)
            }
        }
    }

    /** Removes a book's downloaded files and its download record. */
    fun delete(bookId: String) {
        scope.launch {
            storage.deleteBook(bookId)
            downloadDao.delete(bookId)
        }
    }

    private suspend fun runDownload(bookId: String) {
        val credentials = credentialStore.credentials.value ?: return
        val files = audioFileDao.findForBook(bookId)
        if (files.isEmpty()) return

        downloadDao.upsert(DownloadEntity(bookId, DownloadStatus.DOWNLOADING, 0, files.size, now()))
        files.forEachIndexed { index, file ->
            coroutineContext.ensureActive()
            val dest = storage.fileFor(file.relativePath)
            dest.parentFile?.mkdirs()
            val request = Request.Builder().url(webDavClient.urlFor(credentials, file.relativePath)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${file.relativePath}")
                val body = response.body ?: throw IOException("empty body for ${file.relativePath}")
                body.byteStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
            }
            downloadDao.upsert(DownloadEntity(bookId, DownloadStatus.DOWNLOADING, index + 1, files.size, now()))
        }
        downloadDao.upsert(DownloadEntity(bookId, DownloadStatus.DONE, files.size, files.size, now()))
        Log.i(TAG, "downloaded $bookId (${files.size} files)")
    }

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val TAG = "HomerDownload"
    }
}
