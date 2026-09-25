package com.geozelot.homer.ui.player

import androidx.compose.foundation.background
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.data.library.documentLabels
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.CoverImage
import com.geozelot.homer.ui.components.pressFeedback
import com.geozelot.homer.ui.components.rememberTapInteraction
import com.geozelot.homer.ui.components.tapTarget
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberDeep
import com.geozelot.homer.ui.theme.Studio
import com.geozelot.homer.ui.theme.TabularSmall

// ── Artwork ──────────────────────────────────────────────────────────────────

/** The cover, centered in whatever slot it's given and sized to fit it in both dimensions. */
@Composable
internal fun PlayerArtwork(
    model: Any?,
    /**
     * The sleep timer's remaining time, or null when none is running — read inside [SleepCountdown]
     * ONLY, because it changes every second and this composable holds the cover.
     */
    sleepRemainingMs: () -> Long?,
    /** The book's supplementary PDFs; empty for almost every book, and then no pill is drawn. */
    documents: List<String>,
    onOpenDocument: (String) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            // Swipe down anywhere on the artwork to collapse back to the mini-player.
            .pointerInput(Unit) {
                val threshold = 60.dp.toPx()
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragged > threshold) onCollapse()
                        dragged = 0f
                    },
                    onVerticalDrag = { _, delta -> dragged += delta },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Largest SQUARE cover that fits the slot both ways, floored so a pathological slot can't
        // reduce it to nothing (an unbounded slot height falls back to the width limit).
        //
        // It was 1:1.3, which cut 23% off a square cover — less brutal than the library's 2:3 but on
        // the one screen where the artwork IS the content, and where there is room to show all of it.
        // Whichever bound is smaller, and NO floor: this slot is whatever the cluster below left
        // over, so a cover insisting on a minimum would push the transport off the screen to keep
        // itself large. Small is a cost the artwork can bear; missing controls are not.
        val coverWidth = minOf(maxWidth * 0.82f, maxHeight)
        CoverImage(
            model = model,
            modifier = Modifier
                .width(coverWidth)
                .aspectRatio(1f)
                .shadow(
                    elevation = 30.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = Amber,
                    spotColor = AmberDeep,
                )
                .clip(RoundedCornerShape(14.dp)),
        )
        SleepCountdown(sleepRemainingMs, Modifier.align(Alignment.BottomCenter))
        DocumentsPill(
            documents = documents,
            onOpen = onOpenDocument,
            modifier = Modifier
                .align(Alignment.TopEnd)
                // Inset by the cover's own margin, so the pill sits ON the artwork rather than
                // beside it — `coverWidth` is 82% of the slot, and the pill has to come in by the
                // half that is left plus a little.
                // The 48dp target starts at the cover's top edge and centres the pill inside it,
                // so no top padding of its own.
                .padding(end = (maxWidth - coverWidth) / 2 + 10.dp),
        )
    }
}

/**
 * The booklet, on the cover.
 *
 * Shaped like the chapter pill under the title, because it does the same KIND of thing: it names a
 * list and opens it. Drawn like the sleep countdown rather than like that pill, because it sits on
 * artwork rather than on the ground — a translucent plate over a picture, where [Surface2] over a
 * photograph would read as a hole cut in it.
 *
 * On the cover, and not in the top bar where it started, for the reason the countdown is there: the
 * cover is the one region of this screen with room to spare, and a booklet is about the BOOK — the
 * thing the artwork is — rather than about this listening. Only drawn for a book that has one.
 *
 * One document opens straight away; several open the list, because choosing between a libretto and
 * a score is a question the pill cannot answer.
 */
@Composable
private fun DocumentsPill(
    documents: List<String>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (documents.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val interaction = rememberTapInteraction()
    // A 26dp pill inside a 48dp-tall target, and the press shown on the pill rather than on the
    // box around it — the split `TapFeedback.kt` states, which every small control in Homer makes.
    Box(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .tapTarget(interaction) {
                if (documents.size == 1) onOpen(documents.first()) else open = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Studio.copy(alpha = 0.82f))
                .pressFeedback(interaction)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = Parchment,
                modifier = Modifier.size(13.dp),
            )
            Text(
                stringResource(R.string.player_documents),
                color = Parchment,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val labels = documentLabels(documents)
            documents.forEachIndexed { index, path ->
                DropdownMenuItem(
                    text = { Text(labels[index]) },
                    onClick = {
                        open = false
                        onOpen(path)
                    },
                )
            }
        }
    }
}

/**
 * The countdown, on the artwork.
 *
 * A running sleep timer is the one piece of state that changes what is ABOUT to happen rather than
 * what is happening, and it had nowhere to be seen: the bottom row said "Sleep" whether one was
 * running or not once the labels came off the glyphs, and the amber tint says a timer exists
 * without saying how much of it is left.
 *
 * On the cover rather than beside it because the cover is the one region with space to spare, and
 * because a number floating over the artwork reads as temporary — which it is. Seconds here,
 * minutes in the notification: redrawing THIS costs nothing.
 *
 * A composable of its own so that stays true. The remaining time changes every second; read in
 * [PlayerArtwork]'s body it would recompose the cover with it, and read a level further up it would
 * recompose the entire player once a second for as long as a timer runs. Here, a pill redraws.
 */
@Composable
private fun SleepCountdown(remainingMs: () -> Long?, modifier: Modifier = Modifier) {
    remainingMs()?.let { remaining ->
        Row(
            modifier = modifier
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Studio.copy(alpha = 0.82f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Bedtime,
                contentDescription = stringResource(R.string.player_sleep),
                tint = Amber,
                modifier = Modifier.size(13.dp),
            )
            Text(
                formatTime(remaining),
                // TabularSmall is the app's own "numbers that must not jitter" style, which is
                // exactly what a countdown is: without it the pill changes width every time a
                // 1 ticks past.
                style = TabularSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = Amber,
            )
        }
    }
}
