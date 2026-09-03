package com.geozelot.homer.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Where the covers of a stacked shelf sit, as arithmetic rather than as inline expressions.
 *
 * Pulled out because the same geometry was written twice — once for the grid cell and once for the
 * list row — with its own constants each time, and the two drifted the first chance they got: one
 * fanned by 6.5% of its box and the other by 3.6%, so the list read as a pile and the grid as a
 * hairline. It is also the sort of thing that is obvious on a device and invisible in review, which
 * is the argument for being able to assert on it.
 *
 * ## The fan TAPERS
 *
 * The gaps close as the pile recedes — 8dp, then 5, then 3 — and the reason is that the two numbers
 * people conflate here are not the same number:
 *
 *  - **what you can see** is the FIRST step, since that is the sliver of the cover behind the front
 *    one, and
 *  - **what it costs** is the SUM of every step, because that is what the front cover gives up.
 *
 * A uniform fan pays for the third cover at the same rate as the first, and the third is the one
 * nobody looks at. Tapering spends the budget where it is seen: against a uniform 6dp fan, this
 * shows 8dp of the nearest cover instead of 6 — twice what the original 4dp fan showed — while
 * costing 16dp rather than 18, so the front cover comes out *larger* as well. It beats the uniform
 * version on both axes rather than trading between them.
 *
 * It is also what a real pile looks like from an angle: near gaps open, far gaps compress.
 *
 * ## Room is reserved for the maximum
 *
 * [place] sizes every cover as though the pile were full, and centres however many are actually
 * drawn in what is left. So a two-volume shelf's front cover is exactly the size an eight-volume
 * shelf's is — the alternative, letting it grow, varies the cover by ~11dp between neighbouring
 * cards in the same row.
 *
 * A short pile takes the NEAREST steps, so the first gap is 8dp on every shelf whatever its depth.
 * That is the gap the eye compares between one card and the next.
 */
internal object CoverStack {

    /** Nearest first. Three steps, so at most four covers. */
    val GridSteps: List<Dp> = listOf(8.dp, 5.dp, 3.dp)

    /**
     * Two steps at row scale, tapered on the same reasoning.
     *
     * The grid's numbers do not scale down: a row's whole stack is 46dp, a fifth of a grid cell, so
     * a third and fourth cover there are hairlines that cost the front cover a fifth of its width to
     * say what the count badge says better. Same 6dp total the untapered row fan used, redistributed
     * so the visible gap is 4dp instead of 3.
     */
    val RowSteps: List<Dp> = listOf(4.dp, 2.dp)

    /** How the covers of one pile are laid out inside a square cell. */
    data class Placement(
        /** The side of every cover in the pile — the same whatever [Placement.positions] holds. */
        val cover: Dp,
        /**
         * Top-left of each cover, FRONT FIRST.
         *
         * Draw it in reverse so the deepest lands first and the front cover ends up on top.
         */
        val positions: List<DpOffset>,
    )

    /**
     * Lays out `sheets + 1` covers in a square [cell], inset by [pad] on every side.
     *
     * The front cover sits at the bottom-left of the pile and each cover behind it steps up and to
     * the right by the next entry in [steps]. [sheets] is clamped, so a caller cannot ask for more
     * covers than there are steps to place them with.
     */
    fun place(cell: Dp, pad: Dp, steps: List<Dp>, sheets: Int): Placement {
        val drawn = sheets.coerceIn(0, steps.size)
        val reserved = steps.fold(0.dp) { sum, step -> sum + step }
        val used = steps.take(drawn).fold(0.dp) { sum, step -> sum + step }
        val cover = cell - pad * 2 - reserved
        // Centred in what the undrawn steps left behind, rather than anchored — which would leave a
        // short pile with a hole in the top-right where the missing covers would have been.
        val origin = pad + (reserved - used) / 2
        val bottom = origin + used
        // Cumulative from the front, so a cover's offset is every step between it and the front.
        var offset = 0.dp
        val positions = buildList {
            add(DpOffset(origin, bottom))
            for (index in 0 until drawn) {
                offset += steps[index]
                add(DpOffset(origin + offset, bottom - offset))
            }
        }
        return Placement(cover = cover, positions = positions)
    }
}
