package com.geozelot.homer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.unit.TextUnit
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
 * carrying colour, and a second palette on top of it competes. Meaning is carried by the glyph.
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

/**
 * How large a piece of cover furniture is.
 *
 * A size, not a `compact` flag. There are three of them now and a boolean could not say which: the
 * top corners of a grid card carry the two facts somebody reads at arm's length and were the ones
 * asked to grow; the bottom corner carries a duration and was explicitly left where it was; a list
 * row's cover is 38dp wide and everything on it has to be measured against that.
 *
 * ## [slant] is relaxed on the small size, and it is the only reason it fits
 *
 * The slanted edge costs width proportional to the badge's HEIGHT, so a bigger glyph buys a taller
 * badge which buys more dead diagonal. At [SMALL] with the usual 0.55 a 13dp glyph occupies 29 of
 * the 38dp cover — three quarters of the artwork, for one symbol. At 0.35 it is 25dp. The shape is
 * marginally less raked than its larger siblings, which is a smaller price than a badge that eats
 * the picture it is annotating.
 */
internal enum class BadgeSize(
    val glyph: Dp,
    val text: TextUnit,
    val lineHeight: TextUnit,
    /** Breathing room on the un-slanted side. */
    val flatPad: Dp,
    /** Clearance for the diagonal, which eats into the box as it crosses. */
    val slantPad: Dp,
    val verticalPad: Dp,
    val gap: Dp,
    /** How far the slanted inner edge leans, as a fraction of the area's height. */
    val slant: Float,
) {
    /** The top corners of a grid card. */
    LARGE(17.dp, 13.5.sp, 16.sp, 9.dp, 15.dp, 5.dp, 4.dp, 0.55f),

    /** The bottom corner of a grid card — a duration, and no glyph to make legible. */
    MEDIUM(15.dp, 12.sp, 14.sp, 8.dp, 13.dp, 4.dp, 4.dp, 0.55f),

    /** Anything on a list row's cover. */
    SMALL(13.dp, 9.5.sp, 11.sp, 5.dp, 11.dp, 3.5.dp, 3.dp, 0.35f),
}

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
private fun cutShape(corner: CoverCorner, lean: Float): Shape = GenericShape { size, _ ->
    val slant = size.height * lean
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
    size: BadgeSize = BadgeSize.MEDIUM,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(cutShape(corner, size.slant))
            .background(BadgeScrim)
            // Asymmetric on purpose. The slanted side eats into the box as it crosses, so content
            // set the same distance from both sides collides with the diagonal on one of them —
            // which is what had the duration touching its own edge, since the bottom-right badge
            // was getting no slant allowance at all. Derived from where the slant IS rather than
            // listed per corner.
            .padding(
                start = if (corner.slantsLeft) size.slantPad else size.flatPad,
                end = if (corner.slantsLeft) size.flatPad else size.slantPad,
                top = size.verticalPad,
                bottom = size.verticalPad,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(size.gap),
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
 * share a tint.
 */
@Composable
internal fun BadgeIcon(icon: ImageVector, size: BadgeSize, tint: Color = Parchment, override: Dp? = null) {
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(override ?: size.glyph),
    )
}

/** A short string sized for cover furniture — a duration, a volume count, an index. */
@Composable
internal fun BadgeText(text: String, size: BadgeSize, color: Color = Parchment) {
    Text(
        text,
        color = color,
        fontSize = size.text,
        fontWeight = FontWeight.SemiBold,
        // Set explicitly, because Material's own body styles carry a 24sp line height and a Text
        // overriding only fontSize is laid out in a 24dp line box whatever size the letters are.
        lineHeight = size.lineHeight,
    )
}

/**
 * "Downloaded".
 *
 * The arrow-over-a-bar, which is what the three download menu items that produce this state already
 * use — so the mark on the cover and the action that put it there are the same symbol.
 *
 * It was `OfflinePin`, a tick inside a ring, on the reasoning that at badge size an arrow reads as
 * an action still to be taken rather than a state already reached. That is a real cost and it is
 * being paid deliberately: matching the menu, and the convention every other app uses, is worth more
 * than avoiding a momentary misreading of a corner that also has a count beside it.
 */
@Composable
internal fun OfflineBadge(
    corner: CoverCorner,
    count: String? = null,
    modifier: Modifier = Modifier,
    size: BadgeSize = BadgeSize.LARGE,
) {
    CoverBadge(corner, modifier, size) {
        BadgeIcon(Icons.Filled.Download, size)
        count?.let { BadgeText(it, size) }
    }
}

/**
 * That this card is a SHELF rather than a book, and — where there is room — how many books are on it.
 *
 * ## One mark for a series and for a collection
 *
 * It used to be two: a shelf of books for a collection, a single open book for a series. The
 * distinction cost more than it bought. At badge size the two glyphs were separated by detail that
 * had already dissolved, the open book read as *one* book being read — the opposite of the claim a
 * shelf badge makes — and the framed shelf became a filled square. Meanwhile the question the badge
 * exists to answer is "is this one thing or several", which both kinds answer the same way. Which
 * KIND it is, when that matters, is named in words on the details card and by the collection chip in
 * the control bar.
 *
 * `CollectionsBookmark` — three upright spines with a bookmark tab — because it is the only
 * candidate that still reads as *books* at 13dp. The tab is the part that goes soft first; three
 * uprights is what survives, and three uprights is the whole message.
 *
 * [count] is omitted on a list row, where the meta line beside the cover already says "8 books" and
 * a badge repeating it would put the same number twice on one row two centimetres apart.
 */
@Composable
internal fun ShelfBadge(
    count: Int? = null,
    corner: CoverCorner = CoverCorner.TOP_START,
    modifier: Modifier = Modifier,
    size: BadgeSize = BadgeSize.LARGE,
) {
    CoverBadge(corner, modifier, size) {
        BadgeIcon(Icons.Filled.CollectionsBookmark, size)
        count?.let { BadgeText(it.toString(), size) }
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
    size: BadgeSize = BadgeSize.LARGE,
) {
    val index = seriesIndex ?: collectionIndex ?: return
    // A "#", not a glyph. The book/shelf icons said WHICH of the two numbers this is, at the cost of
    // the corner reading as an icon with a number stuck to it rather than as a number — and the
    // distinction was never the question being asked here. "#3" is what a volume number looks like.
    CoverBadge(corner, modifier, size) { BadgeText("#$index", size) }
}

/** How long a book or a whole shelf runs. */
@Composable
internal fun DurationBadge(
    text: String,
    corner: CoverCorner = CoverCorner.BOTTOM_END,
    modifier: Modifier = Modifier,
    size: BadgeSize = BadgeSize.MEDIUM,
) {
    CoverBadge(corner, modifier, size) { BadgeText(text, size) }
}
