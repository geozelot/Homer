package com.geozelot.homer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.geozelot.homer.R
import com.geozelot.homer.data.metadata.BookGenre
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.BookmarkKind
import com.geozelot.homer.data.metadata.BookLanguage
import com.geozelot.homer.ui.components.HomerIcons
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.LineShelf
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle
import com.geozelot.homer.ui.theme.Surface2

/**
 * Everything Homer knows about one book or one shelf, in one place.
 *
 * The library card has room for a title and two lines, so everything else Homer has learned — the
 * language it read off a filename, the genre out of a tag, how many files the book is, whether a
 * correction is overriding any of it — had nowhere to be seen. The edit dialog was the only place,
 * and it showed those facts as editable fields, which is a poor way to READ them: a form asks you
 * to change something, and most of the time somebody opening it only wants to look.
 *
 * So looking and changing are separated. This is the looking; Edit at the bottom is the changing.
 *
 * Rendered as a dialog rather than a nav destination for the same reason the edit dialogs are: a
 * shelf's identity is its `author|name` key, which does not survive being put in a route, and the
 * subject is re-derived from the live list on every recomposition so an edit made underneath is
 * reflected the moment it lands.
 */

/**
 * One labelled fact. Absent facts are not rendered — an empty row is a worse answer than no row.
 *
 * A fact that names a filterable axis is tappable, and tapping it filters the library to it. This is
 * what turns Details from a read-only panel into part of organising: you notice a book is Fantasy,
 * and the way to see the rest of your Fantasy is the word you are already looking at. Amber, because
 * everywhere else in Homer amber means "this does something".
 */
@Composable
private fun Fact(label: String, value: String?, onTap: (() -> Unit)? = null) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(vertical = 5.dp),
    ) {
        Text(
            label,
            color = Faint,
            fontSize = 11.sp,
            modifier = Modifier.width(96.dp).padding(end = 10.dp, top = 1.dp),
        )
        Text(
            value,
            color = if (onTap != null) Amber else Parchment,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

/**
 * One filterable fact, as a chip: a mark saying what kind of fact it is, and its value.
 *
 * ## Why the labels went
 *
 * These were labelled rows — a 96dp column reading "Author", "Genre", "Language", then the value.
 * Nine of them stacked is a form, and a third of the card's width was spent on words that never
 * change. The mark says the same thing in 11dp, which is what the mark is for, and it says it in
 * every language without being translated.
 *
 * ## Why chips and not rows
 *
 * Every one of these narrows the library to itself, and a thing you can press should look pressable.
 * As rows they were amber text, which in Homer means "this does something" — true, and invisible
 * next to eight other rows of text. It also lets a book with four genres be four chips instead of
 * one row reading "Krimi · Thriller · Hörspiel · Jugend", where only the first was tappable and
 * nothing said so.
 */
private data class DetailChip(
    val icon: ImageVector,
    /** What kind of fact this is — "Author", "Genre" — set in front of the value. */
    val category: String,
    val label: String,
    val token: FilterToken,
)

/**
 * A chip, with its category taken from the facet it filters on.
 *
 * The facet already owns that word — it is what the filter pills say, and it is translated — so
 * spelling it out here is reading it from one place rather than writing a second set of labels
 * that can drift from the first.
 */
@Composable
private fun chip(icon: ImageVector, facet: FilterFacet, label: String, value: String) =
    DetailChip(icon, stringResource(facet.label), label, FilterToken(facet, value))

/** The block of them, wrapping as it needs to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailChips(chips: List<DetailChip>, onFilter: (FilterToken) -> Unit) {
    if (chips.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { chip ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Surface2)
                    .border(1.dp, LineShelf, RoundedCornerShape(999.dp))
                    .clickable { onFilter(chip.token) }
                    .padding(start = 7.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(chip.icon, contentDescription = null, tint = Faint, modifier = Modifier.size(11.dp))
                // Mark, category, value. The mark alone carries it on a library card, where there
                // is no room for more and the reader is scanning; here they are reading, one card
                // at a time, and the word removes the last doubt about which fact is which — a
                // name is a name whether it belongs to a person, a series or a genre.
                Text(
                    chip.category,
                    color = Faint,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                )
                Text(
                    chip.label,
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A hairline between groups of facts, matching the settings pages' rhythm. */
@Composable
private fun FactDivider() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).height(1.dp).background(Line))
}

/**
 * How wide a details card should be, and how tall its body may grow.
 *
 * A stock dialog is sized for a sentence and two buttons. This one carries a cover, a block of
 * chips and a dozen facts, and on a large screen it was leaving half the width empty while wrapping
 * the chips into four rows.
 *
 * Proportions rather than fixed dp, because the thing it has to fit is the SCREEN: 94% of the width
 * up to a limit — past about 560dp a line of text stops being easier to read and starts being
 * harder — and at most three quarters of the height, so the card is always visibly a card sitting
 * on the library rather than a page that replaced it.
 */
@Composable
private fun detailsCardWidth(): Dp =
    (LocalConfiguration.current.screenWidthDp.dp * 0.94f).coerceAtMost(560.dp)

@Composable
private fun detailsBodyMaxHeight(): Dp = LocalConfiguration.current.screenHeightDp.dp * 0.62f

/** The cover and title block every details card opens with. */
@Composable
private fun DetailsHeader(cover: Any?, title: String, subtitle: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        CoverArt(
            model = cover,
            modifier = Modifier
                .width(72.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Line, RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.padding(start = 14.dp).align(Alignment.CenterVertically)) {
            Text(title, style = SerifTitle, color = Parchment, fontSize = 18.sp, lineHeight = 23.sp)
            subtitle?.let {
                Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

/** Everything known about one book. */
@Composable
fun BookDetailsCard(
    book: BookListItem,
    onEdit: () -> Unit,
    onFilter: (FilterToken) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(detailsCardWidth()),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = detailsBodyMaxHeight())
                    .verticalScroll(rememberScrollState()),
            ) {
                DetailsHeader(book.coverModel, book.title, book.author)

                FactDivider()

                // What this book IS, as chips — every one of them a filter. The label filtered ON
                // is the stored value, not the rendered one: a language chip reads "German" and
                // filters `de`, a genre chip reads "Krimi" and filters the canonical key.
                //
                // One chip per genre and one per tag, rather than one row listing them: a book with
                // four genres used to be a single row where only the first was tappable and nothing
                // said which.
                DetailChips(
                    buildList {
                        // Ordered: who wrote it, what it is part of, what it is in, what it is
                        // about. Widening from the book outwards, then the two facts that classify
                        // it — the same reading order the player's header follows.
                        book.author?.takeIf { it.isNotBlank() }?.let {
                            add(chip(HomerIcons.Author, FilterFacet.AUTHOR, it, it))
                        }
                        book.series?.takeIf { it.isNotBlank() }?.let { name ->
                            add(
                                chip(
                                    HomerIcons.SeriesBracket,
                                    FilterFacet.SERIES,
                                    book.seriesLine(context) ?: name,
                                    name,
                                ),
                            )
                        }
                        book.collection?.takeIf { it.isNotBlank() }?.let { name ->
                            add(
                                chip(
                                    HomerIcons.CollectionBracket,
                                    FilterFacet.COLLECTION,
                                    book.collectionLine(context) ?: name,
                                    name,
                                ),
                            )
                        }
                        book.language?.takeIf { it.isNotBlank() }?.let {
                            add(
                                chip(
                                    Icons.Filled.Language,
                                    FilterFacet.LANGUAGE,
                                    BookLanguage.displayName(it, locale),
                                    it,
                                ),
                            )
                        }
                        book.genres.forEach {
                            add(chip(HomerIcons.Genre, FilterFacet.GENRE, BookGenre.display(it, locale), it))
                        }
                        book.tags.forEach {
                            add(chip(Icons.Filled.Tag, FilterFacet.TAG, it, it))
                        }
                    },
                    onFilter,
                )

                FactDivider()

                // What there IS of it. Everything here is a measurement rather than a property, so
                // none of it is a filter and none of it is a chip — which is the line the two
                // blocks are divided on, and the same line the shelf card divides on.
                Fact(
                    stringResource(R.string.details_length),
                    book.totalDurationMs?.let { formatCompactDuration(it) }
                        ?: stringResource(R.string.details_length_unknown),
                )
                Fact(
                    stringResource(R.string.details_files),
                    pluralStringResource(R.plurals.details_file_count, book.fileCount, book.fileCount),
                )
                Fact(stringResource(R.string.details_progress), book.progressLine(context))
                Fact(stringResource(R.string.details_offline), book.offlineLine(context))

                FactDivider()

                // The path is the book's identity — it is the primary key, the fetch URL and the
                // key every shared facet uses. When something is wrong with a book this is the
                // first thing worth seeing, and it was visible nowhere in the app.
                Fact(stringResource(R.string.details_location), book.id)

                // …and if what is wrong is how that path was READ, this is the way out. Seeded from
                // here rather than authored from nothing: the folder is this book's and the shape is
                // whichever pattern is already matching, which is the one that needs changing.
            }
        },
        // Close in the confirm slot, which is where a dialog puts what most taps are for — and on
        // a card people open to LOOK at something, that is closing it again. Edit is the step
        // further in, and sits where a secondary action sits.
        confirmButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Amber) }
        },
        dismissButton = {
            HomerTextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit), color = Muted) }
        },
    )
}

/** Everything known about one shelf — a series, or a collection of them. */
@Composable
fun SeriesDetailsCard(
    series: LibraryEntry.Series,
    onEdit: () -> Unit,
    onFilter: (FilterToken) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(detailsCardWidth()),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = detailsBodyMaxHeight())
                    .verticalScroll(rememberScrollState()),
            ) {
                DetailsHeader(
                    series.frontCover(),
                    series.name,
                    stringResource(
                        if (series.isCollection) R.string.details_kind_collection else R.string.details_kind_series,
                    ),
                )

                FactDivider()

                // The same block a book gets, filled with what a shelf has: who wrote it, the
                // threads inside it, everything its books are about. Chips throughout, because
                // every one of them narrows the library the same way a book's do.
                DetailChips(
                    buildList {
                        series.author?.takeIf { it.isNotBlank() }?.let {
                            add(chip(HomerIcons.Author, FilterFacet.AUTHOR, it, it))
                        }
                        // Only a collection has threads inside it to name, and only when they are
                        // named. Each is its own chip: they are separate series, and a reader who
                        // wants the Watch books wants the Watch books.
                        if (series.isCollection) {
                            series.books.mapNotNull { it.series }.distinct().forEach {
                                add(chip(HomerIcons.SeriesBracket, FilterFacet.SERIES, it, it))
                            }
                        }
                        series.books.mapNotNull { it.language }.distinct().forEach {
                            add(
                                chip(
                                    Icons.Filled.Language,
                                    FilterFacet.LANGUAGE,
                                    BookLanguage.displayName(it, locale),
                                    it,
                                ),
                            )
                        }
                        series.books.flatMap { it.genres }.distinct().forEach {
                            add(chip(HomerIcons.Genre, FilterFacet.GENRE, BookGenre.display(it, locale), it))
                        }
                        series.books.flatMap { it.tags }.distinct().forEach {
                            add(chip(Icons.Filled.Tag, FilterFacet.TAG, it, it))
                        }
                    },
                    onFilter,
                )

                FactDivider()

                Fact(
                    stringResource(R.string.details_volumes),
                    pluralStringResource(R.plurals.home_series_book_count, series.books.size, series.books.size),
                )
                // Rule two, said out loud: a collection carrying volume numbers can be read straight
                // through, and one without them is a grouping and nothing more. It is the difference
                // between Discworld and Star Wars Legends, and the shelf itself cannot show it.
                if (series.isCollection) {
                    Fact(
                        stringResource(R.string.details_reading_order),
                        stringResource(
                            if (series.books.collectionHasReadingOrder()) {
                                R.string.details_reading_order_yes
                            } else {
                                R.string.details_reading_order_no
                            },
                        ),
                    )
                }
                Fact(
                    stringResource(R.string.details_length),
                    seriesTotalMs(series)?.let { formatCompactDuration(it) }
                        ?: stringResource(R.string.details_length_unknown),
                )
                Fact(
                    stringResource(R.string.details_offline),
                    series.books.count { it.isDownloaded }.let { done ->
                        when (done) {
                            0 -> context.getString(R.string.details_offline_none)
                            series.books.size -> context.getString(R.string.details_offline_all)
                            else -> context.getString(R.string.details_offline_some, done, series.books.size)
                        }
                    },
                )
                Fact(
                    stringResource(R.string.details_started),
                    series.books.count { it.started }.let { started ->
                        if (started == 0) {
                            context.getString(R.string.details_started_none)
                        } else {
                            context.getString(R.string.details_started_some, started, series.books.size)
                        }
                    },
                )

                FactDivider()

                // Scoped to the folder the shelf's books SHARE, which is the level a rule about
                // them belongs at — a whole series or collection read wrongly is the case a
                // template is most worth writing for.
                Fact(stringResource(R.string.details_location), series.commonFolder().ifBlank { "/" })
            }
        },
        // Close in the confirm slot, which is where a dialog puts what most taps are for — and on
        // a card people open to LOOK at something, that is closing it again. Edit is the step
        // further in, and sits where a secondary action sits.
        confirmButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Amber) }
        },
        dismissButton = {
            HomerTextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit), color = Muted) }
        },
    )
}

// ── the lines that need composing from more than one field ───────────────────────────────────

/** "Rincewind, #3" — the series and, when known, the position in it. */
private fun BookListItem.seriesLine(context: android.content.Context): String? {
    val name = series ?: return null
    return seriesIndex?.let { context.getString(R.string.details_series_at, name, it) } ?: name
}

/** The same for the collection, which usually has no index and often does not exist at all. */
private fun BookListItem.collectionLine(context: android.content.Context): String? {
    val name = collection ?: return null
    return collectionIndex?.let { context.getString(R.string.details_series_at, name, it) } ?: name
}

private fun BookListItem.progressLine(context: android.content.Context): String = when {
    finished -> context.getString(R.string.status_finished)
    !started -> context.getString(R.string.details_progress_unstarted)
    progress != null -> context.getString(R.string.home_meta_percent_bare, (progress * 100).toInt())
    else -> context.getString(R.string.details_progress_started)
}

private fun BookListItem.offlineLine(context: android.content.Context): String = when {
    isDownloaded -> context.getString(R.string.details_offline_all)
    downloadStatus != null ->
        context.getString(R.string.details_offline_some, downloadedFiles, fileCount)
    else -> context.getString(R.string.details_offline_none)
}

/**
 * A book's bookmarks, reached from the library rather than from the player.
 *
 * Bookmarks used to be visible only while the book was open, which is the one moment you do not
 * need them: the point of a bookmark is to get back to a place in a book you are NOT currently in.
 * Tapping one opens the book at it.
 *
 * Read-and-delete only. Adding a bookmark needs a position, and the position this dialog has is
 * whatever the player last saved — offering "add" here would silently bookmark somewhere the reader
 * is not, so the player keeps that job.
 */
@Composable
fun LibraryBookmarksDialog(
    book: BookListItem,
    bookmarks: List<BookmarkEntity>,
    onOpenAt: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // Cuts are chapter boundaries, not places somebody marked — they belong to the chapter list.
    val notes = bookmarks.filter { it.kind != BookmarkKind.CUT }.sortedBy { it.positionMs }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title, style = SerifTitle, color = Parchment, fontSize = 17.sp) },
        text = {
            if (notes.isEmpty()) {
                Text(stringResource(R.string.details_bookmarks_none), color = Muted, fontSize = 13.sp)
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    notes.forEach { mark ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenAt(mark.positionMs) }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    mark.label?.takeIf { it.isNotBlank() }
                                        ?: mark.chapterTitle.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.details_bookmark_untitled),
                                    color = Parchment,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    formatCompactDuration(mark.positionMs),
                                    color = Faint,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            IconButton(onClick = { onDelete(mark.id) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_clear),
                                    tint = Faint,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        // The way out sits in the confirm slot, in the accent — the same place and the same colour
        // in every dialog that exists to be read. See DetailsCard for the rule.
        confirmButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Amber) }
        },
    )
}
