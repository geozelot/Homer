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

    /** Full scan + cover enrichment (+ Tier-3 publish). Supersedes any running index job. */
    fun scan() = enqueue(scan = true, policy = ExistingWorkPolicy.REPLACE)

    /** Cover enrichment only (on library open); no-op if an index job is already queued. */
    fun enrichCovers() = enqueue(scan = false, policy = ExistingWorkPolicy.KEEP)

    private fun enqueue(scan: Boolean, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<LibraryIndexWorker>()
            .setInputData(workDataOf(LibraryIndexWorker.KEY_SCAN to scan))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(LibraryIndexWorker.WORK_NAME, policy, request)
    }
}
