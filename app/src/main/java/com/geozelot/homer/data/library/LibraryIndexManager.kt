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

    /** Everyday scan: find added/removed books + fetch missing covers. Preserves cached art. */
    fun scan() = enqueue(scan = true, resetCovers = false, policy = ExistingWorkPolicy.REPLACE)

    /** Deep re-scan: rebuild the library and re-fetch all cover art. */
    fun fullScan() = enqueue(scan = true, resetCovers = true, policy = ExistingWorkPolicy.REPLACE)

    /** Cover-only pass for books still missing art (on open); keeps a running index job. */
    fun fetchMissingCovers() = enqueue(scan = false, resetCovers = false, policy = ExistingWorkPolicy.KEEP)

    /** Re-fetch cover art for every book (resets attempts + cached art). */
    fun refreshCovers() = enqueue(scan = false, resetCovers = true, policy = ExistingWorkPolicy.REPLACE)

    private fun enqueue(scan: Boolean, resetCovers: Boolean, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<LibraryIndexWorker>()
            .setInputData(
                workDataOf(
                    LibraryIndexWorker.KEY_SCAN to scan,
                    LibraryIndexWorker.KEY_RESET_COVERS to resetCovers,
                ),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(LibraryIndexWorker.WORK_NAME, policy, request)
    }
}
