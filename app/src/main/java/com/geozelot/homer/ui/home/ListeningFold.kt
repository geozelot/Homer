package com.geozelot.homer.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/**
 * When the Currently-listening panel is folded, and when it is not.
 *
 * Pulled out of the composable and made a plain object because the rules are the hard part and they
 * were unverifiable while they lived in a nested-scroll callback: the first attempt read correctly,
 * compiled, passed review, and did nothing on a device. Everything below is decided here and tested
 * in `ListeningFoldTest`; the composable only forwards events and reads [expanded].
 *
 * ## What it is driven by, and what it deliberately is NOT
 *
 * The version that failed accumulated `available.y` from `onPostScroll` — the slack left AFTER the
 * grid had taken what it wanted. That reads like the obvious signal for "the list can go no
 * further", and it is one dependency too many: a `LazyVerticalGrid` carries a stretch overscroll
 * effect that also participates in that chain, so the leftover a parent finally sees is not
 * something this code can reason about, and if it arrives as zero the pull never registers and there
 * is no way to tell from the source.
 *
 * So it is driven by the two things that cannot be argued with: the RAW pointer delta before anyone
 * has consumed anything, and whether the list is at its own top. Nothing here depends on who
 * consumes what.
 *
 * ## The rules, in full, because their asymmetry is the design
 *
 *  - **It starts expanded.** Every launch. The panel answers "where was I", which is the question
 *    somebody opening an audiobook app is most likely to have.
 *  - **It folds on any travel into the library** — drag or fling, every time, not just the first.
 *  - **It folds when search opens**, for the same reason.
 *  - **Nothing folds it by hand.** There is no collapse control, so the header is not one.
 *  - **It unfolds on a tap anywhere on the panel**, and
 *  - **on a deliberate pull while the library is at its top.** Travel counts only while the list can
 *    go no further up, so scrolling home is not itself a pull — the count starts over every time the
 *    list is away from the top.
 *  - **A fling can fold it but never unfold it.** Folding on momentum is harmless; unfolding on
 *    momentum means the panel opens itself, which is the one thing it must not do — and it is what
 *    stops a single flick from both scrolling home and opening the panel.
 */
internal class ListeningFold(expanded: Boolean = true) {

    /** Whether the panel is showing its full items. Compose state, so reading it recomposes. */
    var expanded: Boolean by mutableStateOf(expanded)
        private set

    /** Downward travel accumulated while the list has been sitting at its top. */
    private var pulled = 0f

    /**
     * How much travel is banked, for diagnostics only.
     *
     * Exposed because this mechanism has now been reported broken four times and cannot be
     * reproduced off a device: the only way to tell "the deltas never arrive" from "they arrive and a
     * condition refuses them" is to be able to print both. Nothing reads this to make a decision.
     */
    internal val pulledPx: Float get() = pulled

    /**
     * The library scrolled.
     *
     * [deltaY] is the RAW pointer movement before anything consumes it — positive when the finger
     * travels down the screen, i.e. back towards the top of the list. [atTop] is whether the list can
     * go no further up. [fromUser] separates a finger from a fling.
     *
     * **Nothing here is latched across a gesture, and that is the fix.** The version before this
     * decided at a gesture's first delta whether that whole gesture was allowed to unfold, and reset
     * the decision in `onPostFling` — a suspend callback that is skipped whenever the fling coroutine
     * is cancelled, which is what happens every time a finger comes down again before the previous
     * fling has settled. Miss it once and the flag stayed set for the life of the screen, so pulling
     * never worked again and nothing about the code looked wrong. Every decision is now taken from
     * the state in front of it.
     *
     * The cost, and it is worth naming: a slow drag that travels to the top and keeps going will
     * open the panel within that same gesture, where before it would have been refused until the
     * finger lifted. A FLING home still cannot, which is the case that actually mattered — that is
     * the one that would have scrolled home and opened the panel off a single flick.
     */
    fun onScroll(deltaY: Float, atTop: Boolean, fromUser: Boolean, threshold: Float) {
        // Folding first, and unconditionally: any travel into the library folds the panel, whether
        // the finger is still down or the list is coasting.
        if (deltaY < 0f) {
            expanded = false
            pulled = 0f
            return
        }
        // Everything below is unfolding, which a fling is never allowed to do. Away from the top a
        // downward delta is the list scrolling, not a pull against its end, so the count starts over
        // — which is also what stops travel from two separate moments adding up.
        if (!fromUser || expanded || !atTop) {
            pulled = 0f
            return
        }
        pulled += deltaY
        if (pulled >= threshold) {
            expanded = true
            pulled = 0f
        }
    }

    /** Search opened. Folds, every time — not only the first. */
    fun onSearchOpened() {
        expanded = false
    }

    /** The panel was tapped. Only ever unfolds: a tap is not a way to fold it. */
    fun onPanelTapped() {
        expanded = true
    }

    companion object {
        /**
         * Only [expanded] survives a rotation. The gesture bookkeeping describes a finger that is no
         * longer on the screen, so restoring it would be restoring a lie.
         */
        val Saver: Saver<ListeningFold, Boolean> = Saver(
            save = { it.expanded },
            restore = { ListeningFold(it) },
        )
    }
}
