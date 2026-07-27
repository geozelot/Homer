package com.geozelot.homer.data.storage

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Enqueues the foreground [StorageMigrationWorker] to move local data to a new storage folder. */
@Singleton
class StorageMigrationManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    /** [source]/[target] are storage-folder Uri strings (null = the default app-external area). */
    fun migrate(source: String?, target: String?, overwrite: Boolean) {
        val request = OneTimeWorkRequestBuilder<StorageMigrationWorker>()
            .setInputData(
                workDataOf(
                    StorageMigrationWorker.KEY_SOURCE to source,
                    StorageMigrationWorker.KEY_TARGET to target,
                    StorageMigrationWorker.KEY_OVERWRITE to overwrite,
                ),
            )
            .build()
        // KEEP: a migration in flight must not be superseded by another tap.
        workManager.enqueueUniqueWork(StorageMigrationWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
