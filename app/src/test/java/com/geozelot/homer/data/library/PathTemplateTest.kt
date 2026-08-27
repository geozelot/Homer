package com.geozelot.homer.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The template compiler, and the two rules that stop it being quietly wrong: a text field never
 * crosses a `/`, and a template either fits a path entirely or produces nothing.
 */
class PathTemplateTest {

    private fun parse(template: String, path: String) =
        PathTemplate.compile(template)?.parse(path)

    // ── the shape ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the conventional layout parses`() {
        val got = parse("{author}/{series}/{title}", "Pratchett/Rincewind/Sourcery")
        assertEquals("Pratchett", got?.get(TemplateField.AUTHOR))
        assertEquals("Rincewind", got?.get(TemplateField.SERIES))
        assertEquals("Sourcery", got?.get(TemplateField.TITLE))
    }

    @Test
    fun `a text field does not cross a slash`() {
        // The rule that stops a library parsing one level too shallow: this path has three
        // segments and a two-field template must NOT claim it with the title eating two of them.
        assertNull(parse("{author}/{title}", "Pratchett/Rincewind/Sourcery"))
    }

    @Test
    fun `literals between fields are what pin a pattern down`() {
        val got = parse("{title} ({series} {index})", "Sourcery (Rincewind 2)")
        assertEquals("Sourcery", got?.get(TemplateField.TITLE))
        assertEquals("Rincewind", got?.get(TemplateField.SERIES))
        assertEquals("2", got?.get(TemplateField.INDEX))
    }

    @Test
    fun `the bracketed collection case from a real library`() {
        val got = parse("{author}/{title} [{collection}]", "Pratchett/Sourcery [Discworld]")
        assertEquals("Sourcery", got?.get(TemplateField.TITLE))
        assertEquals("Discworld", got?.get(TemplateField.COLLECTION))
    }

    @Test
    fun `a numeric field refuses letters`() {
        assertNull(parse("{title} #{index}", "Sourcery #two"))
        assertNotNull(parse("{title} #{index}", "Sourcery #2"))
    }

    @Test
    fun `the ignore wildcard absorbs without capturing`() {
        val got = parse("{*}/{author}/{title}", "Audiobooks/Pratchett/Sourcery")
        assertEquals("Pratchett", got?.get(TemplateField.AUTHOR))
        assertEquals("Sourcery", got?.get(TemplateField.TITLE))
        assertEquals(2, got?.size)
    }

    @Test
    fun `a partial fit produces nothing at all`() {
        // Half a match would fill some fields from the path and leave the rest at whatever they
        // were, which is the hardest kind of wrong result to notice.
        assertNull(parse("{author}/{series}/{title}", "Pratchett/Sourcery"))
    }

    @Test
    fun `leading and trailing slashes do not matter`() {
        assertNotNull(parse("{author}/{title}", "/Pratchett/Sourcery/"))
    }

    @Test
    fun `surrounding whitespace is trimmed out of a captured value`() {
        assertEquals("Sourcery", parse("{author} - {title}", "Pratchett -  Sourcery ")?.get(TemplateField.TITLE))
    }

    // ── refusing to compile ──────────────────────────────────────────────────────────────────

    @Test
    fun `a field this build does not have refuses to compile`() {
        // Rather than treating it as literal text, which would match nothing and look like the
        // template simply did not work.
        assertNull(PathTemplate.compile("{author}/{narrator}/{title}"))
    }

    @Test
    fun `a template with no fields is not a template`() {
        assertNull(PathTemplate.compile("Audiobooks/Fiction"))
        assertNull(PathTemplate.compile(""))
    }

    @Test
    fun `regex metacharacters in the literals are matched literally`() {
        // The user is writing wildcards, not patterns: a dot is a dot.
        assertNull(parse("{author}.{title}", "PratchettXSourcery"))
        assertNotNull(parse("{author}.{title}", "Pratchett.Sourcery"))
    }

    // ── the default ladder ───────────────────────────────────────────────────────────────────

    @Test
    fun `the defaults reproduce the detector's own rules`() {
        val d = PathTemplate.DEFAULTS
        assertEquals(
            "Rincewind",
            PathTemplate.parseFirst("Pratchett/Rincewind/Sourcery", d)?.get(TemplateField.SERIES),
        )
        assertEquals(
            "Discworld",
            PathTemplate.parseFirst("Pratchett/Discworld/Rincewind/Sourcery", d)
                ?.get(TemplateField.COLLECTION),
        )
        assertEquals(
            "Sourcery",
            PathTemplate.parseFirst("Pratchett/Sourcery", d)?.get(TemplateField.TITLE),
        )
        assertEquals("Sourcery", PathTemplate.parseFirst("Sourcery", d)?.get(TemplateField.TITLE))
    }

    @Test
    fun `the most specific default claims a path the shallower ones would also fit`() {
        // Order is the whole contract: {author}/{title} matches a two-segment path, and would have
        // claimed a three-segment one too if a text field could cross a slash.
        val got = PathTemplate.parseFirst("Pratchett/Rincewind/Sourcery", PathTemplate.DEFAULTS)
        assertEquals("Rincewind", got?.get(TemplateField.SERIES))
    }

    @Test
    fun `a user template is tried before the defaults`() {
        val mine = PathTemplate.compile("{author}/{title} ({collection})")!!
        val got = PathTemplate.parseFirst(
            "Pratchett/Sourcery (Discworld)",
            listOf(mine) + PathTemplate.DEFAULTS,
        )
        assertEquals("Discworld", got?.get(TemplateField.COLLECTION))
        assertEquals("Sourcery", got?.get(TemplateField.TITLE))
    }

    // ── arbitrary depth ──────────────────────────────────────────────────────────────────────

    @Test
    fun `a library nested five deep keeps every field`() {
        // The positional rules counted from both ends and ignored the middle. Without a
        // multi-segment wildcard this path would match no default and lose everything.
        val got = PathTemplate.parseFirst(
            "Pratchett/Audiobooks/Discworld/Rincewind/Sourcery",
            PathTemplate.DEFAULTS,
        )
        assertEquals("Pratchett", got?.get(TemplateField.AUTHOR))
        assertEquals("Discworld", got?.get(TemplateField.COLLECTION))
        assertEquals("Rincewind", got?.get(TemplateField.SERIES))
        assertEquals("Sourcery", got?.get(TemplateField.TITLE))
    }

    @Test
    fun `the multi-segment wildcard also matches nothing at all`() {
        val got = PathTemplate.parseFirst("Pratchett/Discworld/Rincewind/Sourcery", PathTemplate.DEFAULTS)
        assertEquals("Discworld", got?.get(TemplateField.COLLECTION))
        assertEquals("Rincewind", got?.get(TemplateField.SERIES))
    }

    @Test
    fun `a three-segment path still falls through to the three-field default`() {
        // The deep template needs four levels, so this must not be claimed by it with an empty
        // collection — it is a plain author/series/title library.
        val got = PathTemplate.parseFirst("Pratchett/Rincewind/Sourcery", PathTemplate.DEFAULTS)
        assertEquals(null, got?.get(TemplateField.COLLECTION))
        assertEquals("Rincewind", got?.get(TemplateField.SERIES))
    }

    // ── the engine difference no JVM test can exercise ───────────────────────────────────────

    @Test
    fun `every brace in the placeholder pattern is escaped`() {
        // Android's java.util.regex is ICU-backed and REJECTS a lone `}`; the JVM accepts it as a
        // literal. So `\{([a-zA-Z*]+)}` compiled fine in every test here and threw
        // PatternSyntaxException on a device — and being a companion val, that surfaced as
        // ExceptionInInitializerError and killed the app the moment anything touched this class.
        //
        // The compiled Regex cannot be asserted on, because this JVM is exactly the engine that
        // does not mind. The SOURCE can: every brace must carry a backslash.
        val src = PathTemplate.PLACEHOLDER_SOURCE
        src.forEachIndexed { i, c ->
            if (c == '{' || c == '}') {
                assertTrue(
                    "unescaped '$c' at $i in \"$src\" — ICU will reject this on a device",
                    i > 0 && src[i - 1] == '\\',
                )
            }
        }
    }

    @Test
    fun `the placeholder still matches what it is supposed to`() {
        // Escaping must not have changed what it means.
        assertNotNull(PathTemplate.compile("{author}/{title}"))
        assertEquals(
            "Pratchett",
            PathTemplate.compile("{author}/{title}")?.parse("Pratchett/Sourcery")
                ?.get(TemplateField.AUTHOR),
        )
    }
}
