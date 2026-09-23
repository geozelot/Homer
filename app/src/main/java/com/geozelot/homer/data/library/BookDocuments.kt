package com.geozelot.homer.data.library

/**
 * The PDFs that belong to a book — a booklet, a libretto, a map, a score.
 *
 * ## Discovery costs nothing extra
 *
 * The crawl already lists every file in every folder, not just the audio: that is how a cover is
 * found, and how a cover sitting at the book level is found while the audio sits under `CD1/`. The
 * same listing yields the PDFs. No extra request, no second pass.
 *
 * ## Which folder a book takes its documents from
 *
 * **The nearest one that has any, starting at the book's own folder and climbing.** A booklet
 * usually sits beside the audio; a series map sits at the series folder and belongs to every book
 * under it, exactly as a cover would if a cover climbed.
 *
 * Nearest-wins rather than collect-everything, for the same reason the cover picks ONE image: a
 * book that showed its own booklet plus three unrelated maps from two levels up would be a control
 * whose contents nobody can predict from where they put the file. With this rule the answer is
 * always "the closest PDFs win", which is a sentence a reader can hold.
 *
 * ## The library root is where the climb stops, and stops SHORT
 *
 * The root is excluded deliberately. It is the one folder that is nobody's book in particular, and
 * it is where a stray PDF is most likely to be sitting — a manual, an export, something a sync
 * client dropped. Included, one such file would attach itself to every book in the library that has
 * none of its own, which is a false positive with the widest possible blast radius.
 *
 * A book's OWN folder always counts, even in the degenerate library whose root directly holds the
 * audio: there the root is the book, and a booklet beside it is plainly that book's.
 */

/**
 * The document paths for a book, nearest folder first. Empty when nothing was found.
 *
 * [folderDocuments] is keyed by folder path (library-root-relative or absolute — the same space as
 * [bookPath] and [libraryRoot], whichever the caller is working in), and holds only folders that
 * actually contain a document.
 */
fun documentPathsFor(
    bookPath: String,
    libraryRoot: String,
    folderDocuments: Map<String, List<String>>,
): List<String> {
    val root = libraryRoot.trim('/')
    var current = bookPath.trim('/')
    folderDocuments[current]?.takeIf { it.isNotEmpty() }?.let { return it }
    while (current.contains('/')) {
        current = current.substringBeforeLast('/')
        if (current == root) break
        folderDocuments[current]?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return emptyList()
}

/**
 * The stored form as a list, in order. Empty when there is nothing stored.
 *
 * Newline-delimited in one column, the same shape authors, genres and tags already use — and for
 * the same reason: a path cannot contain a newline, so nothing has to be escaped.
 */
fun decodeDocuments(raw: String?): List<String> =
    raw?.split('\n')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()

/** The list as a stored value, or null for none. */
fun encodeDocuments(values: List<String>): String? =
    values.map { it.trim() }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString("\n")

/**
 * What a document is called on a button: its file name, without the extension.
 *
 * `Booklet.pdf` reads as "Booklet", which is what whoever named the file meant it to say. Not
 * prettified further — a file named `DG_4795234_booklet` is unhelpful, and inventing a better name
 * for it would only make a DIFFERENT unhelpful name that no longer matches what is on the server.
 */
fun documentLabel(path: String): String =
    path.substringAfterLast('/').substringBeforeLast('.').ifBlank { path.substringAfterLast('/') }
