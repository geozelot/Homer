package com.geozelot.homer.data.library

/**
 * A pattern that reads a book's fields out of its path.
 *
 * `{author}/{series}/{title}` is the convention [BookDetector] has always hardcoded; written down
 * as a template it stops being a special case and becomes the default entry in a list the user can
 * add to. That is the point of the whole feature: a library whose folders do not follow the
 * convention — or one that carries the sub-series in brackets in the title, as `Sourcery (Rincewind
 * 2)` does — can say so, instead of Homer guessing and being wrong in a way nothing can correct.
 *
 * **Wildcards, never regular expressions.** The user writes `{field}` and literal text; the literals
 * between the fields are what pin it down. Regex is what this replaces, so it never appears: a
 * pattern that will not compile is a typo somebody can see, not a character class they cannot.
 *
 * A text field matches within ONE path segment — it will not swallow a `/`. Without that
 * `{author}/{title}` would match a three-segment path with the title eating two of them, and a
 * library would silently parse one level too shallow.
 */
class PathTemplate private constructor(
    val source: String,
    private val regex: Regex,
    private val slots: List<Slot>,
) {

    /**
     * One capture in the pattern, and what to do with what it caught.
     *
     * A slot is not the same thing as a [TemplateField]: three of them can target the same field.
     * `{author}` takes a segment whole, `{author_surname}` takes a PIECE of a name that another
     * slot completes, and `{authors[;]}` takes a run that is several names at once. All three end
     * up in the `author` column, because that is where an author lives — the distinction is how the
     * path spells it, not what it means, and keeping it out of [TemplateField] keeps it out of
     * every consumer downstream that only ever wanted the field.
     */
    internal class Slot(
        val field: TemplateField,
        /** Set when this slot is one HALF of a name; the halves are joined after the match. */
        val part: NamePart? = null,
        /** Split the capture on this, when the field holds several values. Null = one value. */
        val delimiter: String? = null,
        /** Read each value with this, for a list whose items have a shape of their own. */
        val each: PathTemplate? = null,
        /**
         * How the values of a list are stored — set only on the plural fields, so its presence is
         * what MAKES a slot a list. Carried on the slot rather than chosen by a `when` over the field,
         * which needed a default branch nothing could reach and would have given any future plural
         * field the wrong column codec silently.
         */
        val codec: ((List<String>) -> String?)? = null,
    ) {
        val list: Boolean get() = codec != null
    }

    /** Which half of a name a slot caught. */
    internal enum class NamePart { GIVEN, SURNAME }
    /**
     * What [path] says under this template, or null if it does not fit the shape.
     *
     * Null rather than a partial map: a template that matched half a path would fill some fields
     * from the path and leave others at whatever they were, which is the hardest kind of wrong
     * result to notice.
     */
    fun parse(path: String): Map<TemplateField, String>? {
        val match = regex.matchEntire(path.trim('/')) ?: return null
        val out = LinkedHashMap<TemplateField, String>()
        var given: String? = null
        var surname: String? = null
        // groupValues[0] is the whole match, so the slots start at 1 and stay in template order.
        slots.forEachIndexed { i, slot ->
            val captured = match.groupValues.getOrNull(i + 1)?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@forEachIndexed
            when {
                slot.part == NamePart.GIVEN -> given = captured
                slot.part == NamePart.SURNAME -> surname = captured
                slot.list -> encodeList(slot, captured)?.let { out[slot.field] = it }
                else -> out[slot.field] = captured
            }
        }
        // A name caught in halves becomes the one name everything else stores: given name first,
        // in the column that already holds it. Homer keeps names first-last in storage and decides
        // how to SHOW them separately, so a folder filed `Pratchett, Terry` and one filed
        // `Terry Pratchett` reach the library as the same person.
        //
        // Skipped when a list slot already answered for the author: a template naming both is
        // saying the same thing twice, and the list is the more specific claim.
        if (TemplateField.AUTHOR !in out) {
            listOfNotNull(given, surname).takeIf { it.isNotEmpty() }
                ?.let { out[TemplateField.AUTHOR] = it.joinToString(" ") }
        }
        return out
    }

    /**
     * A captured run as the stored list form: split, each item read, then newline-joined.
     *
     * An item the sub-pattern cannot read falls back to the raw text rather than being dropped.
     * A list is very often mixed — `Pratchett, Terry - Neil Gaiman` — and the halves that do not
     * match a shape are still names; [displayAuthor] straightens them out on the way to the screen
     * anyway. Dropping them would lose an author to a punctuation mismatch, silently.
     */
    private fun encodeList(slot: Slot, captured: String): String? {
        val items = slot.delimiter?.let { captured.split(it) } ?: listOf(captured)
        val values = items.mapNotNull { item ->
            val trimmed = item.trim()
            if (trimmed.isEmpty()) null else slot.each?.parse(trimmed)?.get(slot.field) ?: trimmed
        }
        return slot.codec?.invoke(values)
    }

    override fun toString(): String = source

    companion object {
        /**
         * `{name}`, and nothing else — a brace that opens and never closes is a typo, not a pattern.
         *
         * **Both braces are escaped, and the closing one is not optional.** Android's
         * `java.util.regex` is backed by ICU, which REJECTS a lone `}` outright; the JVM's engine
         * accepts it as a literal. So the unescaped form compiles perfectly in unit tests and throws
         * `PatternSyntaxException` on a device — and because this is a companion-object `val`, that
         * throw came out as `ExceptionInInitializerError` and took the whole class down with it, so
         * opening the template editor (or running any scan) killed the app.
         *
         * No JVM test can catch that difference, which is why [PLACEHOLDER_SOURCE] is exposed and
         * asserted on as a plain string instead.
         */
        internal const val PLACEHOLDER_SOURCE = """\{([a-zA-Z_*]+)((?:\[[^\[\]]*\])*)\}"""

        private val PLACEHOLDER = Regex(PLACEHOLDER_SOURCE)

        /**
         * One `[…]` group after a field name. Several may follow, and their ORDER does not matter.
         *
         * A group is a sub-pattern when it contains a brace and a delimiter when it does not, which
         * is unambiguous because a delimiter with a `{` in it would be a delimiter nobody could
         * type by accident. That is what lets both of these mean what they look like:
         *
         *  - `{authors[;]}` — several authors, separated by a semicolon
         *  - `{authors[{author_surname}, {author_firstname}]}` — one author, written surname-first
         *  - `{authors[ - ][{author_surname}, {author_firstname}]}` — both at once
         *
         * A `]` cannot appear inside either, which is the price of not needing an escape character
         * in a syntax whose whole point is that it is not a regular expression.
         */
        private val BRACKET = Regex("""\[([^\[\]]*)\]""")

        /**
         * Compiles [template], or returns null if it names a field this build does not have.
         *
         * Null rather than ignoring the unknown field: a template mentioning `{narrator}` was
         * written by somebody expecting narrators to be read, and quietly treating it as literal
         * text would match nothing and look like the template simply did not work.
         */
        fun compile(template: String): PathTemplate? {
            val trimmed = template.trim().trim('/')
            if (trimmed.isEmpty()) return null
            val slots = mutableListOf<Slot>()
            val pattern = StringBuilder()
            var cursor = 0
            for (m in PLACEHOLDER.findAll(trimmed)) {
                pattern.append(Regex.escape(trimmed.substring(cursor, m.range.first)))
                val name = m.groupValues[1]
                if (name == "**") {
                    // Any number of whole segments, including none. This is what lets one template
                    // describe a library nested arbitrarily deep, the way the positional rules it
                    // replaces always did — they counted from both ends and did not care what was
                    // in the middle. Written to consume the following slash as well, so the
                    // template reads `{author}/{**}/{series}/{title}` rather than needing the
                    // author's slash to be optional.
                    pattern.append("(?:[^/]+/)*?")
                    // …and swallow the literal slash the template puts after it, since this
                    // alternative already ends on one.
                    cursor = m.range.last + if (trimmed.getOrNull(m.range.last + 1) == '/') 2 else 1
                    continue
                }
                if (name == "*") {
                    // One segment, absorbed and not captured — "there is something here I do not
                    // care about".
                    pattern.append("[^/]+?")
                } else {
                    val groups = BRACKET.findAll(m.groupValues[2]).map { it.groupValues[1] }.toList()
                    val patterns = groups.filter { it.contains('{') }
                    val delimiters = groups.filterNot { it.contains('{') }
                    // Refused rather than quietly narrowed. A second delimiter or a second shape
                    // has no meaning, and taking the first of each and dropping the rest is exactly
                    // the kind of "it compiled, so it must work" this syntax exists to avoid: the
                    // templates preview would show one author for "A, B" and nothing would say why.
                    if (patterns.size > 1 || delimiters.size > 1) return null
                    val each = patterns.firstOrNull()?.let { compile(it) ?: return null }
                    val delimiter = delimiters.firstOrNull()?.takeIf { it.isNotEmpty() }
                    val slot = when (name.lowercase()) {
                        // The halves of a name. A folder reading `Pratchett, Terry` is a perfectly
                        // ordinary way to file an author and had no way to be read at all:
                        // `{author}` took the whole thing, comma and all, and the library grew a
                        // heading for a person whose name appeared to start with their surname.
                        "author_firstname" -> Slot(TemplateField.AUTHOR, part = NamePart.GIVEN)
                        "author_surname" -> Slot(TemplateField.AUTHOR, part = NamePart.SURNAME)
                        // The plural forms. Both columns have held several values since genres and
                        // authors became lists; only the path had no way to say so.
                        "authors" -> Slot(TemplateField.AUTHOR, delimiter = delimiter, each = each, codec = ::encodeAuthors)
                        "genres" -> Slot(TemplateField.GENRE, delimiter = delimiter, each = each, codec = ::encodeGenres)
                        else -> TemplateField.from(name)?.let { Slot(it) } ?: return null
                    }
                    // Brackets only mean something on the plural fields. On `{genre[, ]}` — the
                    // singular, by one missing letter — they were dropped without a word, and the
                    // whole segment "Krimi, Thriller" became one genre. Refusing to compile is what
                    // the unknown-field rule above already does for a typo, for the same reason.
                    if (groups.isNotEmpty() && !slot.list) return null
                    slots += slot
                    pattern.append(if (slot.field.numeric) "(\\d+)" else "([^/]+?)")
                }
                cursor = m.range.last + 1
            }
            pattern.append(Regex.escape(trimmed.substring(cursor)))
            if (slots.isEmpty()) return null
            return runCatching { PathTemplate(trimmed, Regex(pattern.toString()), slots) }.getOrNull()
        }

        /**
         * The rules [BookDetector] has always applied, in the order it applied them.
         *
         * Most specific first, because the first template that fits wins and `{author}/{title}`
         * would otherwise claim a path that `{author}/{series}/{title}` describes properly. This is
         * the same `segments.size >= 3` / `>= 2` ladder the detector compiled in, written down.
         */
        val DEFAULTS: List<PathTemplate> = listOfNotNull(
            // Three or more segments: author first, title last, series directly above it, and
            // anything in between ignored. `{**}` is how the positional rule survives being written
            // as a pattern — it counted from both ends and did not care about the middle, so
            // without it a library nested five deep would match no default and lose every field.
            //
            // **No default reads a COLLECTION.** It used to take the folder above the series, which
            // inferred a parent grouping from depth alone — and a library laid out
            // `Author/Genre/Series/Book` silently acquired "Fantasy" as a collection, behaving like
            // a parent nobody had asked for. A collection is a claim about what these books ARE,
            // not about how deep they sit, so it is now only ever something stated: by a template,
            // by assigning a series to one, or by editing a book. Guessing it is cheap to get wrong
            // and silent when it is.
            compile("{author}/{**}/{series}/{title}"),
            compile("{author}/{title}"),
            compile("{title}"),
        )

        /**
         * The first template in [templates] that [path] fits, and what it says.
         *
         * Order is the whole contract: the caller's own templates come before [DEFAULTS], so a
         * pattern somebody wrote for a folder beats the convention for that folder.
         */
        fun parseFirst(path: String, templates: List<PathTemplate>): Map<TemplateField, String>? =
            templates.firstNotNullOfOrNull { it.parse(path) }
    }
}

/** A field a template can pull out of a path. */
enum class TemplateField(val key: String, val numeric: Boolean = false) {
    AUTHOR("author"),
    TITLE("title"),
    SERIES("series"),
    INDEX("index", numeric = true),
    COLLECTION("collection"),
    COLLECTION_INDEX("collectionIndex", numeric = true),
    GENRE("genre"),
    LANGUAGE("language"),
    YEAR("year", numeric = true),
    ;

    companion object {
        fun from(key: String): TemplateField? = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

/**
 * A [PathTemplate] restricted to one folder of the library.
 *
 * [scope] is a library-relative folder prefix, or empty for the whole library. A book is a candidate
 * only when its id sits inside that folder — so "the German imports are laid out differently" is
 * expressible without the pattern having to be true of everything else.
 *
 * **The scope SELECTS; it does not strip.** The pattern is matched against the whole
 * library-relative path, scope segments included, so `{author}` still means the top folder. Stripping
 * the scope first would read better — a short pattern for a deep folder — and would quietly lose
 * every field the pattern no longer mentions: a scoped `{series}/{title}` under `Pratchett/Discworld`
 * would leave those books with no author at all, because the first matching template is the only one
 * that runs. Repeating the scope in the pattern is a small cost against that.
 */
data class ScopedTemplate(val scope: String, val template: PathTemplate) {
    /** [path]'s fields under this template, or null if the scope excludes it or the shape does not fit. */
    fun parse(path: String): Map<TemplateField, String>? {
        val clean = path.trim('/')
        if (!covers(clean)) return null
        return template.parse(clean)
    }

    /** Whether [path] is inside this template's folder. */
    fun covers(path: String): Boolean {
        val s = scope.trim('/')
        if (s.isEmpty()) return true
        // The trailing slash matters: scope "Pratchett" must not claim "PratchettAnthologies/x".
        return path.equals(s, ignoreCase = true) || path.startsWith("$s/", ignoreCase = true)
    }

    /** The stored form. Tab-separated because a tab cannot occur in a path or be typed into the field. */
    fun encode(): String = if (scope.isBlank()) template.source else "$scope\t${template.source}"

    companion object {
        /** The whole library, unscoped. */
        fun of(pattern: String): ScopedTemplate? =
            PathTemplate.compile(pattern)?.let { ScopedTemplate("", it) }

        fun decode(raw: String): ScopedTemplate? {
            val (scope, pattern) = if ('\t' in raw) {
                raw.substringBefore('\t').trim() to raw.substringAfter('\t').trim()
            } else {
                "" to raw.trim()
            }
            return PathTemplate.compile(pattern)?.let { ScopedTemplate(scope.trim('/'), it) }
        }

        /** The conventional layout, applying everywhere. */
        val DEFAULTS: List<ScopedTemplate> = PathTemplate.DEFAULTS.map { ScopedTemplate("", it) }

        /**
         * The first template that both covers [path] and fits it.
         *
         * A narrower scope earlier in the list wins, which is why the caller's own templates are
         * ordered ahead of [DEFAULTS] rather than merged into them.
         */
        fun parseFirst(path: String, templates: List<ScopedTemplate>): Map<TemplateField, String>? =
            templates.firstNotNullOfOrNull { it.parse(path) }
    }
}


/**
 * The stored template lines after taking on what the shared index carries.
 *
 * Pure, and tested, because this function can DELETE the user's work and the two ways it used to do
 * so were both invisible: it runs on a background pull, writes its result straight back to storage,
 * and nothing on screen says it happened.
 *
 * ## It works on RAW TEXT, and that is the point
 *
 * The previous version decoded every stored line into a [ScopedTemplate] first and dropped whatever
 * failed — then wrote the survivors back. So one pattern that no longer compiled was not merely
 * inert, it was **erased on the next sync**, silently and for good. Which is exactly what
 * `LibrarySettings.pathTemplates` promises never happens: templates are stored as text precisely so
 * that a pattern naming a field a later build renamed, or one with a typo in it, survives being
 * loaded and can be seen and fixed. A scope can be read off a line with a string split; compiling it
 * is not needed to decide where it belongs, so nothing here compiles anything.
 *
 * ## An empty remote rule is not an instruction to delete
 *
 * `TemplateRule.patterns` defaults to an empty list, so a rule whose field is absent, renamed or
 * unreadable arrives as "no patterns" rather than as an error. Assigning that over a local scope
 * would let one malformed shared file wipe a folder's templates on every device that reads it.
 * A remote scope only ever wins when it actually carries something.
 *
 * @param local the stored lines, `scope\tpattern` or a bare pattern for the whole library
 * @param remote the shared index's patterns per scope
 */
internal fun mergeTemplateLines(local: List<String>, remote: Map<String, List<String>>): List<String> {
    fun scopeOf(raw: String) = if ('\t' in raw) raw.substringBefore('\t').trim().trim('/') else ""
    fun patternOf(raw: String) = if ('\t' in raw) raw.substringAfter('\t').trim() else raw.trim()

    val merged = LinkedHashMap<String, List<String>>()
    // Whatever is here stays: the server has no opinion about a scope it has never held, and a
    // read-only share user CANNOT publish, so their own patterns are never up there to be echoed.
    for ((scope, lines) in local.filter { it.isNotBlank() }.groupBy(::scopeOf)) {
        merged[scope] = lines.map(::patternOf)
    }
    // …and a scope the server does hold wins, because it is the newer deliberate act.
    for ((scope, patterns) in remote) {
        val cleaned = patterns.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isNotEmpty()) merged[scope.trim().trim('/')] = cleaned
    }
    return merged.entries
        // Narrowest scope first, so a folder's own pattern is tried before a library-wide one.
        .sortedByDescending { it.key.length }
        .flatMap { (scope, patterns) ->
            patterns.map { if (scope.isBlank()) it else "$scope\t$it" }
        }
}
