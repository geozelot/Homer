package com.geozelot.homer.data.library

/** Recognized audiobook file types. Opus/Ogg are first-class (chapter-split books). */
object AudioFormats {
    val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "m4b", "aac", "opus", "ogg", "oga", "flac", "wav",
    )
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()

    fun isAudio(name: String): Boolean = extensionOf(name) in AUDIO_EXTENSIONS

    fun isImage(name: String): Boolean = extensionOf(name) in IMAGE_EXTENSIONS

    /**
     * Natural (numeric-aware) filename comparison so `2 - foo.mp3` sorts before
     * `10 - foo.mp3`. Case-insensitive.
     *
     * A **total** order, which matters more here than it looks. This decides `sortIndex`, which is
     * persisted and published in the shared index — so a comparator that called two different names
     * equal would leave their order to whatever the server happened to list first, and two devices
     * reading the same folder could disagree about which chapter comes second. Leading zeros were
     * exactly that case: `1.mp3` and `01.mp3` compare equal on every rule below, so the raw strings
     * break the tie at the end.
     */
    fun naturalCompare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ni = i
                while (ni < a.length && a[ni].isDigit()) ni++
                var nj = j
                while (nj < b.length && b[nj].isDigit()) nj++
                val na = a.substring(i, ni).trimStart('0').ifEmpty { "0" }
                val nb = b.substring(j, nj).trimStart('0').ifEmpty { "0" }
                val cmp = if (na.length != nb.length) na.length - nb.length else na.compareTo(nb)
                if (cmp != 0) return cmp
                i = ni
                j = nj
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        val byRemainder = (a.length - i) - (b.length - j)
        if (byRemainder != 0) return byRemainder
        // Equal under every rule above — but not necessarily the same string. Anything left is a
        // difference the rules deliberately ignore (leading zeros, letter case), and it still has to
        // order deterministically.
        return a.compareTo(b)
    }
}
