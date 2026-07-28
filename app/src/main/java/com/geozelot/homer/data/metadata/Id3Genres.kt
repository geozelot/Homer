package com.geozelot.homer.data.metadata

/**
 * Resolves ID3v1 numeric genre codes to their names. Some files (and ID3v2 TCON frames that only
 * reference an ID3v1 code) carry a genre like `(12)` or `(12)Refinement` rather than a plain name;
 * Media3 surfaces that string verbatim, so a book would otherwise show "(12)" as its genre.
 */
object Id3Genres {

    /** Normalises a raw genre tag: decodes `(n)`/`n` codes, prefers refinement text, drops unknowns. */
    fun resolve(raw: String?): String? {
        val g = raw?.trim()?.ifBlank { null } ?: return null
        // "(12)" or "(12)Some Refinement"
        PARENTHESISED.find(g)?.let { m ->
            val refinement = m.groupValues[2].trim()
            if (refinement.isNotEmpty()) return refinement
            return NAMES.getOrNull(m.groupValues[1].toInt()) // null → unknown code, drop it
        }
        // A bare numeric code.
        g.toIntOrNull()?.let { return NAMES.getOrNull(it) }
        return g
    }

    private val PARENTHESISED = Regex("^\\((\\d+)\\)(.*)$")

    // The ID3v1 + Winamp-extended genre table (indices are the tag codes).
    private val NAMES = arrayOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz",
        "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno",
        "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno",
        "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental",
        "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise", "Alternative Rock", "Bass", "Soul",
        "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic",
        "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream",
        "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle",
        "Native US", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi",
        "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock",
        "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebob", "Latin", "Revival",
        "Celtic", "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock",
        "Symphonic Rock", "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour",
        "Speech", "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony", "Booty Bass", "Primus",
        "Porn Groove", "Satire", "Slow Jam", "Club", "Tango", "Samba", "Folklore", "Ballad",
        "Power Ballad", "Rhythmic Soul", "Freestyle", "Duet", "Punk Rock", "Drum Solo", "A cappella",
        "Euro-House", "Dance Hall",
    )
}
