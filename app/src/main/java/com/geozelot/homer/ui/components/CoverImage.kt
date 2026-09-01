package com.geozelot.homer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Surface2

/**
 * Book cover, or a book-glyph placeholder when none is available. [model] may be a URL string
 * (folder cover image) or a ByteArray (embedded artwork) — Coil handles both.
 *
 * ## It FITS, and it never crops
 *
 * This drew with [ContentScale.Crop] into frames that were all 2:3 or thereabouts, on the reasonable
 * assumption that a book cover is shaped like a book. **Audiobook artwork is square** — Audible's is,
 * embedded M4B art is, and so is nearly every `cover.jpg` sitting in a book folder. Cropping a square
 * image to 2:3 discards a third of its height, centred, so it takes the top and the bottom: on art
 * laid out with the title above and the author below, it removed both and left the middle. Covers
 * looked cut because they were being cut, everywhere except the list rows, which were square already.
 *
 * The frames are square now, so the common case fills them exactly with nothing lost and no bands.
 * Fit is what handles the rest: a genuinely portrait cover is letterboxed rather than trimmed, which
 * is the right way round — a band of mount is a visible, honest choice, and a cropped title is a
 * defect that looks like the art was made badly.
 *
 * [Surface2] behind it is the mount. Without it the bands are whatever happens to be behind the
 * frame — the card, the Ground, or a stack sheet — and the cover reads as floating rather than
 * as artwork set into something.
 */
@Composable
fun CoverImage(model: Any?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Surface2), contentAlignment = Alignment.Center) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.fillMaxSize(0.4f),
            )
        }
    }
}
