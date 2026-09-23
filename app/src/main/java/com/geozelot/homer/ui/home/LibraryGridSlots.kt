package com.geozelot.homer.ui.home

// ── What the grid emits ──────────────────────────────────────────────────────
//
// One lazy item per slot, in this order, and NOTHING works out that order a second time.
//
// It used to be worked out inline: `libraryContent` walked `entries` and called `item()` a variable
// number of times per entry — one for a heading, one for a card, a banner plus a row each for an
// open shelf. That is fine while the grid is the only thing that cares. It stops being fine the
// moment something outside has to know WHERE an entry landed, because a lazy grid is addressed by
// index and the index is the running total of everything emitted before it.
//
// The fast-scroll lane is that something. Computing the same running total beside the code that
// emits would have been two descriptions of one layout, agreeing until the day somebody adds an
// item to one of them — and the failure would be a lane that scrolls to the wrong book, which
// nothing fails and no test sees.
//
// So the order is a value. [librarySlots] produces it, `libraryContent` draws it, and the lane
// indexes into it.

/** One lazy item. [key] is the grid's identity for it; [fullSpan] whether it takes the whole row. */
internal sealed interface GridSlot {
    val key: String
    val fullSpan: Boolean

    /** A shelf heading. */
    data class Heading(val entry: LibraryEntry.Header, override val key: String) : GridSlot {
        override val fullSpan get() = true
    }

    /** A book standing on its own: a cell in the grid, a row in the list. */
    data class Book(
        val book: BookListItem,
        override val key: String,
        override val fullSpan: Boolean,
    ) : GridSlot

    /** A closed shelf drawn as one card (grid), or as one row (list, open or closed). */
    data class Shelf(
        val series: LibraryEntry.Series,
        val open: Boolean,
        val flat: Boolean,
        override val key: String,
        override val fullSpan: Boolean,
    ) : GridSlot

    /** The banner an opened shelf wears in grid view. */
    data class ShelfBanner(
        val series: LibraryEntry.Series,
        val flat: Boolean,
        override val key: String,
    ) : GridSlot {
        override val fullSpan get() = true
    }

    /** One row inside an opened shelf — a sub-heading, or a run of its books. */
    data class ShelfEpisode(
        val series: LibraryEntry.Series,
        val row: ShelfRow,
        val flat: Boolean,
        /** Last row of this shelf, which is what closes the enclosure round it. */
        val last: Boolean,
        override val key: String,
    ) : GridSlot {
        override val fullSpan get() = true
    }
}

/**
 * The slots a library draws, in order.
 *
 * Pure, so the order can be asserted rather than believed — see `LibraryGridSlotsTest`.
 *
 * [isOpen] is passed in rather than read here because "open" lives in Compose state that the grid
 * owns; this function only needs the answer.
 */
internal fun librarySlots(
    entries: List<LibraryEntry>,
    gridView: Boolean,
    columns: Int,
    flatCollections: Set<String>,
    isOpen: (LibraryEntry.Series) -> Boolean,
): List<GridSlot> {
    // Two headings really can carry the same title — a book whose author metadata literally reads
    // "Unknown author" gets its own section beside the fallback one — and duplicate keys make the
    // lazy layout throw. Counting repeats of the title is unique and only changes when the titles
    // do, unlike a key built from how many rows happened to precede it.
    val headingOrdinals = HashMap<String, Int>()
    val slots = mutableListOf<GridSlot>()

    for (entry in entries) {
        when (entry) {
            is LibraryEntry.Header -> slots += GridSlot.Heading(
                entry,
                "header:${entry.title}#${headingOrdinals.merge(entry.title, 1, Int::plus)}",
            )

            is LibraryEntry.Standalone ->
                slots += GridSlot.Book(entry.book, entry.book.id, fullSpan = !gridView)

            is LibraryEntry.Series -> {
                val shelfKey = entry.expandKey
                val open = isOpen(entry)
                // Only a collection has two readings, and only one with threads AND numbers has a
                // choice worth offering — see CollectionOrderChip.
                val flat = entry.isCollection && entry.name in flatCollections
                if (gridView && open) {
                    slots += GridSlot.ShelfBanner(entry, flat, "series-open:$shelfKey")
                } else {
                    // Grid: a card in its cell. List: one full-width row, open or not — the shelf
                    // row IS the top slice of the enclosure its episodes continue.
                    slots += GridSlot.Shelf(entry, open, flat, shelfKey, fullSpan = !gridView)
                }
                if (!open) continue

                // One book per row in list view, so the same split yields one Books row each and
                // the sub-headings land between the threads.
                val rows = entry.expandedRows(if (gridView) columns else 1, flat = flat)
                val prefix = if (gridView) "se" else "ep"
                rows.forEachIndexed { index, row ->
                    slots += GridSlot.ShelfEpisode(
                        series = entry,
                        row = row,
                        flat = flat,
                        last = index == rows.lastIndex,
                        // First id in the row: unique across the library (a book sits in one
                        // series) and stable while the row's membership holds. A sub-heading keys
                        // on its own label, which is unique within the shelf.
                        key = when (row) {
                            is ShelfRow.SubHeader -> "${prefix}sub:$shelfKey:${row.label}"
                            ShelfRow.LooseHeader -> "${prefix}loose:$shelfKey"
                            is ShelfRow.Books -> "$prefix${if (gridView) "p" else ""}:${row.books.first().id}"
                        },
                    )
                }
            }
        }
    }
    return slots
}
