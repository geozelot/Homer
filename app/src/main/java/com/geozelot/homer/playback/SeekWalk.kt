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
 *
 * ## A backward crossing lands short of the end, never on it
 *
 * The neighbour's length is a MEASUREMENT, and a header estimate can run a fraction of a second
 * past what the stream actually decodes. Landing inside that fraction puts the player past the real
 * end, it clamps, it auto-advances — and a skip BACK has carried the listener forward, to the start
 * of the chapter they were in. So a landing reached by crossing backwards is held at least
 * [END_MARGIN_MS] clear of the chapter's end. A second is well inside what a skip-back is for.
 */
internal fun seekTarget(
    index: Int,
    positionMs: Long,
    deltaMs: Long,
    chapterCount: Int,
    durationAt: (Int) -> Long,
): SeekTarget {
    // No chapters, no walk: asking [durationAt] anything here would be asking about a chapter that
    // does not exist, which is exactly how the caller used to crash.
    if (chapterCount <= 0) return SeekTarget(0, (positionMs + deltaMs).coerceAtLeast(0L))
    val start = index.coerceIn(0, chapterCount - 1)
    var at = start
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
    val end = when {
        duration <= 0 -> Long.MAX_VALUE
        at < start -> (duration - END_MARGIN_MS).coerceAtLeast(0L)
        else -> duration
    }
    return SeekTarget(at, target.coerceIn(0L, end))
}

/** How far clear of a chapter's measured end a backward crossing lands. See [seekTarget]. */
internal const val END_MARGIN_MS = 1_000L
