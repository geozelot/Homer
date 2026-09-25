package com.geozelot.homer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.EditableBook
import com.geozelot.homer.data.library.authorsFromInput
import com.geozelot.homer.ui.components.HomerIcons
import com.geozelot.homer.ui.components.pressFeedback
import com.geozelot.homer.ui.components.rememberTapInteraction
import com.geozelot.homer.ui.components.tapTarget
import com.geozelot.homer.ui.home.FilterFacet
import com.geozelot.homer.ui.home.FilterToken
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.LineShelf
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle
import com.geozelot.homer.ui.theme.Surface2

// ── Book header ──────────────────────────────────────────────────────────────

/** Wide pill opening the chapter picker; sits below the title. */
@Composable
internal fun ChapterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // The tap area is expanded to the 48dp minimum around the pill rather than by inflating the
    // pill itself, so the visual stays the compact chip the layout was designed around.
    val interaction = rememberTapInteraction()
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .tapTarget(interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Surface2)
                .pressFeedback(interaction)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Parchment, modifier = Modifier.size(15.dp))
            Text(
                stringResource(R.string.player_chapters),
                color = Parchment,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Who wrote it, what it is called, and what it belongs to — in that order, in a block of a fixed
 * height.
 *
 * ## The order
 *
 * Author, title, series, collection. The two names a reader already knows lead — the author, then
 * the book — and what places it in a shelf follows, narrowest relation first: the series it is a
 * volume of, then the collection that series sits in. Reading down is reading outward.
 *
 * ## Why the space is reserved
 *
 * Every slot is drawn whether or not the book fills it. A standalone with no series and no
 * collection occupies exactly as much as a numbered volume of a nested series does, so the cover
 * above and the scrubber below sit at the same height for every book — and moving between two
 * books does not shuffle the transport under a thumb that is already reaching for it.
 *
 * ## The chips
 *
 * Series and collection are chips because they are the two things here you can act on: each one
 * narrows the library to it and leaves. They differ in weight on purpose. The series is the closer
 * relation and wears the accent; the collection is the outer one and wears the shelf hairline the
 * library already uses to mean exactly that. Both say "<name>, Volume n" — a number without the
 * thing it counts is not a fact.
 */
@Composable
internal fun BookHeader(
    book: EditableBook?,
    title: String,
    scale: Float,
    onFilter: (FilterToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Reserved, like everything below it: a book with no author must not pull the title up.
        Box(modifier = Modifier.height(BookHeaderAuthorLine.scaled(scale)), contentAlignment = Alignment.Center) {
            // ONE CHIP PER AUTHOR. The field this comes from is the edit form — semicolon-joined,
            // because a name may contain a comma — so a single chip labelled it verbatim: a
            // co-written book read "Marc Vierhaus; Asja Maass" and filtered on that whole string,
            // which matches nobody. Split back through the same function the field is written with,
            // so the two cannot disagree about where one name ends.
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                authorsFromInput(book?.author.orEmpty()).forEach { author ->
                    // A chip like the two below it. It was bare text, which made the one fact up
                    // here you can act on look like the one fact you cannot — and three things that
                    // all filter the library should not be drawn three different ways.
                    LineageChip(
                        label = author,
                        icon = HomerIcons.Author,
                        scale = scale,
                        onClick = { onFilter(FilterToken(FilterFacet.AUTHOR, author)) },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        // Two lines' worth, always — a one-line title leaves the second empty rather than letting
        // the block breathe differently for every book.
        Box(
            modifier = Modifier.height(BookHeaderTitleBlock.scaled(scale)).padding(top = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title,
                style = SerifTitle.copy(
                    fontSize = 22.sp.scaled(scale, floor = 17f),
                    lineHeight = 27.sp.scaled(scale, floor = 21f),
                ),
                color = Parchment,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.height(BookHeaderChipRow.scaled(scale)), contentAlignment = Alignment.Center) {
            book?.series?.takeIf { it.isNotBlank() }?.let {
                LineageChip(
                    label = withVolume(it, book.seriesIndex),
                    icon = HomerIcons.SeriesBracket,
                    scale = scale,
                    onClick = { onFilter(FilterToken(FilterFacet.SERIES, it)) },
                )
            }
        }
        Box(modifier = Modifier.height(BookHeaderChipRow.scaled(scale)), contentAlignment = Alignment.Center) {
            book?.collection?.takeIf { it.isNotBlank() }?.let {
                LineageChip(
                    label = withVolume(it, book.collectionIndex),
                    icon = HomerIcons.CollectionBracket,
                    scale = scale,
                    onClick = { onFilter(FilterToken(FilterFacet.COLLECTION, it)) },
                )
            }
        }
    }
}

/**
 * One fact about the book, as a chip: a mark saying which kind of fact, and its value.
 *
 * ## One style for all three
 *
 * They were drawn three ways — the author as bare text, the series on the accent, the collection
 * quieter — which said the three were three different sorts of thing. They are not: each is one
 * fact about this book, and each narrows the library to it. The mark is what distinguishes them
 * now, which is what a mark is for, and it leaves the chips free to be identical.
 *
 * ## Why the accent went
 *
 * A filled amber chip is what Homer uses for a filter that is ON. None of these is on; each is an
 * offer. Wearing the accent, the series chip claimed the library was already narrowed to it.
 */
@Composable
private fun LineageChip(
    label: String,
    icon: ImageVector,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Surface2)
            .border(1.dp, LineShelf, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Faint,
            modifier = Modifier.size(11.dp.scaled(scale)),
        )
        Text(
            label,
            color = Muted,
            fontSize = 11.sp.scaled(scale, floor = 10f),
            lineHeight = 13.sp.scaled(scale, floor = 12f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Helgoland, Volume 1", or just "Helgoland" where the book carries no number in it. */
@Composable
private fun withVolume(name: String, index: Int?): String =
    if (index == null) name else stringResource(R.string.player_series_volume, name, index)

/** The author's line, reserved whether or not there is one. */
private val BookHeaderAuthorLine = 20.dp

/** Two lines of the serif title at 22sp, so a one-line title does not shorten the block. */
private val BookHeaderTitleBlock = 60.dp

/** One relation chip, reserved. Two of these, so a standalone book is as tall as a nested one. */
private val BookHeaderChipRow = 24.dp
