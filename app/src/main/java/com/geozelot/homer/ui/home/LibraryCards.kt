package com.geozelot.homer.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.LineShelf
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Studio
import java.util.Locale

// ── Grid cards ───────────────────────────────────────────────────────────────
//
// What a book and a shelf look like in grid view: the card, its footer text, the chips it carries,
// and the stacked-cover treatment that says "more than one book". The rules deciding which chips
// appear ([RowContext], [volumeIndexFor]) live here because the cards are what they were written
// for — the list rows read them across.

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookGridCard(
    book: BookListItem,
    ctx: RowContext,
    onOpen: (String) -> Unit,
    actions: BookActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val showProgress = book.hasVisibleProgress()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (book.hidden) 0.5f else 1f)
            .combinedClickable(
                onClick = { onOpen(book.id) },
                onLongClick = { menuOpen = true },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Line, RoundedCornerShape(10.dp)),
        ) {
            CoverArt(model = book.coverModel, modifier = Modifier.fillMaxSize())
            // Top-left: where this book sits in the thing it belongs to.
            //
            // The SERIES number when it has one, and the collection's otherwise — the sub-series is
            // the more specific claim, and a book in both would say two different numbers about
            // itself in one corner. Which of the two it is goes unsaid: the shelf the book is
            // sitting on is the context that answers it.
            VolumeIndexBadge(
                index = volumeIndexFor(book, ctx),
                modifier = Modifier.align(Alignment.TopStart),
                size = BadgeSize.LARGE,
            )
            if (book.isDownloaded) {
                OfflineBadge(
                    CoverCorner.TOP_END,
                    modifier = Modifier.align(Alignment.TopEnd),
                    size = BadgeSize.LARGE,
                )
            }
            // The length, moved to the LEFT corner to make room for the menu opposite. Still the
            // corner a reader consults least, and still MEDIUM while the two above are LARGE: it is
            // text with no glyph to make legible.
            book.totalDurationMs?.let {
                DurationBadge(
                    formatCompactDuration(it),
                    corner = CoverCorner.BOTTOM_START,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
            // The menu, off the footer and onto the cover — see MenuBadge. What the footer gets
            // back is 32dp of width for the title, which is the thing a grid cell never has enough
            // of.
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                CoverMenuButton { menuOpen = true }
                BookMenu(book, menuOpen, actions) { menuOpen = false }
            }
        }
        // Under the cover rather than on it, exactly as the listening strip has always drawn it —
        // so "how far in am I" looks the same wherever a book appears.
        if (showProgress) {
            ProgressBar(
                fraction = book.progress ?: 0f,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            )
        }
        GridCardText(
            title = book.title,
            meta = bookMeta(book, ctx, LocalContext.current, withDuration = false, withIndex = false),
            chip = {
                MetaChipSlot(
                    chips = bookChip(book, ctx),
                    ctx = ctx,
                    onFilter = { kind, value -> actions.onFilter(chipToken(kind, value)) },
                    lines = ctx.chipLines,
                )
            },
        )
    }
}

/**
 * Title (2 reserved lines) + the genre chip's reserved row + meta (2 reserved lines) — a
 * fixed-height block, so every grid card (book or series) is exactly the same total height and
 * their bottoms line up.
 *
 * ## The overflow button is not here any more
 *
 * It was, and it took 32dp of a ~100dp cell away from the title — a compromise the old comment
 * spent a paragraph defending, on a card whose scarcest resource is width for a name. It is a
 * badge on the cover's bottom-right corner now, where the corners are furniture anyway and the
 * duration it displaced had a free corner to move to. The footer is a plain column of text, and
 * gets the whole cell.
 */
@Composable
private fun GridCardText(
    title: String,
    meta: String,
    /**
     * The genre chip's row, reserved whether or not there is a chip in it.
     *
     * A slot rather than a value, because what goes in it differs per item — a book carries its own
     * genres, a shelf carries what most of its books agree on — and both have to occupy exactly the
     * same height or the grid stops lining up.
     */
    chip: @Composable () -> Unit,
) {
    // The footer stands off whatever is below it.
    //
    // The grid spaces its rows 12dp apart, which is the gap between a row's tallest CARD and the
    // next row's cover — and a card's last line is small muted text, so 12dp put one card's meta
    // line closer to the next book's artwork than to its own title. The extra is on the card
    // rather than on the grid so a full-span heading, which is its own item, does not inherit it;
    // see the header's own top padding, which gives that gap back.
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = GridCardFooterGap)) {
        // Indented to where the chip's TEXT starts, not to where its outline does. The pill's
        // hairline hangs into the margin instead of shunting the words it belongs to sideways, so
        // the title, the chip's label and the meta line share one left edge.
        Text(
            title,
            color = Parchment,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 13.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = MetaChipSlot.TextInset, top = 5.dp),
        )
        Box(modifier = Modifier.padding(top = MetaChipSlot.TitleGap)) { chip() }
        Text(
            meta,
            color = Muted,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = MetaChipSlot.TextInset, top = 1.dp),
        )
    }
}


/**
 * The chip a book card shows, and the token tapping it commits.
 *
 * Factored out because four item views ask the same two questions and answering them at each call
 * site is how the grid and the list end up disagreeing about what a card says.
 */
internal fun bookChip(book: BookListItem, ctx: RowContext) =
    metaChipFor(
        book.genres,
        book.author,
        ctx.shelving,
        // Shelved by nothing: no heading above this card says either fact, so the card says both.
        //
        // It used to also require a flat grouping, which made the same card describe itself three
        // different ways depending on a setting that has nothing to do with it — two chips when
        // flat, one chip and the author in plain text underneath when grouped. Whether books are
        // stacked into series is a question about the LIST; whether a heading already names the
        // author is a question about this card, and only the second one is this rule's business.
        unshelved = ctx.shelving == LibraryShelving.ITEM,
    )

/** The same, for a shelf: what most of its books agree on. */
internal fun shelfChip(series: LibraryEntry.Series, ctx: RowContext) =
    metaChipFor(
        series.books.shelfGenres(),
        series.author,
        ctx.shelving,
        // Same rule as a book's: a shelf card standing in an unshelved list has no heading over it
        // either, and two cards side by side should not describe themselves differently.
        unshelved = ctx.shelving == LibraryShelving.ITEM,
    )

/**
 * The chip an OPENED shelf wears: what it is, rather than what it is about.
 *
 * A reader who has just opened a shelf can see its books; what the header still has to answer is
 * whether this is a collection or one series, which the cover stack used to imply and no longer
 * does in list view.
 */
internal fun shelfKindChip(series: LibraryEntry.Series) = listOf(
    MetaChipKind.SHELF to listOf(
        (if (series.isCollection) BookState.IN_COLLECTION else BookState.IN_SERIES).key,
    ),
)

/** A chip's value as a filter token. */
internal fun chipToken(kind: MetaChipKind, value: String): FilterToken = when (kind) {
    MetaChipKind.GENRE -> FilterToken(FilterFacet.GENRE, value)
    MetaChipKind.AUTHOR -> FilterToken(FilterFacet.AUTHOR, value)
    // Not `collection:TKKG`, which would narrow the library to the shelf already on screen — the
    // state, so it answers "show me everything that is in a collection".
    MetaChipKind.SHELF -> FilterToken(FilterFacet.STATE, value)
}

/**
 * How many chip lines an item reserves in this arrangement.
 *
 * Two only where the rule can produce two — shelved by item, where no heading names either fact —
 * and only in the GRID, which has the height to spend and the narrow cells that make two pills side
 * by side unreadable. A list row is one line tall and the width of the screen, so there the two sit
 * beside each other exactly as they did.
 *
 * Read from the arrangement rather than from the book, so every card reserves the same space
 * whether or not it fills it, and the grid still lines up.
 */
internal val RowContext.chipLines: Int
    get() = if (gridView && shelving == LibraryShelving.ITEM) 2 else 1

/**
 * The number a book's corner shows — which depends entirely on what shelf it is standing on.
 *
 * A volume number is a claim about a book's place in something, so it is only worth showing while
 * that something is on screen. Four cases, and three of them used to give the same answer:
 *
 *  - **Flat.** Nothing. The arrangement has taken the shelves apart, so "#3" refers to a run the
 *    reader is not currently looking at — and next to a book numbered 3 of a different series it is
 *    actively misleading.
 *  - **A collection read as one numbered run.** The collection's own number: that IS the shelf.
 *  - **Grouped by series.** The series number, and nothing when the book has none. A standalone
 *    inside a collection used to fall back to the COLLECTION's number here, which put a number from
 *    the enclosing shelf on a book sitting outside every sub-series of it.
 *  - **Grouped by collection.** The series number if it has one — the more specific claim, and a
 *    Discworld witches novel is "Die Hexen #3" before it is "Scheibenwelt #12" — otherwise the
 *    collection's, which is the shelf it is on.
 */
internal fun volumeIndexFor(book: BookListItem, ctx: RowContext): Int? = when {
    ctx.series == LibraryDepth.FLAT -> null
    ctx.collectionNumbered -> book.collectionIndex
    ctx.series == LibraryDepth.SERIES -> book.seriesIndex
    else -> book.seriesIndex ?: book.collectionIndex
}

/** What the arrangement already tells the reader, so a row can say something else instead. */
@Immutable
internal data class RowContext(
    val shelving: LibraryShelving,
    val series: LibraryDepth,
    /**
     * Which view these rows are being drawn in.
     *
     * Only the expanding genre chip asks: a card has height to grow into and a row has width, so
     * the two expand in different directions — see [MetaChipStrip].
     */
    val gridView: Boolean = true,
    /**
     * Whether the shelf these books are sitting on is counting the COLLECTION rather than the series.
     *
     * Set only for the books inside a collection being read as one numbered run. The corner shows
     * one number and the shelf is the context that says which — so when the shelf is the collection,
     * the number has to be the collection's, even for a book that also belongs to a thread.
     */
    val collectionNumbered: Boolean = false,
    /**
     * The interface's locale, for the labels that are translated by Homer rather than by Android.
     *
     * Carried on the context rather than read where it is needed because `bookMeta` is not a
     * composable — it builds strings for a row to draw, and a genre label has to re-resolve when
     * the interface language changes, so the locale has to arrive from the composition that has it.
     */
    val locale: Locale = Locale.ENGLISH,
)

/**
 * The line under a book's title: the facts the current arrangement is NOT already showing.
 *
 * Shelved by author, the author is the section heading — repeating it on every row beneath is
 * noise, so the line carries the genre instead. Shelved by genre, the reverse. Shelved by item,
 * both. A series book loose in the list adds its episode number, which a stacked shelf conveys by
 * position and therefore omits. Length last, whenever it is known.
 *
 * Plain text rather than chips: the genre pill and the tag bubbles gave every row a different
 * height depending on whether that book happened to have either.
 */
private fun bookMeta(
    book: BookListItem,
    ctx: RowContext,
    context: android.content.Context,
    withStatus: Boolean = false,
    /**
     * False where the cover already carries the length — the grid card, whose bottom-right corner
     * now says it. Repeating it two lines below would spend one of the meta line's two lines
     * restating what is on screen an inch above.
     */
    withDuration: Boolean = true,
    /**
     * True in LIST view, where the cover no longer carries a corner badge for it. False on the grid
     * card, whose cover does — saying it in both places would put the same fact twice on one card.
     */
    withOffline: Boolean = false,
    /**
     * False on the grid card, whose cover corner now carries the number. Same rule as
     * [withDuration]: whatever a corner says, the text beside it stops saying.
     */
    withIndex: Boolean = true,
): String = buildList {
    // The length leads. It is the one number a reader scans a list FOR — "have I got an hour for
    // this" — and it was last, after the author and the genre they can already see from the shelf
    // they are standing on.
    if (withDuration) book.totalDurationMs?.takeIf { it > 0 }?.let { add(formatCompactDuration(it)) }
    // Then whether this device HAS it, which is the other half of "can I listen to this now" and so
    // belongs beside the length rather than after the tags. Same slot in seriesMeta — see there.
    if (withOffline && book.isDownloaded) add(context.getString(R.string.details_offline))
    // The author, unless something else on the card is already saying it: the heading overhead when
    // the shelf IS the author, or the chip when it took the name. Asked of `metaChipFor` rather
    // than re-derived here — one rule, subtracted, so the two cannot drift into printing the name
    // twice or dropping it from both.
    if (ctx.shelving != LibraryShelving.AUTHOR && !bookChip(book, ctx).carriesAuthor()) {
        add(book.author ?: context.getString(R.string.unknown_author))
    }
    // Genres are the chip's, and only the chip's. They used to be joined into this line with the
    // author and the tags, which on a narrow cell meant three ellipsised genres and no author.
    //
    // The language used to sit here too, as a two-letter marker shown only on a library holding
    // more than one. It was the odd one out: every other fact on the line is about the book as a
    // thing to listen to, and a row that carries "DE" on some libraries and not on others is a fact
    // that comes and goes by context. It is a search filter, which is where a language is actually
    // useful — `language:de` — and it will get a place of its own when there is one worth giving it.
    if (withIndex && ctx.series == LibraryDepth.FLAT && book.series != null && book.seriesIndex != null) {
        add(context.getString(R.string.home_meta_series_position, book.seriesIndex))
    }
    addAll(book.tags)
    if (withStatus) {
        when {
            book.finished -> add(context.getString(R.string.status_finished))
            // Gated on `started`, not merely on progress being computable: marking a book
            // completed RESETS it to position 0, which is measurable, so progress comes out 0f
            // rather than null and the row went on advertising "0%" for a book just cleared.
            book.started && book.progress != null ->
                add(context.getString(R.string.home_meta_percent_bare, (book.progress * 100).toInt()))
        }
    }
}.joinToString(" · ")

// ── the stack ────────────────────────────────────────────────────────────────
//
// Up to four real covers, receding to the top-right at 45°, inside a padded cell.
//
// Five earlier versions and what each got wrong, because the constraints only became visible as they
// conflicted:
//
//  1. Three full covers fanned across the cell — cost the front book a third of its width and its
//     height to show two others at an unreadable angle.
//  2. Front cover inset from all four sides, fanning up — artwork floating with slack on every edge.
//  3. Anchored bottom-left, stepping 13dp across for 4dp up — filled the cell, but the wide
//     horizontal step took the cover to about 1:1.79.
//  4. Equal 4dp fan filling the cell exactly — right proportions, but flush to the card's border on
//     two sides, and the sheets were blank stock so a 4dp sliver of nothing said very little.
//  5. Same, at the list's proportional step — the fan grew, and filling the cell still meant the
//     front cover read as a smaller object than the standalone beside it.
//
// **The sheets are real artwork now, and that is what pays for everything else.** A 4dp sliver of an
// actual cover carries what 7dp of blank stock could not, so the fan can stay small while the pile
// reads unmistakably as a pile of books. It costs up to three more image loads per series cell —
// which is exactly why they were blank, and is the trade being made deliberately.
//
// The stack no longer fills the cover space: [StackPad] holds it off the card's own border on every
// side, so a shelf reads as a stack sitting IN the cell rather than as one cropped by it.
//
// **Room is reserved for the maximum, and the drawn stack is CENTRED in what is left.** A shelf of
// two draws two covers, not four — but its front cover is the same size as an eight-volume shelf's,
// because the space the missing sheets would have taken is split evenly around what is drawn instead
// of left empty in the top-right. That is what keeps a row of series cards from going ragged while
// still letting the pile say how much is in it.

/** Held off the card's border on every side, so the stack is in the cell rather than cropped by it. */
private val StackPad = 4.dp

/** The cut between covers in the pile. */
private val StackEdge = 1.5.dp

/**
 * A shelf row's whole stack footprint — a book row's cover exactly, so the two line up.
 *
 * Square is right here rather than a compromise: a book row's own cover is 46dp square, so the list
 * crops everything to 1:1 already and a shelf that did otherwise would be the odd one out.
 */
internal val ShelfRowBox = 46.dp
internal val RowStackPad = 2.dp
internal val RowStackEdge = 1.dp

/** A series as a grid cell the same size as a book card: one cover with a pile receding behind it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeriesGridCard(
    series: LibraryEntry.Series,
    ctx: RowContext,
    onOpen: () -> Unit,
    actions: BookActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                // See SeriesShelfRow: a shelf's cell is outlined a step brighter than a book's.
                .border(1.dp, LineShelf, RoundedCornerShape(10.dp)),
        ) {
            // The cell is square by construction, so one dimension is all of them.
            val sheets = series.stackSheets(CoverStack.GridSteps.size)
            val pile = CoverStack.place(maxWidth, StackPad, CoverStack.GridSteps, sheets.size)
            // Front-first from `place`, drawn in reverse so the deepest lands first and the front
            // cover ends up on top.
            val models = listOf(series.frontCover()) + sheets
            for ((index, at) in pile.positions.withIndex().reversed()) {
                CoverArt(
                    model = models[index],
                    modifier = Modifier
                        .offset(x = at.x, y = at.y)
                        .size(pile.cover)
                        .clip(RoundedCornerShape(9.dp))
                        .border(StackEdge, Studio, RoundedCornerShape(9.dp)),
                )
            }
            // The badges belong to the CELL, not to the front cover.
            //
            // They were briefly moved onto the front cover so they would hug the artwork instead of
            // floating in the band the stack recedes into. That put them 8dp inside the card, so a
            // shelf's corners sat somewhere different from a book's and the grid lost its alignment.
            // A badge is furniture on the ITEM's cover space, and the stack is a drawing inside that
            // space — running off the cell's own edge is what makes a badge read as part of the card.
            ShelfBadge(
                isCollection = series.isCollection,
                count = series.books.size,
                modifier = Modifier.align(Alignment.TopStart),
                size = BadgeSize.LARGE,
            )
            // The count comes along only when it is not all of them: "12 of 12 downloaded" is said
            // better by the icon alone.
            val downloaded = series.books.count { it.isDownloaded }
            if (downloaded > 0) {
                OfflineBadge(
                    CoverCorner.TOP_END,
                    count = downloaded.takeIf { it < series.books.size }?.toString(),
                    modifier = Modifier.align(Alignment.TopEnd),
                    size = BadgeSize.LARGE,
                )
            }
            seriesTotalMs(series)?.let {
                DurationBadge(
                    formatCompactDuration(it),
                    corner = CoverCorner.BOTTOM_START,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
            // Same corner as a book's, so the control a reader reaches for is in one place whatever
            // kind of card they are looking at.
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                CoverMenuButton { menuOpen = true }
                SeriesMenu(series, menuOpen, actions) { menuOpen = false }
            }
        }
        GridCardText(
            title = series.name,
            meta = seriesCardMeta(series, ctx, LocalContext.current),
            // What most of its books shelve under, then everything else any of them carries —
            // so a shelf can say "Krimi +3" where no single volume carries four.
            chip = {
                MetaChipSlot(
                    chips = shelfChip(series, ctx),
                    ctx = ctx,
                    onFilter = { kind, value -> actions.onFilter(chipToken(kind, value)) },
                    lines = ctx.chipLines,
                )
            },
        )
    }
}

/**
 * The line under a stacked shelf's cover in the grid.
 *
 * A folded shelf in the grid and a folded shelf in the list are the same object saying the same
 * thing, so this is [seriesMeta]'s folded form and nothing else. It used to be its own two-branch
 * rule about the author, which is how the two views came to disagree the moment either was touched.
 */
private fun seriesCardMeta(
    series: LibraryEntry.Series,
    ctx: RowContext,
    context: android.content.Context,
): String = seriesMeta(series, ctx, context, expanded = false)
