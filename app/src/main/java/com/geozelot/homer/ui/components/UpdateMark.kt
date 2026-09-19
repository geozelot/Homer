package com.geozelot.homer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.SectionLabel

// ── An update is waiting ─────────────────────────────────────────────────────
//
// Two marks, one fact. The dot says an update EXISTS, from a screen that has no room to say
// anything else about it; the pill says where to go for it, on the row that leads there.
//
// Both last until the update is installed rather than until the reader has looked at them. A mark
// that clears on being seen has to remember having been seen, and one that says the update is gone
// when it is not is worse than one that nags — so neither of these remembers anything. They read
// `UpdateState.pendingRelease`, which is null again the moment a newer Homer is the one running.

/** How big the dot is, and how far it overhangs the glyph it sits on. */
private val DotSize = 7.dp

/**
 * A gold dot in the top-right corner of whatever it wraps.
 *
 * Wrapping rather than a modifier because the dot has to sit OUTSIDE the icon's bounds to read as a
 * badge rather than as part of the glyph, and a 48dp icon button has the room for it.
 */
@Composable
fun UpdateDot(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        content()
        if (visible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(DotSize)
                    .clip(CircleShape)
                    .background(Amber)
                    // DECORATION, deliberately. Given a description of its own it became a second
                    // stop in the traversal — "An update is available" with no role and nothing to
                    // activate, sitting beside the button that actually leads there. The button
                    // says both things instead; see the settings icon's contentDescription.
                    .clearAndSetSemantics { },
            )
        }
    }
}

/** "New version", for the row that leads to the updater. */
@Composable
fun UpdatePill(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.update_pill_new),
        style = SectionLabel,
        color = Amber,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        modifier = modifier
            .clip(CircleShape)
            .background(AmberSoft)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
