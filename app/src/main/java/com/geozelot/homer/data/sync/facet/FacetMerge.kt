package com.geozelot.homer.data.sync.facet

/**
 * How two views of the shared library reconcile. Pure functions over the facet models, so every
 * rule here is testable without a device, a server or a database.
 *
 * The old catalog merged a whole book by one timestamp, which meant a device that knew the
 * durations and a device that knew the genre could not both be right: whichever wrote last erased
 * the other. Each facet now merges by the rule its own content justifies.
 */
object FacetMerge {

    // ── structure ────────────────────────────────────────────────────────────────────────────

    /**
     * Union the books, newest per book — then let the most recent COMPLETE crawl remove what it
     * proved is gone.
     *
     * Pruning is the only reason the crawl marker exists. Without it a union can never express a
     * deletion, which is why a book removed from the server used to reappear on the next sync: the
     * scanner dropped it locally, the union put it back, and the cycle repeated forever.
     */
    fun structure(local: StructureFacet, remote: StructureFacet): StructureFacet {
        val merged = LinkedHashMap<String, StructureBook>(local.books.size + remote.books.size)
        for (id in local.books.keys + remote.books.keys) {
            val l = local.books[id]
            val r = remote.books[id]
            merged[id] = when {
                l == null -> r!!
                r == null -> l
                r.updatedAt > l.updatedAt -> r
                else -> l
            }
        }

        // Which side carries the newer full crawl, and therefore the authoritative book set.
        val lm = local.lastFullCrawl
        val rm = remote.lastFullCrawl
        val useRemote = rm != null && (lm == null || rm.at > lm.at)
        val marker = if (useRemote) rm else lm
        val sawIds = if (useRemote) remote.books.keys else local.books.keys

        val kept = if (marker == null) {
            // Nobody has ever seen the whole tree. Deleting on that basis would be a guess.
            merged
        } else {
            merged.filterTo(LinkedHashMap()) { (id, book) ->
                // Absent from the crawl, but added after it ran: that crawl never had a chance to
                // see it, so its silence says nothing. Anything older than the crawl and unseen
                // by it is genuinely gone.
                id in sawIds || book.updatedAt > marker.at
            }
        }

        return StructureFacet(lastFullCrawl = marker, books = kept)
    }

    // ── derived ──────────────────────────────────────────────────────────────────────────────

    /**
     * Non-null wins, field by field.
     *
     * A duration is a fact about bytes: two devices reading the same file cannot legitimately
     * disagree. So a value beats no value, and on the rare genuine disagreement the newer book
     * entry wins — because agreement is the norm and difference means one side is stale. This is
     * what lets the facet carry no per-field timestamps, which would have tripled its size.
     */
    fun derived(local: DerivedFacet, remote: DerivedFacet): DerivedFacet {
        val merged = LinkedHashMap<String, DerivedBook>(local.books.size + remote.books.size)
        for (id in local.books.keys + remote.books.keys) {
            val l = local.books[id]
            val r = remote.books[id]
            merged[id] = when {
                l == null -> r!!
                r == null -> l
                else -> mergeDerivedBook(l, r)
            }
        }
        return DerivedFacet(books = merged)
    }

    private fun mergeDerivedBook(l: DerivedBook, r: DerivedBook): DerivedBook {
        // Who to believe when both sides have a value for the same field.
        val newer = if (r.updatedAt > l.updatedAt) r else l
        val older = if (newer === l) r else l
        return DerivedBook(
            genre = pick(l.genre, r.genre, newer.genre),
            totalDurationMs = pick(l.totalDurationMs, r.totalDurationMs, newer.totalDurationMs),
            // A fact about the server, not about either device: if one of them uploaded the art,
            // it is in the shared cache for everyone.
            hasCachedCover = l.hasCachedCover || r.hasCachedCover,
            chapterTier = pick(l.chapterTier, r.chapterTier, newer.chapterTier),
            // Chapters travel with the tier that established them, or the list would be free to
            // come from the side that never worked them out.
            chapters = when {
                l.chapterTier == null -> r.chapters
                r.chapterTier == null -> l.chapters
                else -> newer.chapters
            },
            // Union per file: a key present at all is a measurement, so nothing is ever lost by
            // merging two partial sweeps.
            fileDurationsMs = buildMap {
                putAll(older.fileDurationsMs)
                putAll(newer.fileDurationsMs)
            },
            updatedAt = maxOf(l.updatedAt, r.updatedAt),
        )
    }

    /** Non-null wins; [ifBoth] decides only when both sides actually have a value. */
    private fun <T> pick(l: T?, r: T?, ifBoth: T?): T? = when {
        l == null -> r
        r == null -> l
        else -> ifBoth
    }

    // ── corrections ──────────────────────────────────────────────────────────────────────────

    /**
     * Newest edit wins, per book.
     *
     * Per book rather than per field, because an edit is one deliberate act: a person opened a
     * book and set what it should say. Merging field by field would make *clearing* a correction
     * impossible to express — an absent field would always lose to the stale value still sitting
     * on the other side.
     */
    fun corrections(local: CorrectionsFacet, remote: CorrectionsFacet): CorrectionsFacet {
        val merged = LinkedHashMap<String, BookCorrection>(local.books.size + remote.books.size)
        for (id in local.books.keys + remote.books.keys) {
            val l = local.books[id]
            val r = remote.books[id]
            merged[id] = when {
                l == null -> r!!
                r == null -> l
                r.editedAt > l.editedAt -> r
                else -> l
            }
        }
        return CorrectionsFacet(books = merged)
    }
}
