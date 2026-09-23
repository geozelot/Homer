package com.geozelot.homer.data.library

/**
 * One author's name: the form it is SHOWN in, and the key it FILES under.
 *
 * ## A name is always shown first-name-last
 *
 * Whatever is stored — what a tagger wrote, what a path template captured, what somebody typed —
 * an author reads "Terry Pratchett" everywhere Homer shows one. There is no display setting,
 * because there is nothing a reader would want to switch: "Pratchett, Terry" is a filing
 * convention, not a way of saying somebody's name, and a library that renders it is showing its
 * index rather than its books.
 *
 * [displayAuthor] is the one rule that makes that true for values it did not choose: **a comma
 * means the surname came first**, so it flips. That is a rule and not a guess — a comma cannot
 * occur inside a name written the normal way round, which is precisely why the edit field
 * separates several authors with a semicolon (see AuthorList.kt).
 *
 * ## Filing is a sort method, and only a sort method
 *
 * [authorSortKey] puts the surname in front so that ordering by it files Pratchett under P. It is
 * never displayed and never stored: it exists to be compared. That split is what lets the surname
 * order be a heuristic at all — get "Ursula K. Le Guin" wrong and a shelf is in the wrong place,
 * which a reader can see and correct, rather than a name being rendered wrongly for ever.
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
fun displayAuthor(name: String): String {
    val trimmed = name.trim()
    if (trimmed.count { it == ',' } != 1) return trimmed
    val (surname, rest) = trimmed.split(',', limit = 2).map { it.trim() }
    if (surname.isEmpty() || rest.isEmpty()) return trimmed
    return "$rest $surname"
}

/**
 * The key the name files under: surname first, lowercased, for comparison only.
 *
 * Takes [displayAuthor]'s form as its input, so a stored "Pratchett, Terry" and a stored "Terry
 * Pratchett" file identically — which they must, being the same person.
 */
fun authorSortKey(name: String): String {
    val tokens = displayAuthor(name).split(' ', '\t').map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return ""
    if (tokens.size == 1) return tokens[0].lowercase()

    // A generational suffix files with nothing: "Sammy Davis Jr." is under D.
    var last = tokens.lastIndex
    while (last > 0 && tokens[last].lowercase() in NameSuffixes) last--
    if (last == 0) return tokens[0].lowercase()

    // Particles travel with the surname, and there may be two ("Ludwig van der Berg").
    var first = last
    while (first > 1 && tokens[first - 1].lowercase() in SurnameParticles) first--

    val surname = tokens.subList(first, last + 1).joinToString(" ")
    val given = tokens.subList(0, first).joinToString(" ")
    val trailing = tokens.subList(last + 1, tokens.size).joinToString(" ")
    return listOf(surname, given, trailing).filter { it.isNotEmpty() }.joinToString(" ").lowercase()
}
