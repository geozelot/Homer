package com.geozelot.homer.playback

/**
 * Where a relative seek lands, when a book is several files and the delta does not fit in one.
 *
 * Pulled out of [PlaybackConnection] because a `MediaController` cannot be constructed in a unit
 * test — the same move `ShakeGesture` and `ListeningFold` made for the same reason. What is left
 * behind there is the call; what is here is every rule about where the finger ends up.
 */
internal data class SeekTarget(val index: Int, val positionMs: Long)

/**
 * Walks [deltaMs] from ([index], [positionMs]) across chapter boundaries.
 *
 * ## The leftover travels
 *
 * A seek used to clamp inside the chapter it started in. Thirty seconds back, ten seconds into a
 * chapter, landed on that chapter's first frame — losing the twenty seconds actually being reached
 * for, which sit at the END of the chapter before. The remainder crosses now, and lands as an
 * offset into whatever chapter it lands in.
 *
 * ## An unmeasured neighbour stops the walk
 *
 * Crossing needs the neighbour's length, and [durationAt] returns 0 for a chapter nothing has
 * measured yet. That is a hard stop rather than something to guess around: treating an unknown
 * length as zero would let a single skip sail through every unmeasured chapter to the start of the
 * book, where stopping at this chapter's edge is merely the old, unhelpful behaviour. A book gets
 * measured on its way to being played, so the good case is the common one.
 *
 * The forward walk stops at the last chapter for the same reason there is nowhere past it: the
 * end of the book is the end of the book, and a seek past it clamps there rather than wrapping.
 */
internal fun seekTarget(
    index: Int,
    positionMs: Long,
    deltaMs: Long,
    chapterCount: Int,
    durationAt: (Int) -> Long,
): SeekTarget {
    var at = index.coerceIn(0, (chapterCount - 1).coerceAtLeast(0))
    var target = positionMs + deltaMs

    while (target < 0 && at > 0) {
        val previous = durationAt(at - 1)
        if (previous <= 0) break
        at--
        target += previous
    }
    while (at < chapterCount - 1) {
        val here = durationAt(at)
        if (here <= 0 || target < here) break
        target -= here
        at++
    }

    val duration = durationAt(at)
    return SeekTarget(at, target.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE))
}
