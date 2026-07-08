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
        return (a.length - i) - (b.length - j)
    }
}
