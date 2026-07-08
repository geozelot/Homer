package com.geozelot.homer.ui

import java.util.concurrent.TimeUnit

/** Compact human duration, e.g. "12h 34m" or "45m" (minute resolution). */
fun formatCompactDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
