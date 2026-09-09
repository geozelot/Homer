package com.geozelot.homer.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How long the sleep timer has left, where anything can read it.
 *
 * ## Why this exists rather than a getter on the connection
 *
 * The countdown is owned by [SleepTimer], which lives inside [PlaybackConnection] in the app. The
 * notification is built by [PlaybackService]. They are the same process but neither may hold the
 * other: the connection exists to drive a controller that connects TO the service, and injecting it
 * back into the service closes that loop.
 *
 * A singleton holding one number breaks it. The connection writes; the service reads. Nothing else
 * is shared, and neither side learns anything about the other.
 */
@Singleton
class SleepTimerState @Inject constructor() {

    private val _remainingMs = MutableStateFlow<Long?>(null)

    /** Milliseconds until the timer fires, or null when no countdown is running. */
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    fun set(remainingMs: Long?) {
        _remainingMs.value = remainingMs
    }
}
