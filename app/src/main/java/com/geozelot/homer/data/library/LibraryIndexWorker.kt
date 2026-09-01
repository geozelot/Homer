package com.geozelot.homer.data.library

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.metadata.CoverEnricher
import com.geozelot.homer.data.metadata.DurationEnricher
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.sync.facet.LibraryIndexRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground (data-sync) worker that drains the queue of requested [IndexPass]es, so the work
 * survives the app being backgrounded or killed and shows a progress notification. It reuses
 * WorkManager's SystemForegroundService (data-sync type already declared in the manifest).
 *
 * It takes no input: the queue in [IndexPassStore] says what to do. That is what makes a pass
 * resumable — a run the system stops leaves its token behind, and WorkManager re-running the
 * worker picks up the same pass.
 */
@HiltWorker
class LibraryIndexWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val libraryRepository: LibraryRepository,
    private val coverEnricher: CoverEnricher,
    private val durationEnricher: DurationEnricher,
    private val bookDao: BookDao,
    private val libraryIndex: LibraryIndexRepository,
    private val librarySettings: LibrarySettings,
    private val passes: IndexPassStore,
    private val maintenance: LibraryMaintenance,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ensureChannel()
        var failed = false
        while (true) {
            val request = passes.next() ?: break
            try {
                runPass(request)
            } catch (e: CancellationException) {
                // Stopped, not finished. The token stays, so the pass resumes on the next run —
                // which is what lets an hours-long sweep complete across several sittings.
                throw e
            } catch (e: Exception) {
                // Dropped rather than left queued: a pass that cannot run now would otherwise be
                // retried immediately, for ever, with nothing to show for it. The user can ask
                // again, and the log says what happened.
                Log.w(TAG, "${request.pass} pass failed", e)
                failed = true
            }
            passes.done(request)
        }
        return if (failed) Result.failure() else Result.success()
    }

    /**
     * One pass, start to finish.
     *
     * The order passes run in is [IndexPass]'s own; this only says what each one does. Each ends by
     * publishing the facets it could have changed, because the alternative — publishing only after
     * a crawl — meant an hour-long duration sweep shared nothing until something later triggered a
     * scan.
     */
    private suspend fun runPass(request: PassRequest) {
        when (request.pass) {
            IndexPass.BOOKS -> {
                report(request.pass)
                setForegroundSafely(foregroundInfo("Scanning library…", 0, 0))
                // Re-posted as the crawl walks, on the same throttle as the other two passes.
                //
                // Not only for the progress, though a crawl of a large library sat on a static
                // "Scanning library…" for minutes. A notification posted before the user has
                // granted POST_NOTIFICATIONS is dropped and does not appear retroactively, and on a
                // first run with a share account the crawl is enqueued in the same frame as the
                // permission dialog — so the one post above could be made while the dialog was
                // still on screen, and that crawl would then show nothing at all however long it
                // ran. Re-posting means it appears as soon as the permission lands.
                coroutineScope {
                    val progress = launch {
                        var lastNotifyMs = 0L
                        libraryRepository.scanState.collect { state ->
                            if (state !is ScanState.Scanning) return@collect
                            val now = System.currentTimeMillis()
                            if (now - lastNotifyMs < PROGRESS_NOTIFY_INTERVAL_MS) return@collect
                            lastNotifyMs = now
                            setForegroundSafely(
                                foregroundInfo(
                                    text = "Scanning library…",
                                    done = 0,
                                    total = 0,
                                    detail = "${state.booksFound} book(s) in ${state.directoriesVisited} folder(s)",
                                ),
                            )
                            report(request.pass, books = state.booksFound)
                        }
                    }
                    // Deep means a full crawl: every folder re-read rather than unchanged subtrees
                    // skipped on their ETag. Only a full crawl may stamp the marker that authorises
                    // another device to prune.
                    try {
                        libraryRepository.scan(incremental = !request.deep)
                    } finally {
                        progress.cancel()
                    }
                }
                publish("Updating shared library…")
            }

            IndexPass.ARTWORK -> {
                // A reader device gets the shared cache and nothing else — and a DEEP artwork pass
                // is entirely about re-creating art, so for a reader there is nothing to do at all.
                val readerOnly = !maintenance.maintainsNow()
                if (readerOnly && request.deep) return
                if (request.deep) libraryRepository.resetCoverArt()
                // Nothing to fetch: finish without ever showing a notification.
                if (coverEnricher.pendingCount() == 0) return
                report(request.pass)
                // Throttle the notification: one update per book overran Android's ~5/s notify
                // budget on a large library, so the framework shed the updates and flooded the log
                // with "rate limit exceeded" instead of showing progress. Once a second is plenty
                // for a progress bar, and the final call always lands so it never ends mid-way.
                var lastNotifyMs = 0L
                coverEnricher.enrich(sharedOnly = readerOnly) { done, total ->
                    val now = System.currentTimeMillis()
                    if (done >= total || now - lastNotifyMs >= PROGRESS_NOTIFY_INTERVAL_MS) {
                        lastNotifyMs = now
                        setForegroundSafely(foregroundInfo("Fetching covers…", done, total))
                        report(request.pass, done, total)
                    }
                }
                // Cover art lands in the shared cache, and `derived` records which books have one
                // — neither of which a reader has anything to say about.
                if (!readerOnly) publish("Updating shared library…")
            }

            IndexPass.LENGTHS -> {
                // A stored length is never discarded — a duration is a fact about bytes. Deep only
                // re-arms what was tried and could not be measured, plus the tag read that rides
                // along with it.
                if (request.deep) libraryRepository.rearmDurations()
                val pending = bookDao.idsWithoutDuration()
                Log.i(TAG, "measuring lengths for ${pending.size} book(s)")
                report(request.pass)
                var lastMeasureNotifyMs = 0L
                durationEnricher.measureAll(pending) { p ->
                    val now = System.currentTimeMillis()
                    if (p.files >= p.fileTotal || now - lastMeasureNotifyMs >= PROGRESS_NOTIFY_INTERVAL_MS) {
                        lastMeasureNotifyMs = now
                        // Both counts: books say how much is left, files say it is still moving.
                        // One 1020-file book makes either number alone misleading.
                        setForegroundSafely(
                            foregroundInfo(
                                text = "Measuring lengths",
                                done = p.files,
                                total = p.fileTotal,
                                detail = "Book ${p.books} of ${p.bookTotal} · ${p.files} of ${p.fileTotal} files",
                            ),
                        )
                        report(request.pass, p.files, p.fileTotal, p.books, p.bookTotal)
                    }
                }
                publish("Updating shared library…")
            }
        }
    }

    /** Publishes this device's view, if the user has sharing on (a read-only share cannot). */
    private suspend fun publish(text: String) {
        if (!librarySettings.sharedCatalogEnabled.first()) return
        setForegroundSafely(foregroundInfo(text, 0, 0))
        libraryIndex.push()
    }

    /**
     * Mirrors the notification's progress into WorkManager, so the library screen can show the same
     * thing inline. A long measure pass is otherwise invisible unless the user pulls down the
     * notification shade — and it is the one pass that can run for many minutes.
     *
     * Called once with no counts as each pass starts, so the row for the running pass can say so
     * before there is anything to count.
     */
    private suspend fun report(
        pass: IndexPass,
        done: Int = 0,
        total: Int = 0,
        books: Int = 0,
        bookTotal: Int = 0,
    ) {
        setProgress(
            workDataOf(
                KEY_PASS to pass.name,
                KEY_DONE to done,
                KEY_TOTAL to total,
                KEY_BOOKS to books,
                KEY_BOOK_TOTAL to bookTotal,
            ),
        )
    }

    private suspend fun setForegroundSafely(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "setForeground failed; continuing as background worker", e)
        }
    }

    private fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Library", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun foregroundInfo(
        text: String,
        done: Int,
        total: Int,
        detail: String? = null,
    ): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Homer")
            .setContentText(detail ?: if (total > 0) "$text ${done}/$total" else text)
            .setOngoing(true)
            .setProgress(total, done, total == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    companion object {
        /** Progress reported back to the UI while the worker runs. */
        const val KEY_PASS = "pass"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_BOOKS = "books"
        const val KEY_BOOK_TOTAL = "book_total"
        const val WORK_NAME = "library-index"
        private const val TAG = "HomerScan"
        private const val CHANNEL_ID = "library"
        private const val NOTIF_ID = 42

        /** Minimum gap between progress notifications (the platform sheds updates above ~5/s). */
        private const val PROGRESS_NOTIFY_INTERVAL_MS = 1_000L
    }
}
