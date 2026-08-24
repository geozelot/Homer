package com.geozelot.homer.data.library

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Its own file rather than a key in the settings store: this is work state, not a preference. It
 * changes several times a minute while a pass runs, and sharing the settings file would wake every
 * settings observer in the app on each of those writes.
 *
 * Losing the file to a truncated write costs at most a queued pass, which the user can ask for
 * again, so resetting beats being unable to read it ever again.
 */
private val Context.indexPassDataStore by preferencesDataStore(
    name = "homer_index_passes",
    corruptionHandler = ReplaceFileCorruptionHandler {
        Log.w("HomerScan", "pass queue was corrupt; starting empty", it)
        emptyPreferences()
    },
)

/**
 * The persisted queue of requested [IndexPass]es.
 *
 * Persisted because the request has to outlive both the app and the worker: WorkManager re-runs a
 * worker the system stopped, and it is the surviving token that tells the new run what it was in
 * the middle of. Every mutation is a read-modify-write inside DataStore's own transform, so two
 * taps at once cannot lose a request between them.
 */
@Singleton
class IndexPassStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Everything requested — queued or running — in run order. */
    val pending: Flow<List<PassRequest>> =
        context.indexPassDataStore.data.map { PassQueue.pending(it[KEY_PASSES].orEmpty()) }

    /** Adds a request, absorbing it into an equivalent one already waiting. */
    suspend fun request(request: PassRequest) {
        context.indexPassDataStore.edit {
            it[KEY_PASSES] = PassQueue.request(it[KEY_PASSES].orEmpty(), request)
        }
    }

    /** The next pass to run, or null when nothing is left. */
    suspend fun next(): PassRequest? =
        PassQueue.next(context.indexPassDataStore.data.first()[KEY_PASSES].orEmpty())

    /**
     * Drops a pass that ran to a conclusion.
     *
     * Deliberately not called when a pass is *stopped*: the token staying is what makes the next
     * run resume it.
     */
    suspend fun done(request: PassRequest) {
        context.indexPassDataStore.edit {
            it[KEY_PASSES] = PassQueue.done(it[KEY_PASSES].orEmpty(), request)
        }
    }

    /** Forgets everything requested — what Stop means, over and above cancelling the worker. */
    suspend fun clear() {
        context.indexPassDataStore.edit { it.remove(KEY_PASSES) }
    }

    private companion object {
        val KEY_PASSES = stringSetPreferencesKey("requested_passes")
    }
}
