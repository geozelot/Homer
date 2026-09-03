package com.geozelot.homer.data.library

import com.geozelot.homer.data.metadata.BookGenre
import java.util.Locale

/**
 * A book's genres, which are now several rather than one.
 *
 * ## Why the column did not change
 *
 * The genre is stored newline-delimited in the same single `genre` column, exactly the way `tags`
 * already is. A list of one round-trips as the bare string it always was, so every row written by
 * every earlier build reads back correctly and there is no migration. The alternative — a real
 * relation, or a new column — buys nothing here: nothing queries genres in SQL, and the shelf and
 * the filter both read the whole book anyway.
 *
 * ## Order carries meaning, which is why this is a List and not a Set
 *
 * **The first genre is the one the book shelves under.** A book with three genres appearing under
 * three headings would stop the genre shelf being a partition — the counts above each heading would
 * no longer sum to the library, a reader scrolling would meet the same book three times, and the
 * grid's item keys (built from the book id) would collide. So one of them has to be primary, and
 * making it the first one means the choice is expressed by typing rather than by a second control.
 *
 * Every genre filters. That half needs no compromise: `valuesFor` already returned a list, so a
 * `genre:` token matching any of a book's genres costs nothing and is what somebody means.
 *
 * ## Detected values are never split
 *
 * [decode] splits on newlines only. A tag reading "Fantasy, Humor" stays one genre, because a comma
 * inside an ID3 genre frame is the tagger's punctuation and not a delimiter we get to reinterpret.
 * Splitting on commas happens where somebody TYPES commas — [fromInput], driven by the edit dialog,
 * the same rule the tags field has always used.
 */

/** The stored form as a list, in order. Empty when there is nothing stored. */
fun decodeGenres(raw: String?): List<String> =
    raw?.split('\n')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()

/** The list as a stored value, or null for none — so "no genres" stays an absence, not an empty string. */
fun encodeGenres(values: List<String>): String? =
    values.map { it.trim() }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString("\n")

/**
 * The genre a book shelves and displays under: the first one it carries.
 *
 * Null rather than "" when it carries none, because the shelf distinguishes "no genre" as its own
 * heading and an empty string would sort in among the real ones.
 */
fun primaryGenre(raw: String?): String? = decodeGenres(raw).firstOrNull()

/**
 * What somebody typed into the genre field, as a stored value.
 *
 * Commas separate, whitespace around them does not count, and duplicates are dropped — typing
 * "Fantasy, fantasy" is a slip rather than two genres, and so is "Kurzgeschichten, Short Stories"
 * once both resolve to one entry in the vocabulary.
 */
fun genresFromInput(input: String): String? {
    val seen = LinkedHashMap<String, String>()
    for (part in input.split(',')) {
        // CANONICALISED, so an edit stores the vocabulary's key rather than the spelling that was
        // typed: somebody entering "Kurzgeschichten" and somebody entering "Short Stories" file the
        // book on the same shelf, in every interface language, without either of them being told
        // which spelling was the blessed one. A genre the vocabulary does not know is kept verbatim.
        val value = BookGenre.canonical(part)
        if (value.isEmpty()) continue
        // Keyed on the CANONICAL form, so the two spellings of one genre also cannot both survive
        // as duplicates of each other.
        seen.putIfAbsent(BookGenre.fold(value).ifEmpty { value.lowercase() }, value)
    }
    return encodeGenres(seen.values.toList())
}

/**
 * Stored genres as the edit field shows them: translated labels, comma-separated.
 *
 * Not the stored values. Those are canonical keys now, and a field reading `shortstories,
 * radioplay` is Homer's bookkeeping leaking onto the screen. A genre the vocabulary does not know
 * has no label and appears as written, which is what a tag supplied.
 */
fun genresToInput(genres: List<String>, locale: Locale): String =
    genres.joinToString(", ") { BookGenre.display(it, locale) }
