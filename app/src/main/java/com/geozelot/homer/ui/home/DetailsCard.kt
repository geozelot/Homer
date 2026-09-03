package com.geozelot.homer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.data.metadata.BookGenre
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.BookmarkKind
import com.geozelot.homer.data.metadata.BookLanguage
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle

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

/** A hairline between groups of facts, matching the settings pages' rhythm. */
@Composable
private fun FactDivider() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).height(1.dp).background(Line))
}

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
    /**
     * Opens the template editor seeded for this book's folder — null where there is nothing to
     * seed, which is a reader device whose patterns are somebody else's to write.
     */
    onReadFolderDifferently: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DetailsHeader(book.coverModel, book.title, book.author)

                FactDivider()

                // The VALUE filtered on is the stored one, not the rendered one: the language row
                // reads "German" and filters on `de`.
                Fact(stringResource(R.string.details_author), book.author) {
                    book.author?.let { onFilter(FilterToken(FilterFacet.AUTHOR, it)) }
                }
                Fact(stringResource(R.string.details_series), book.seriesLine(context)) {
                    book.series?.let { onFilter(FilterToken(FilterFacet.SERIES, it)) }
                }
                Fact(stringResource(R.string.details_collection), book.collectionLine(context)) {
                    book.collection?.let { onFilter(FilterToken(FilterFacet.COLLECTION, it)) }
                }
                // Every genre, and tapping filters on the PRIMARY one. A single tap cannot mean
                // three tokens, and the first is the one the shelf agrees with.
                Fact(
                    stringResource(R.string.details_genre),
                    book.genres.takeIf { it.isNotEmpty() }
                        ?.joinToString(" · ") { BookGenre.display(it, locale) },
                ) {
                    book.genre?.let { onFilter(FilterToken(FilterFacet.GENRE, it)) }
                }
                Fact(
                    stringResource(R.string.details_language),
                    book.language?.let { BookLanguage.displayName(it, locale) },
                ) { book.language?.let { onFilter(FilterToken(FilterFacet.LANGUAGE, it)) } }
                Fact(stringResource(R.string.details_tags), book.tags.takeIf { it.isNotEmpty() }?.joinToString(" · "))

                FactDivider()

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
                onReadFolderDifferently?.let { open ->
                    Text(
                        stringResource(R.string.details_read_folder),
                        color = Amber,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = open)
                            .padding(top = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            HomerTextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit), color = Amber) }
        },
        dismissButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Muted) }
        },
    )
}

/** Everything known about one shelf — a series, or a collection of them. */
@Composable
fun SeriesDetailsCard(
    series: LibraryEntry.Series,
    onEdit: () -> Unit,
    onFilter: (FilterToken) -> Unit,
    /** Opens the template editor scoped to the folder this shelf's books share. Null for a reader. */
    onReadFolderDifferently: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DetailsHeader(
                    series.frontCover(),
                    series.name,
                    stringResource(
                        if (series.isCollection) R.string.details_kind_collection else R.string.details_kind_series,
                    ),
                )

                FactDivider()

                Fact(stringResource(R.string.details_author), series.author) {
                    series.author?.let { onFilter(FilterToken(FilterFacet.AUTHOR, it)) }
                }
                Fact(
                    stringResource(R.string.details_volumes),
                    pluralStringResource(R.plurals.home_series_book_count, series.books.size, series.books.size),
                )
                // Only a collection has threads inside it to name, and only when they are named.
                if (series.isCollection) {
                    Fact(
                        stringResource(R.string.details_subseries),
                        series.books.mapNotNull { it.series }.distinct()
                            .takeIf { it.isNotEmpty() }?.joinToString(" · "),
                    )
                    // Rule two, said out loud: a collection carrying volume numbers can be read
                    // straight through, and one without them is a grouping and nothing more. It is
                    // the difference between Discworld and Star Wars Legends, and the shelf itself
                    // cannot show it.
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
                    stringResource(R.string.details_genre),
                    series.books.flatMap { it.genres }.distinct().takeIf { it.isNotEmpty() }
                        ?.joinToString(" · ") { BookGenre.display(it, locale) },
                )
                Fact(
                    stringResource(R.string.details_language),
                    series.books.mapNotNull { it.language }.distinct()
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(" · ") { BookLanguage.displayName(it, locale) },
                )

                FactDivider()

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
                onReadFolderDifferently?.let { open ->
                    Text(
                        stringResource(R.string.details_read_folder),
                        color = Amber,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = open)
                            .padding(top = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            HomerTextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit), color = Amber) }
        },
        dismissButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Muted) }
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
        confirmButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Muted) }
        },
    )
}
