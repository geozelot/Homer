package com.geozelot.homer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Marks Homer draws itself, because a stock icon that merely sounds right is not a design decision.
 *
 * [Spines] exists because of a specific failure: a mark was designed, reviewed at true size, and
 * approved — and then implemented as `Icons.Filled.CollectionsBookmark` on the strength of the name
 * matching the description. It renders as something else. The mock and the device disagreed, which is
 * the one thing a design review is supposed to rule out, and no amount of care in the review could
 * have caught it because the review never looked at the stock icon.
 *
 * So the shape lives here, in the same coordinates it was drawn and approved in. What ships is what
 * was shown.
 */
object HomerIcons {

    /**
     * Three upright book spines, the last carrying a bookmark notch. A shelf, at badge size.
     *
     * ## Why this shape and not a stock one
     *
     * It is the only candidate tested that still reads as *books* at 13dp. The framed alternatives
     * (`LibraryBooks`) lose their contents to the frame; the open book (`MenuBook`) reads as ONE book
     * being read, which is the opposite of what a shelf badge claims; the fanned card shapes rely on
     * opacity steps that are gone well before 15dp; and the layered lozenges are the Arrange chip's
     * own glyph, so re-using them would make one symbol mean two things.
     *
     * Three uprights survive on silhouette alone, and three uprights is the whole message. The
     * bookmark notch is the part that goes soft first, which is fine — it is the grace note, not the
     * meaning.
     *
     * ## The geometry, deliberately blunt
     *
     * A 24-unit viewport to match the Material set it sits beside, so the same `size` reads the same
     * weight. Plain rectangles with square corners: the design carries a 1-unit corner radius, which
     * at 13–17dp is a third of a pixel and costs four arcs per corner to express. The notch is a
     * single V cut into the third spine's foot.
     *
     * Filled black because [androidx.compose.material3.Icon] paints its own tint over the vector;
     * the fill colour here is never seen.
     */
    val Spines: ImageVector by lazy {
        ImageVector.Builder(
            name = "Spines",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Left spine.
                moveTo(2f, 3f)
                lineTo(6.4f, 3f)
                lineTo(6.4f, 21f)
                lineTo(2f, 21f)
                close()
                // Middle spine.
                moveTo(8f, 3f)
                lineTo(12.4f, 3f)
                lineTo(12.4f, 21f)
                lineTo(8f, 21f)
                close()
                // Right spine, wider, with the bookmark cut out of its foot.
                moveTo(14.4f, 3f)
                lineTo(21.6f, 3f)
                lineTo(21.6f, 21f)
                lineTo(18f, 18.6f)
                lineTo(14.4f, 21f)
                close()
            }
        }.build()
    }
}
