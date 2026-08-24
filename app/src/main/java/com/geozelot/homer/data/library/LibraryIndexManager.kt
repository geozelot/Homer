package com.geozelot.homer.data.library

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.geozelot.homer.data.settings.PlaybackSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues the foreground [LibraryIndexWorker] so scanning and cover enrichment run
 * independently of app state (survive backgrounding/kill, progress-notified). A manual
 * [scan] replaces any running index job; the on-open [enrichCovers] keeps an existing one.
 */
@Singleton
class LibraryIndexManager @Inject constructor(
    @ApplicationContext context: Context,
    playbackSettings: PlaybackSettings,
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Mirrored into a field rather than read when enqueuing, because [enqueue] is not suspending
     * and launching a coroutine to read it would leave a window where a second tap enqueues a
     * second job before `busy` has turned true.
     */
    @Volatile private var wifiOnly = false

    init {
        playbackSettings.wifiOnlyDownloads
            .onEach { wifiOnly = it }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }

    /** Which long pass the worker is in the middle of, and how far through. */
    enum class IndexPhase { COVERS, LENGTHS }

    data class IndexProgress(val phase: IndexPhase, val done: Int, val total: Int)

    /**
     * Progress of the running index job, or null when nothing is running. Read off WorkManager
     * rather than held in memory so it survives the settings screen being closed and reopened —
     * the work outlives the UI, and a measure pass over a whole library outlives it by a lot.
     */
    val progress: Flow<IndexProgress?> =
        workManager.getWorkInfosForUniqueWorkFlow(LibraryIndexWorker.WORK_NAME).map { infos ->
            val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: return@map null
            val phase = when (running.progress.getString(LibraryIndexWorker.KEY_PHASE)) {
                LibraryIndexWorker.PHASE_LENGTHS -> IndexPhase.LENGTHS
                LibraryIndexWorker.PHASE_COVERS -> IndexPhase.COVERS
                else -> return@map null // running, but not yet in a phase that reports
            }
            IndexProgress(
                phase = phase,
                done = running.progress.getInt(LibraryIndexWorker.KEY_DONE, 0),
                total = running.progress.getInt(LibraryIndexWorker.KEY_TOTAL, 0),
            )
        }

    /**
     * True while an index job is queued OR running. Broader than [progress], which only reports
     * once a pass reaches a phase that reports — every action enqueues with REPLACE, so the UI has
     * to stop accepting taps from the moment one is queued, not from the first progress update.
     */
    val busy: Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(LibraryIndexWorker.WORK_NAME).map { infos ->
            infos.any { !it.state.isFinished }
        }

    /**
     * True while a pass is queued but not running — the constraints are not met yet.
     *
     * Without this the wifi-only rule below is indistinguishable from a hang: the work sits in
     * ENQUEUED, [busy] is true, and nothing ever reports progress. The UI says what it is waiting
     * for instead.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val waiting: Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(LibraryIndexWorker.WORK_NAME)
            .map { infos ->
                infos.any { it.state == WorkInfo.State.ENQUEUED } &&
                    infos.none { it.state == WorkInfo.State.RUNNING }
            }
            .distinctUntilChanged()
            .transformLatest { blocked ->
                // Every pass is ENQUEUED for a moment before it starts, so claiming immediately
                // would flash "waiting" on every single tap. Only the claim is delayed; clearing
                // it is instant, so the notice never outlives the wait it describes.
                if (!blocked) emit(false) else { delay(WAITING_GRACE_MS); emit(true) }
            }

    /**
     * Stops whatever pass is queued or running.
     *
     * Every pass here is resumable — a scan re-crawls, a cover or length pass skips everything
     * already done — so stopping costs only the file in flight, and starting again picks up where
     * it left off. That is also why there is no separate pause: the work list IS the state, and a
     * second flag beside it would only drift.
     */
    fun cancel() {
        workManager.cancelUniqueWork(LibraryIndexWorker.WORK_NAME)
    }

    /** Everyday scan: incremental (skips unchanged subtrees) + fetch missing covers. */
    fun scan() = enqueue(scan = true, incremental = true, resetCovers = false, policy = ExistingWorkPolicy.REPLACE)

    /** Deep re-scan: full crawl (re-reads everything) + re-fetch all cover art. */
    fun fullScan() = enqueue(scan = true, incremental = false, resetCovers = true, policy = ExistingWorkPolicy.REPLACE)

    /** Cover-only pass for books still missing art (on open); keeps a running index job. */
    fun fetchMissingCovers() = enqueue(scan = false, incremental = false, resetCovers = false, policy = ExistingWorkPolicy.KEEP)

    /** Re-fetch cover art for every book (resets attempts + cached art). */
    fun refreshCovers() = enqueue(scan = false, incremental = false, resetCovers = true, policy = ExistingWorkPolicy.REPLACE)

    /**
     * Measure every book that still has no total length.
     *
     * Deliberately its own action rather than part of [scan] or [fullScan]. A crawl reads names,
     * sizes and ETags — one cheap request per folder — whereas a length needs a ranged probe of
     * every audio file, so a library of a few hundred books is thousands of requests. Folding that
     * into the everyday scan would make the routine action the expensive one; this way the cost is
     * asked for.
     */
    fun measureDurations() =
        enqueue(scan = false, incremental = false, resetCovers = false, measure = true, policy = ExistingWorkPolicy.REPLACE)

    private fun enqueue(
        scan: Boolean,
        incremental: Boolean,
        resetCovers: Boolean,
        policy: ExistingWorkPolicy,
        measure: Boolean = false,
    ) {
        val request = OneTimeWorkRequestBuilder<LibraryIndexWorker>()
            .setInputData(
                workDataOf(
                    LibraryIndexWorker.KEY_SCAN to scan,
                    LibraryIndexWorker.KEY_INCREMENTAL to incremental,
                    LibraryIndexWorker.KEY_RESET_COVERS to resetCovers,
                    LibraryIndexWorker.KEY_MEASURE to measure,
                ),
            )
            // Follows the same preference as downloads. A length pass is thousands of requests
            // over the whole library, so honouring "Wi-Fi only" matters more here than it does for
            // a single book — this used to run on mobile data regardless of the setting.
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(LibraryIndexWorker.WORK_NAME, policy, request)
    }

    private companion object {
        /** Long enough to cover the ordinary ENQUEUED → RUNNING hop, short enough to be useful. */
        const val WAITING_GRACE_MS = 2_500L
    }
}
