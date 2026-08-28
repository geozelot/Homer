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
 *  - **on a deliberate pull while the library is ALREADY at its top.** A gesture that begins
 *    anywhere below the top is refused for its whole duration, however far past the top it carries;
 *    otherwise one flick would both scroll home and unfold a panel nobody asked for.
 *  - **Folding disqualifies the gesture that did it.** Scroll down and back up in one movement and
 *    the panel stays folded — a single gesture must not both close and reopen it.
 *  - **A fling can fold it but never unfold it.** Folding on momentum is harmless; unfolding on
 *    momentum is the panel opening itself.
 */
internal class ListeningFold(expanded: Boolean = true) {

    /** Whether the panel is showing its full items. Compose state, so reading it recomposes. */
    var expanded: Boolean by mutableStateOf(expanded)
        private set

    /**
     * Whether a finger-down-to-finger-up interaction is in progress, INCLUDING the fling it throws.
     *
     * Compose reports a drag's deltas, then a pre-fling, then the fling's deltas, then a post-fling.
     * Treating all of that as one gesture is what makes "the gesture began below the top" mean what
     * a reader would mean by it.
     */
    private var inGesture = false

    /** Whether this gesture is allowed to unfold at all. Decided at its first delta, and revocable. */
    private var mayUnfold = false

    /** Downward travel accumulated by the current gesture, once it is eligible to count. */
    private var pulled = 0f

    /**
     * A scroll delta arrived.
     *
     * [deltaY] is the RAW pointer movement before anything consumes it — positive when the finger
     * travels down the screen, i.e. back towards the top of the list. [atTop] is whether the list
     * can go no further up. [fromUser] separates a finger from a fling.
     */
    fun onScroll(deltaY: Float, atTop: Boolean, fromUser: Boolean, threshold: Float) {
        if (!inGesture) {
            inGesture = true
            // Only a gesture that begins at the very top may ever unfold.
            mayUnfold = atTop
        }
        // Folding first, and unconditionally: any travel into the library folds the panel, whether
        // the finger is still down or the list is coasting.
        if (deltaY < 0f) {
            expanded = false
            pulled = 0f
            // …and this gesture has now had its say. Without this, scrolling down and back up in one
            // movement would fold the panel and then immediately pull it open again.
            mayUnfold = false
            return
        }
        if (!fromUser || expanded || !mayUnfold) return
        // Below the top, a downward delta is the list scrolling, not a pull against its end.
        if (!atTop) {
            pulled = 0f
            return
        }
        if (deltaY > 0f) {
            pulled += deltaY
            if (pulled >= threshold) {
                expanded = true
                pulled = 0f
            }
        }
    }

    /**
     * The gesture ended — finger up AND its fling finished.
     *
     * Called from `onPostFling`, never `onPreFling`. Pre-fling runs at the START of the fling, still
     * inside the gesture, so clearing the refusal there would hand that gesture's own momentum a
     * clean slate and undo what it had earned.
     */
    fun onGestureEnd() {
        inGesture = false
        mayUnfold = false
        pulled = 0f
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
