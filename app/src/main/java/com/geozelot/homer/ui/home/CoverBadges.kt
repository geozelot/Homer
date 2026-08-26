package com.geozelot.homer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
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
import com.geozelot.homer.ui.theme.Sage
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
internal enum class CoverCorner { TOP_START, TOP_END, BOTTOM_END }

/** How far the slanted inner edge leans, as a fraction of the area's height. */
private const val SLANT = 0.55f

/** The scrim behind cover furniture: dark enough to read on any artwork, light enough to see through. */
private val BadgeScrim = Studio.copy(alpha = 0.78f)

/**
 * A quadrilateral with two sides on the cover's edges and one slanted side facing the artwork.
 *
 * Built per corner rather than rotated, because the slant always leans the same way relative to the
 * corner it anchors to — a rotation would send it the wrong way on two of the three.
 */
private fun cutShape(corner: CoverCorner): Shape = GenericShape { size, _ ->
    val slant = size.height * SLANT
    when (corner) {
        // Anchored top-left; the inner edge falls away to the right as it descends.
        CoverCorner.TOP_START -> {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - slant, size.height)
            lineTo(0f, size.height)
        }
        // Anchored top-right; the inner edge falls away to the left as it descends.
        CoverCorner.TOP_END -> {
            moveTo(slant, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
        }
        // Anchored bottom-right; the inner edge leans out as it descends to the cover's foot.
        CoverCorner.BOTTOM_END -> {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(slant, size.height)
        }
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
    content: @Composable () -> Unit,
) {
    // The padding is asymmetric on purpose: the slanted side needs room the square sides do not,
    // or the content collides with the diagonal.
    val slantPad = 9.dp
    Box(
        modifier = modifier
            .clip(cutShape(corner))
            .background(BadgeScrim)
            .padding(
                start = if (corner == CoverCorner.TOP_END) slantPad else 6.dp,
                end = if (corner == CoverCorner.TOP_START) slantPad else 6.dp,
                top = 3.dp,
                bottom = 3.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            content()
        }
    }
}

/** A glyph sized for cover furniture. */
@Composable
internal fun BadgeIcon(icon: ImageVector, tint: Color = Parchment, size: Dp = 11.dp) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

/** A short string sized for cover furniture — a duration, a volume count. */
@Composable
internal fun BadgeText(text: String, color: Color = Parchment) {
    Text(text, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 11.sp)
}

/** "Downloaded", in the one colour Homer uses to mean it. */
@Composable
internal fun OfflineBadge(corner: CoverCorner, count: String? = null, modifier: Modifier = Modifier) {
    CoverBadge(corner, modifier) {
        BadgeIcon(Icons.Filled.DownloadDone, tint = Sage)
        count?.let { BadgeText(it, color = Sage) }
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
) {
    CoverBadge(corner, modifier) {
        BadgeIcon(if (isCollection) Icons.Filled.LibraryBooks else Icons.Filled.MenuBook)
        BadgeText(count.toString())
    }
}
