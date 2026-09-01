package com.geozelot.homer.data.library

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.geozelot.homer.data.settings.PlaybackSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests maintenance passes over the library and reports what they are doing.
 *
 * A request is a token in [IndexPassStore]; one foreground [LibraryIndexWorker] drains the queue.
 * That indirection is the point: the passes used to share a WorkManager unique name enqueued with
 * `REPLACE`, so asking for lengths cancelled a cover pass in flight and every action had to be
 * greyed out while any one of them ran. Nothing here uses `REPLACE`, and asking for a second pass
 * queues it.
 */
@Singleton
class LibraryIndexManager @Inject constructor(
    @ApplicationContext context: Context,
    private val playbackSettings: PlaybackSettings,
    private val passes: IndexPassStore,
    private val maintenance: LibraryMaintenance,
) {
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val work = workManager.getWorkInfosForUniqueWorkFlow(LibraryIndexWorker.WORK_NAME)

    init {
        // The invariant, self-healing: if something is requested and no worker is queued or
        // running, enqueue one. It closes the window where a request arrives just as the drain
        // ends — the worker is still RUNNING, so `KEEP` drops the enqueue, and without this the
        // token would sit there with nothing to drain it. It also picks the queue back up after a
        // failed run and after a cold start.
        //
        // The Wi-Fi preference is part of the combine rather than read at enqueue time so that a
        // pass resumed on launch cannot be enqueued before its value is known — the resumed pass is
        // the length sweep, the one where thousands of requests on mobile data actually matter.
        combine(passes.pending, work, playbackSettings.wifiOnlyDownloads) { pending, infos, wifiOnly ->
            wifiOnly.takeIf { pending.isNotEmpty() && infos.none { info -> !info.state.isFinished } }
        }
            .distinctUntilChanged()
            .onEach { if (it != null) enqueue(it) }
            .launchIn(scope)
    }

    /** [books]/[bookTotal] are only meaningful for [IndexPass.LENGTHS]; zero elsewhere. */
    data class IndexProgress(
        val pass: IndexPass,
        val done: Int,
        val total: Int,
        val books: Int = 0,
        val bookTotal: Int = 0,
    )

    /**
     * The pass running right now and how far through it is, or null when nothing is running.
     *
     * Read off WorkManager rather than held in memory so it survives the screen being closed and
     * reopened — the work outlives the UI, and a measure pass over a whole library outlives it by
     * a lot. A [IndexProgress.total] of zero means the pass has started but has no count to report
     * yet.
     */
    val progress: Flow<IndexProgress?> = work.map { infos ->
        val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: return@map null
        val pass = IndexPass.of(running.progress.getString(LibraryIndexWorker.KEY_PASS)) ?: return@map null
        IndexProgress(
            pass = pass,
            done = running.progress.getInt(LibraryIndexWorker.KEY_DONE, 0),
            total = running.progress.getInt(LibraryIndexWorker.KEY_TOTAL, 0),
            books = running.progress.getInt(LibraryIndexWorker.KEY_BOOKS, 0),
            bookTotal = running.progress.getInt(LibraryIndexWorker.KEY_BOOK_TOTAL, 0),
        )
    }

    /** Which pass is running, for a row that only needs to know that much. */
    val running: Flow<IndexPass?> = progress.map { it?.pass }

    /**
     * Every pass that has been asked for, whether it is running or still waiting its turn.
     *
     * This is what a row shows its own state from. Nothing derives "disable everything" from it
     * any more — a queued pass is a promise, not a reason to refuse the next request.
     */
    val queued: Flow<Set<IndexPass>> = passes.pending.map { requests -> requests.map { it.pass }.toSet() }

    /** True while anything at all is outstanding — what Stop is offered on. */
    val active: Flow<Boolean> = combine(passes.pending, work) { pending, infos ->
        pending.isNotEmpty() || infos.any { !it.state.isFinished }
    }.distinctUntilChanged()

    /**
     * True while a pass is queued but not running — the constraints are not met yet.
     *
     * Without this the wifi-only rule below is indistinguishable from a hang: the work sits in
     * ENQUEUED, something is plainly outstanding, and nothing ever reports progress. The UI says
     * what it is waiting for instead.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val waiting: Flow<Boolean> = work
        .map { infos ->
            infos.any { it.state == WorkInfo.State.ENQUEUED } &&
                infos.none { it.state == WorkInfo.State.RUNNING }
        }
        .distinctUntilChanged()
        .transformLatest { blocked ->
            // Every pass is ENQUEUED for a moment before it starts, so claiming immediately would
            // flash "waiting" on every single tap. Only the claim is delayed; clearing it is
            // instant, so the notice never outlives the wait it describes.
            if (!blocked) emit(false) else { delay(WAITING_GRACE_MS); emit(true) }
        }

    /**
     * Asks for [pass], queued behind whatever is already running.
     *
     * The token is written before the worker is enqueued, so a worker that starts immediately
     * cannot find an empty queue.
     */
    fun request(pass: IndexPass, deep: Boolean = false) {
        scope.launch {
            // Refused here, not merely hidden in the UI. `fetchMissingCovers()` runs on every app
            // open, so a reader device would otherwise start a pass on each launch however carefully
            // the screens were written — and the whole point of the rule is that the requests never
            // reach the server.
            if (pass.needsMaintainer && !maintenance.maintainsNow()) {
                Log.i(TAG, "not running the $pass pass: this device reads an index it cannot write")
                return@launch
            }
            passes.request(PassRequest(pass, deep))
            enqueue(playbackSettings.wifiOnlyDownloads.first())
        }
    }

    /**
     * Everyday scan: find what has arrived or gone, then finish the job on whatever is new.
     *
     * An incremental crawl (skipping unchanged subtrees on their ETag), then covers for the books
     * that have none, then lengths for the files that have never been measured. One action, because
     * "the library changed" is one thought — asking somebody to run three passes in the right order
     * is asking them to hold a model of the pass queue.
     *
     * ## Lengths belong here now, and the reason it used to be otherwise still stands
     *
     * A length needs a ranged probe of every audio file, so a library of a few hundred books is
     * thousands of requests — which is why this pass was kept out of the everyday scan, so the
     * routine action would not be the expensive one.
     *
     * What makes it safe is that the shallow pass only touches files it has NEVER tried
     * (`durationMs == null && !durationAttempted`). On a settled library that set is empty and this
     * costs nothing; on a library that just grew by three books it is three books. The expensive
     * case — re-arming files whose probe failed — is still asked for explicitly, by
     * [remeasureDurations].
     */
    fun scan() {
        request(IndexPass.BOOKS)
        request(IndexPass.ARTWORK)
        request(IndexPass.LENGTHS)
    }

    /** Deep re-scan: full crawl re-reading everything, and all cover art fetched again. */
    fun fullScan() {
        request(IndexPass.BOOKS, deep = true)
        request(IndexPass.ARTWORK, deep = true)
    }

    /** Cover pass for books still missing art — what the library screen asks for on open. */
    fun fetchMissingCovers() = request(IndexPass.ARTWORK)

    /** Re-fetch cover art for every book (drops what is cached first). */
    fun refreshCovers() = request(IndexPass.ARTWORK, deep = true)

    /**
     * Measure every book that still has no total length.
     *
     * Deliberately its own pass rather than part of a scan. A crawl reads names, sizes and ETags —
     * one cheap request per folder — whereas a length needs a ranged probe of every audio file, so
     * a library of a few hundred books is thousands of requests. Folding that into the everyday
     * scan would make the routine action the expensive one; this way the cost is asked for.
     */
    fun measureDurations() = request(IndexPass.LENGTHS)

    /**
     * As [measureDurations], but re-arms the files whose probe failed before.
     *
     * A stored length is never discarded — a duration is a fact about bytes and re-reading it can
     * only cost time — so this widens the pass to what was tried and could not be measured.
     */
    fun remeasureDurations() = request(IndexPass.LENGTHS, deep = true)

    /**
     * Stops everything queued or running.
     *
     * The queue is cleared *first*: the reconciler above would otherwise see a pending request with
     * no worker and start one straight back up. Every pass resumes where it stopped when it is next
     * asked for — a scan re-crawls, a cover or length pass skips what is already done — which is
     * why this is a plain Stop and not a pause. A second flag beside the queue and WorkManager
     * would only be a third copy of the same state.
     */
    fun cancel() {
        scope.launch {
            passes.clear()
            workManager.cancelUniqueWork(LibraryIndexWorker.WORK_NAME)
        }
    }

    /**
     * Enqueues a drain. Enqueuing twice for one request is deliberately harmless — the queue
     * absorbs a duplicate and `KEEP` leaves the run in flight alone — so nothing here has to guard
     * against a second tap.
     */
    private fun enqueue(wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<LibraryIndexWorker>()
            // Follows the same preference as downloads. A length pass is thousands of requests over
            // the whole library, so honouring "Wi-Fi only" matters more here than it does for a
            // single book — this used to run on mobile data regardless of the setting.
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        // KEEP, never REPLACE: a worker already draining the queue will reach this request too, and
        // replacing it would cancel the pass in flight — the whole defect this queue removes.
        workManager.enqueueUniqueWork(LibraryIndexWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val TAG = "HomerScan"

        /** Long enough to cover the ordinary ENQUEUED → RUNNING hop, short enough to be useful. */
        const val WAITING_GRACE_MS = 2_500L
    }
}
