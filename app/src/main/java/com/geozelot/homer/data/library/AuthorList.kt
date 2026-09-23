package com.geozelot.homer.data.library

/**
 * A book's authors, which are now several rather than one.
 *
 * ## The column did not change, for the reasons [decodeGenres] did not change it
 *
 * Newline-delimited in the same `author` column, exactly as genres and tags already are. A list of
 * one round-trips as the bare string it always was, so every row written by every earlier build
 * reads back correctly and there is no migration — and the `.homer` structure facet carries the
 * same string, so nothing on the wire changes either.
 *
 * The one cost, which genres paid silently and is worth naming here: an older Homer on another
 * device reads the whole string, so a two-author book shows up there as one author with a line
 * break in it. Ugly on that device, harmless to the data, and it corrects itself the moment that
 * device updates.
 *
 * ## The first author is the one the book files under
 *
 * Same argument as genres: a book appearing under three author headings would stop the author shelf
 * being a partition — the counts above each heading would no longer sum to the library, and a
 * reader scrolling would meet the same book three times. So one has to be primary, and making it
 * the first means the choice is expressed by typing rather than by a second control.
 *
 * Every author FILTERS, though. `valuesFor(AUTHOR)` returns them all, so `author:` finds a book by
 * its second name as readily as its first, which is what somebody looking for a co-author means.
 *
 * ## Why the separator is a semicolon and not a comma
 *
 * Tags are typed comma-separated, and authors cannot be. A name may legitimately contain a comma —
 * "Pratchett, Terry" is how somebody files a name deliberately, and the per-book override exists
 * precisely so they can — and a comma separator would silently split that one author into two.
 *
 * So the field takes `Pratchett, Terry; Neil Gaiman` and reads it as two people. Splitting on the
 * character that cannot appear inside the value costs a placeholder's worth of explanation once,
 * where splitting on the one that can costs a corrupted name nobody notices until the shelf has two
 * headings for one author.
 */

/** The stored form as a list, in order. Empty when there is nothing stored. */
fun decodeAuthors(raw: String?): List<String> =
    raw?.split('\n')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()

/** The list as a stored value, or null for none — so "no author" stays an absence, not an empty string. */
fun encodeAuthors(values: List<String>): String? =
    values.map { it.trim() }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString("\n")

/**
 * The author a book files and displays under: the first one it carries.
 *
 * Null rather than "" when it carries none, because the shelf distinguishes "no author" as its own
 * heading and an empty string would sort in among the real ones.
 */
fun primaryAuthor(raw: String?): String? = decodeAuthors(raw).firstOrNull()

/**
 * The stored form as the list Homer SHOWS: every name first-name-last, whatever was stored.
 *
 * Normalised here, once, on the way out of storage — so a template that captured "Pratchett,
 * Terry" and a tag that wrote "Terry Pratchett" reach the shelf, the cards, the filter and its
 * suggestions as the same person, under the same heading. The stored value is never touched; see
 * [displayAuthor].
 */
fun displayAuthors(raw: String?): List<String> = decodeAuthors(raw).map(::displayAuthor)

/** What the edit field's text means — see the header on why this is not a comma. */
fun authorsFromInput(text: String): List<String> =
    text.split(';').map { it.trim() }.filter { it.isNotBlank() }

/** The list as the edit field shows it. */
fun authorsToInput(values: List<String>): String = values.joinToString("; ")
