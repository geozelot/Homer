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
 * ## Which folders a book takes its documents from: ALL of them, up to the root
 *
 * The book's own folder, then the part folders its audio actually sits in, then every ancestor —
 * nearest first. A booklet beside the audio, a map at the series folder and a family tree at the
 * collection folder are three different documents about the same book, and a reader who filed them
 * at three levels meant all three to be reachable.
 *
 * This started as nearest-wins, on the reasoning that a predictable single source beats a pile
 * from three levels. That was wrong about what people actually file: a series map belongs to every
 * book in the series AND each book keeps its own booklet, and nearest-wins silently hid whichever
 * one was further away. Each document is labelled by its file name, so a list of three says what
 * the three are.
 *
 * ## The part folders, because that is where the audio is
 *
 * A book split across `CD1/`, `CD2/` is one book whose folder holds no audio at all. A PDF sitting
 * in `CD1/` beside the files is as much that book's as one at the book level — and climbing alone
 * never looks there, because climbing only ever goes up. Same reason the cover search reads the
 * part folders' images: the book is where its audio is.
 *
 * ## The library root is where the climb stops, and stops SHORT
 *
 * The root is excluded deliberately. It is the one folder that is nobody's book in particular, and
 * it is where a stray PDF is most likely to be sitting — a manual, an export, something a sync
 * client dropped. Included, one such file would attach itself to every book in the library, which
 * is a false positive with the widest possible blast radius.
 *
 * Everything below the root is fair game, which does mean a PDF in an author folder reaches every
 * book by that author. That is the same rule working — those books do share that folder — and it
 * is undone by moving the file down a level, where a root PDF's reach could not be.
 *
 * A book's OWN folder always counts, even in the degenerate library whose root directly holds the
 * audio: there the root is the book, and a booklet beside it is plainly that book's.
 */

/**
 * Every document path for a book, nearest folder first. Empty when nothing was found.
 *
 * [folderDocuments] is keyed by folder path (library-root-relative or absolute — the same space as
 * [bookPath] and [libraryRoot], whichever the caller is working in), and holds only folders that
 * actually contain a document.
 *
 * @param partPaths the book's own audio-bearing subfolders — `CD1`, `Part 2` — when it has any.
 *   Read straight after the book folder, because a PDF filed with the audio is the book's however
 *   deep the audio was put.
 */
fun documentPathsFor(
    bookPath: String,
    libraryRoot: String,
    folderDocuments: Map<String, List<String>>,
    partPaths: List<String> = emptyList(),
): List<String> {
    val root = libraryRoot.trim('/')
    // A set, ordered: the same folder can be reached twice (a part folder IS the book folder for a
    // book with no parts), and a document offered twice is a duplicate button.
    val found = LinkedHashSet<String>()
    var current = bookPath.trim('/')
    folderDocuments[current]?.let(found::addAll)
    partPaths.forEach { part -> folderDocuments[part.trim('/')]?.let(found::addAll) }
    while (current.contains('/')) {
        current = current.substringBeforeLast('/')
        if (current == root) break
        folderDocuments[current]?.let(found::addAll)
    }
    return found.toList()
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

/**
 * The labels for a whole set of documents, with collisions resolved by where they came from.
 *
 * Only a problem since documents stopped being taken from one folder: a book folder and its series
 * folder both very reasonably hold a `Booklet.pdf`, and two buttons both reading "Booklet" is a
 * choice nobody can make. The one that is not unique gains the folder it sits in — "Booklet ·
 * Discworld" — which is exactly the thing that distinguishes them, and is said only where it has
 * to be. Unique names are left alone.
 */
fun documentLabels(paths: List<String>): List<String> {
    val counts = paths.groupingBy(::documentLabel).eachCount()
    return paths.map { path ->
        val label = documentLabel(path)
        if (counts[label] == 1) {
            label
        } else {
            val folder = path.substringBeforeLast('/', "").substringAfterLast('/')
            if (folder.isBlank()) label else "$label · $folder"
        }
    }
}
