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
    private val fields: List<TemplateField>,
) {
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
        // groupValues[0] is the whole match, so the fields start at 1 and stay in template order.
        fields.forEachIndexed { i, field ->
            match.groupValues.getOrNull(i + 1)?.trim()?.takeIf { it.isNotEmpty() }?.let { out[field] = it }
        }
        return out
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
        internal const val PLACEHOLDER_SOURCE = """\{([a-zA-Z*]+)\}"""

        private val PLACEHOLDER = Regex(PLACEHOLDER_SOURCE)

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
            val fields = mutableListOf<TemplateField>()
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
                    val field = TemplateField.from(name) ?: return null
                    fields += field
                    pattern.append(if (field.numeric) "(\\d+)" else "([^/]+?)")
                }
                cursor = m.range.last + 1
            }
            pattern.append(Regex.escape(trimmed.substring(cursor)))
            if (fields.isEmpty()) return null
            return runCatching { PathTemplate(trimmed, Regex(pattern.toString()), fields) }.getOrNull()
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
