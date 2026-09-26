package com.geozelot.homer.data.library

/**
 * One author's name: the form it is SHOWN in, and the key it FILES under.
 *
 * ## Storage is one form; showing is a choice
 *
 * Whatever is stored — what a tagger wrote, what a path template captured, what somebody typed —
 * a name is held given-name-first. [displayAuthor] is the rule that makes that true for values it
 * did not choose: **a comma means the surname came first**, so it flips. That is a rule and not a
 * guess — a comma cannot occur inside a name written the normal way round, which is precisely why
 * the edit field separates several authors with a semicolon (see AuthorList.kt).
 *
 * What a reader SEES is then a setting, and off by default. "Pratchett, Terry" is a filing
 * convention rather than a way of saying somebody's name, so a library that renders it is showing
 * its index — but a reader who has chosen to file by surname is reading an index on purpose, and
 * having the shelf headings disagree with the order they are in is its own kind of wrong. So the
 * option exists, and it exists only there: it is offered alongside filing by surname, because
 * without that it would put names back to front in a list that is not in that order.
 *
 * ## Filing is a sort method first
 *
 * [authorSortKey] puts the surname in front so that ordering by it files Pratchett under P. It is
 * never stored: it exists to be compared. That split is what lets the surname order be a heuristic
 * at all — get "Ursula K. Le Guin" wrong and a shelf is in the wrong place, which a reader can see
 * and correct, rather than a name being rendered wrongly for ever. [filedAuthor] renders the same
 * split for the eye, which is why they share one [splitName] and cannot disagree.
 *
 * The heuristic is deliberately small. Particles travel with the surname (`Le Guin`, `van Gogh`,
 * `von Humboldt`), generational suffixes do not file (`Jr.`), and a single-word name is its own
 * key (`Homer`). Anything it cannot parse falls back to the whole name, which orders it under its
 * first letter — wrong, but wrong in the way an unsorted list is wrong rather than in a way that
 * hides the book.
 */

/** Particles that belong to the surname rather than standing before it. */
private val SurnameParticles = setOf(
    "van", "von", "de", "del", "della", "der", "den", "di", "da", "dos", "du",
    "la", "le", "les", "ten", "ter", "af", "av", "bin", "ibn", "mac", "mc", "o'",
)

/** Endings that are not part of a surname and must not file under their own letter. */
private val NameSuffixes = setOf("jr", "jr.", "sr", "sr.", "ii", "iii", "iv", "phd", "ph.d.", "md")

/**
 * The name as Homer shows it: first name last, whatever form it arrived in.
 *
 * Splits on the FIRST comma only, so "Smith, John" flips cleanly and a name carrying more than one
 * comma is left alone rather than rearranged into something nobody wrote.
 */
fun displayAuthor(name: String, surnameFirst: Boolean = false): String {
    val trimmed = name.trim()
    val givenFirst = if (trimmed.count { it == ',' } != 1) {
        trimmed
    } else {
        val (surname, rest) = trimmed.split(',', limit = 2).map { it.trim() }
        if (surname.isEmpty() || rest.isEmpty()) trimmed else "$rest $surname"
    }
    // Normalised to one form FIRST, then turned around if asked. Flipping the stored text directly
    // would leave a name that already read "Pratchett, Terry" untouched and one that read "Terry
    // Pratchett" flipped, so a shelf would carry both spellings of the same convention.
    return if (surnameFirst) filedAuthor(givenFirst) else givenFirst
}

/**
 * The name as an index writes it: "Pratchett, Terry".
 *
 * Built from the same [splitName] the sort key uses, so what a reader sees and what the list is
 * ordered by cannot disagree — a heading reading "Le Guin, Ursula K." is under L because that is
 * the same split that put it there.
 *
 * A name with nothing in front of the surname is left as it is: "Homer, " is not a name.
 */
fun filedAuthor(name: String): String {
    val parts = splitName(name)
    val rest = listOf(parts.given, parts.trailing).filter { it.isNotEmpty() }.joinToString(" ")
    return when {
        parts.surname.isEmpty() -> name.trim()
        rest.isEmpty() -> parts.surname
        else -> "${parts.surname}, $rest"
    }
}

/** A name in the three pieces both the sort key and the filed form are built from. */
private class NameParts(val surname: String, val given: String, val trailing: String)

/**
 * Where the surname starts and ends.
 *
 * The heuristic, in one place. Particles travel with the surname, generational suffixes file with
 * nothing, and a single-word name is its own surname.
 */
private fun splitName(name: String): NameParts {
    val tokens = displayAuthor(name).split(' ', '\t').map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return NameParts("", "", "")
    if (tokens.size == 1) return NameParts(tokens[0], "", "")

    // A generational suffix files with nothing: "Sammy Davis Jr." is under D.
    var last = tokens.lastIndex
    while (last > 0 && tokens[last].lowercase() in NameSuffixes) last--
    if (last == 0) return NameParts(tokens[0], "", tokens.drop(1).joinToString(" "))

    // Particles travel with the surname, and there may be two ("Ludwig van der Berg").
    var first = last
    while (first > 1 && tokens[first - 1].lowercase() in SurnameParticles) first--

    return NameParts(
        surname = tokens.subList(first, last + 1).joinToString(" "),
        given = tokens.subList(0, first).joinToString(" "),
        trailing = tokens.subList(last + 1, tokens.size).joinToString(" "),
    )
}

/**
 * The key the name files under: surname first, lowercased, for comparison only.
 *
 * Takes [displayAuthor]'s form as its input, so a stored "Pratchett, Terry" and a stored "Terry
 * Pratchett" file identically — which they must, being the same person.
 */
fun authorSortKey(name: String): String = with(splitName(name)) {
    listOf(surname, given, trailing).filter { it.isNotEmpty() }.joinToString(" ").lowercase()
}
