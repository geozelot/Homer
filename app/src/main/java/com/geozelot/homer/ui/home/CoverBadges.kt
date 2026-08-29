package com.geozelot.homer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Studio

/**
 * The furniture that sits ON a cover: small translucent areas carrying one fact each.
 *
 * A cover is the largest thing on a library card and the only part of it with room to spare, so the
 * facts that used to need a line of text underneath — is it downloaded, how long is it, how many
 * volumes — are drawn into its corners instead. That buys the text block below back for the title.
 *
 * Each area is **cut to an edge of the cover**: two of its sides are the cover's own edges, and the
 * side facing into the artwork is slanted. A plain rectangle floating in a corner reads as a sticker
 * dropped on top of the picture; one that runs off the edge reads as part of the card. The slant is
 * what keeps it from looking like a crop bar — it gives the area a direction, away from the corner
 * it is anchored to.
 *
 * Everything here is deliberately monochrome on a dark scrim: the cover underneath is already
 * carrying colour, and a second palette on top of it competes. [Sage] is the one exception, and only
 * where it means what it means everywhere else in Homer — downloaded.
 */

/** Which edge-corner an area is cut to. */
internal enum class CoverCorner {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
    ;

    /** Whether this corner hangs from the cover's top edge or stands on its bottom one. */
    internal val atTop: Boolean get() = this == TOP_START || this == TOP_END

    /** Whether the slanted side faces left — true when the badge is anchored to the right edge. */
    internal val slantsLeft: Boolean get() = this == TOP_END || this == BOTTOM_END
}

/** How far the slanted inner edge leans, as a fraction of the area's height. */
private const val SLANT = 0.55f

/** The scrim behind cover furniture: dark enough to read on any artwork, light enough to see through. */
private val BadgeScrim = Studio.copy(alpha = 0.78f)

/**
 * A quadrilateral with two sides on the cover's edges and one slanted side facing the artwork.
 *
 * **The long edge always lies along the cover's border.** A badge hanging from the top edge is
 * widest at the top and tapers downward; one standing on the bottom edge is widest at the bottom and
 * tapers upward. Get that backwards and the shape reads as peeling away from the edge it is supposed
 * to be part of — which is what the top-right and bottom-right ones were doing, both tapering the
 * wrong way because the geometry was written per corner by hand instead of derived from it.
 *
 * The slant then leans away from the corner it anchors to: leftward for a badge on the right edge,
 * rightward for one on the left.
 */
private fun cutShape(corner: CoverCorner): Shape = GenericShape { size, _ ->
    val slant = size.height * SLANT
    val w = size.width
    val h = size.height
    // The inset is on the edge OPPOSITE the border the badge hangs from, on the slanted side.
    if (corner.atTop) {
        // Full width along the top; the bottom edge is pulled in on the slanted side.
        moveTo(0f, 0f)
        lineTo(w, 0f)
        lineTo(if (corner.slantsLeft) w else w - slant, h)
        lineTo(if (corner.slantsLeft) slant else 0f, h)
    } else {
        // Full width along the bottom; the top edge is pulled in on the slanted side.
        moveTo(if (corner.slantsLeft) slant else 0f, 0f)
        lineTo(if (corner.slantsLeft) w else w - slant, 0f)
        lineTo(w, h)
        lineTo(0f, h)
    }
    close()
}

/**
 * One area of cover furniture.
 *
 * [content] is laid out in a row and is expected to be an icon, a short string, or both. Nothing
 * here is announced to a screen reader: the card that owns the cover already reads out its title and
 * its state, and a handful of extra nodes in the middle of the artwork only interrupt that.
 */
@Composable
internal fun CoverBadge(
    corner: CoverCorner,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Asymmetric on purpose. The slanted side eats into the box as it crosses, so content set the
    // same distance from both sides collides with the diagonal on one of them — which is what had
    // the duration touching its own edge, since the bottom-right badge was getting no slant
    // allowance at all. Derived from where the slant IS rather than listed per corner.
    val slantPad = if (compact) 9.dp else 13.dp
    val flatPad = if (compact) 5.dp else 8.dp
    Box(
        modifier = modifier
            .clip(cutShape(corner))
            .background(BadgeScrim)
            .padding(
                start = if (corner.slantsLeft) slantPad else flatPad,
                end = if (corner.slantsLeft) flatPad else slantPad,
                top = if (compact) 3.dp else 4.dp,
                bottom = if (compact) 3.dp else 4.dp,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
        ) {
            content()
        }
    }
}

/**
 * A glyph sized for cover furniture.
 *
 * Everything is [Parchment] — the same tone the interface's own headings and primary text use.
 * Colour-coding the offline mark green made it the loudest thing on a cover that is already carrying
 * artwork, and the corners read as one set of markings rather than a traffic light now that they
 * share a tint. Meaning is carried by the glyph.
 */
@Composable
internal fun BadgeIcon(icon: ImageVector, tint: Color = Parchment, size: Dp? = null, compact: Boolean = false) {
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size ?: if (compact) 11.dp else 15.dp),
    )
}

/** A short string sized for cover furniture — a duration, a volume count. */
@Composable
internal fun BadgeText(text: String, color: Color = Parchment, compact: Boolean = false) {
    Text(
        text,
        color = color,
        fontSize = if (compact) 9.5.sp else 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = if (compact) 11.sp else 14.sp,
    )
}

/**
 * "Downloaded".
 *
 * `OfflinePin` — a tick inside a ring — rather than `DownloadDone`'s arrow-over-a-line. At this size
 * the arrow reads as an action still to be taken, which is the opposite of what it means; a sealed
 * tick reads as a state already reached.
 */
@Composable
internal fun OfflineBadge(
    corner: CoverCorner,
    count: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    CoverBadge(corner, modifier, compact) {
        BadgeIcon(Icons.Filled.OfflinePin, compact = compact)
        count?.let { BadgeText(it, compact = compact) }
    }
}

/**
 * How many volumes a stacked shelf holds, and whether it is a collection or a plain series.
 *
 * The glyph is the only thing that distinguishes a Discworld card from a Rincewind card once both
 * are drawn as one stack, so it carries the difference: a shelf of books for a parent grouping, a
 * single book for a series.
 */
@Composable
internal fun VolumeCountBadge(
    count: Int,
    isCollection: Boolean = false,
    corner: CoverCorner = CoverCorner.TOP_START,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    CoverBadge(corner, modifier, compact) {
        BadgeIcon(if (isCollection) Icons.Filled.LibraryBooks else Icons.Filled.MenuBook, compact = compact)
        BadgeText(count.toString(), compact = compact)
    }
}

/**
 * Where one book sits in the thing it belongs to — "#3" of its series, or of its collection.
 *
 * Series first when a book has both. The sub-series is the more specific claim, and a Discworld
 * witches novel is "Die Hexen #3" before it is "Scheibenwelt #12"; showing both would put two
 * different numbers about the same book in the same corner — so the corner does not say WHICH it is,
 * and does not try to. The shelf the book is sitting on is the context that answers that.
 *
 * Draws nothing when the book is in neither, rather than a bare "#" or a zero — most standalones are
 * in neither, and a badge that appears on every cover to say nothing is worse than no badge.
 */
@Composable
internal fun VolumeIndexBadge(
    seriesIndex: Int?,
    collectionIndex: Int?,
    corner: CoverCorner = CoverCorner.TOP_START,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val index = seriesIndex ?: collectionIndex ?: return
    // A "#", not a glyph. The book/shelf icons said WHICH of the two numbers this is, at the cost of
    // the corner reading as an icon with a number stuck to it rather than as a number — and the
    // distinction was never the question being asked here. "#3" is what a volume number looks like.
    CoverBadge(corner, modifier, compact) { BadgeText("#$index", compact = compact) }
}

/**
 * WHAT this shelf is — a series or a collection — with no number attached.
 *
 * For the list view, where the count already sits in the text beside the cover ("12 books") and a
 * badge repeating it would put the same number twice on one row two centimetres apart. What the text
 * cannot say at a glance is which KIND of shelf this is, and that is exactly what a glyph is for.
 */
@Composable
internal fun ShelfKindBadge(
    isCollection: Boolean,
    corner: CoverCorner = CoverCorner.TOP_START,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    CoverBadge(corner, modifier, compact) {
        BadgeIcon(if (isCollection) Icons.Filled.LibraryBooks else Icons.Filled.MenuBook, compact = compact)
    }
}

/** How long a book or a whole shelf runs. */
@Composable
internal fun DurationBadge(
    text: String,
    corner: CoverCorner = CoverCorner.BOTTOM_END,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    CoverBadge(corner, modifier, compact) { BadgeText(text, compact = compact) }
}
