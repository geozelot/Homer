package com.geozelot.homer.data.update

/**
 * A Homer version name, ordered the way releases actually succeed one another.
 *
 * The app cannot compare `versionCode`s to decide whether a release is newer: CI derives the code
 * from the workflow run number, which is monotonic but appears nowhere in a tag or in a release
 * payload. The ordering therefore has to come from the NAME — and the name carries a pre-release
 * suffix, which is where the two obvious comparisons both go wrong:
 *
 *  - `1.1.0-BETA18` is OLDER than `1.1.0`. A pre-release leads up to its release, so the bare core
 *    wins. A plain string compare sorts the longer string last and would offer a downgrade from
 *    the finished release back onto the last beta.
 *  - `BETA9` is OLDER than `BETA18`. The digits compare as a number. A lexical compare sorts
 *    `BETA9` last and would stop offering updates the moment the beta count reached double figures.
 *
 * Parsing is deliberately forgiving — a name that cannot be understood must still order somewhere
 * sane rather than throw, because it arrives from a tag pushed by a human.
 */
class AppVersion private constructor(
    val raw: String,
    private val core: List<Long>,
    private val pre: List<Token>,
) : Comparable<AppVersion> {

    /** One run of a pre-release suffix: digits compare as a number, letters as text. */
    internal data class Token(val number: Long?, val text: String) : Comparable<Token> {
        override fun compareTo(other: Token): Int = when {
            number != null && other.number != null -> number.compareTo(other.number)
            // Semver: a numeric identifier always ranks below an alphanumeric one.
            number != null -> -1
            other.number != null -> 1
            else -> text.compareTo(other.text)
        }
    }

    /** True when this name carries a pre-release suffix (`1.1.0-BETA18`, `0.0.0-dev42`). */
    val isPrerelease: Boolean get() = pre.isNotEmpty()

    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(core.size, other.core.size)) {
            val c = core.getOrElse(i) { 0L }.compareTo(other.core.getOrElse(i) { 0L })
            if (c != 0) return c
        }
        // Same core: a pre-release leads up to its release, so no suffix outranks any suffix.
        if (pre.isEmpty() || other.pre.isEmpty()) return other.pre.size.compareTo(pre.size)
        for (i in 0 until maxOf(pre.size, other.pre.size)) {
            // A shorter suffix that is otherwise identical ranks first: BETA < BETA2.
            val a = pre.getOrNull(i) ?: return -1
            val b = other.pre.getOrNull(i) ?: return 1
            val c = a.compareTo(b)
            if (c != 0) return c
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0

    // Trailing zeros are dropped in [parse] precisely so equal versions hash alike here.
    override fun hashCode(): Int = 31 * core.hashCode() + pre.hashCode()

    override fun toString(): String = raw

    companion object {
        /** Never throws: an unparseable name degrades to zeros plus whatever text it carried. */
        fun parse(raw: String): AppVersion {
            val trimmed = raw.trim().removePrefix("v").removePrefix("V")
            // Build metadata (`+ci7`) is not part of the ordering, per semver.
            val withoutBuild = trimmed.substringBefore('+')
            val coreText = withoutBuild.substringBefore('-')
            val preText = withoutBuild.substringAfter('-', "")
            val core = coreText.split('.')
                .map { part -> part.filter(Char::isDigit).toLongOrNull() ?: 0L }
                // 1.1 and 1.1.0 are the same release; dropping trailing zeros keeps equals and
                // hashCode consistent for them.
                .dropLastWhile { it == 0L }
            return AppVersion(raw.trim(), core, tokenize(preText))
        }

        /** Splits a suffix into alternating digit and non-digit runs, separators discarded. */
        private fun tokenize(pre: String): List<Token> {
            val tokens = mutableListOf<Token>()
            var i = 0
            while (i < pre.length) {
                if (pre[i] in SEPARATORS) {
                    i++
                    continue
                }
                val digits = pre[i].isDigit()
                val start = i
                while (i < pre.length && pre[i] !in SEPARATORS && pre[i].isDigit() == digits) i++
                val run = pre.substring(start, i)
                // Lowercased so BETA and beta are one identifier, not two.
                tokens += if (digits) Token(run.toLongOrNull() ?: 0L, run) else Token(null, run.lowercase())
            }
            return tokens
        }

        private const val SEPARATORS = ".-_"
    }
}
