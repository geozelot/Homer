package com.geozelot.homer.data.library

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
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
) {
    private val workManager = WorkManager.getInstance(context)

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
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(LibraryIndexWorker.WORK_NAME, policy, request)
    }
}
