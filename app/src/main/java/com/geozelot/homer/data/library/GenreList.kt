package com.geozelot.homer.data.library


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
 * [decodeGenres] splits on newlines only. A tag reading "Fantasy, Humor" stays one genre, because a
 * comma inside an ID3 genre frame is the tagger's punctuation and not a delimiter we get to
 * reinterpret.
 *
 * Nothing here parses typed text any more. `genresFromInput`/`genresToInput` existed for a
 * comma-separated field, and the field is a picker over `BookGenre` now — so the values arrive as a
 * list that is already canonical, and the two functions went rather than staying as something the
 * app no longer calls.
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
