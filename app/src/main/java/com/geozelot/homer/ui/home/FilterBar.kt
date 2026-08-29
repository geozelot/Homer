package com.geozelot.homer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface2

/**
 * What a token's value should READ as.
 *
 * A state token stores a stable key — `downloaded`, `no-cover` — because that is what matching and
 * the saved form need. Showing that key would put an untranslated slug in the middle of a German
 * interface, so the pill and the suggestion row resolve it to the state's own label. Everything else
 * is a value out of the library and already reads the way the library does.
 */
@Composable
internal fun displayValue(facet: FilterFacet, value: String): String =
    if (facet == FilterFacet.STATE) {
        BookState.from(value)?.let { stringResource(it.label) } ?: value
    } else {
        value
    }

/**
 * The committed filters, and what they have left.
 *
 * Absent entirely when nothing is filtered — an empty row of chrome above every unfiltered library
 * would cost the list a line of height permanently to say nothing.
 *
 * The count is not decoration. A filter you have forgotten is a library that looks broken, and this
 * is the line that stops "where did my books go" being a bug report: it says how many of how many,
 * and Clear is next to it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPills(
    tokens: List<FilterToken>,
    shown: Int,
    total: Int,
    onRemove: (FilterToken) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tokens.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Wraps and scrolls VERTICALLY. Horizontally, a filter you had set scrolled off the side
        // and left the shelf short for no visible reason; wrapping keeps every pill on screen, and
        // the cap stops eight of them pushing the library off the bottom.
        FlowRow(
            modifier = Modifier
                .weight(1f)
                .heightIn(max = PillRowsMax)
                .verticalScroll(rememberScrollState())
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tokens.forEach { token -> FilterPill(token) { onRemove(token) } }
        }
        // Outside the scrolling area on purpose, so it is the edge the pills scroll against and
        // never scrolls away itself. A reader who has filtered themselves into an empty shelf must
        // always be able to see the way out of it.
        Column(horizontalAlignment = Alignment.End) {
            // The same height as the chips it sits beside. Left to size itself it came out taller
            // than every pill on the row, because a TextButton carries Material's own 40dp minimum
            // — so the one control that is not a chip was the tallest thing on the line.
            HomerTextButton(
                onClick = onClear,
                contentPadding = FilterChipPadding,
                modifier = Modifier.height(FilterChipHeight),
            ) {
                Text(
                    stringResource(R.string.filter_clear_all),
                    color = Amber,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                stringResource(R.string.filter_count, shown, total),
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

/** Three rows of pills before it scrolls — past that the library is the thing being crowded out. */
private val PillRowsMax = 96.dp

/**
 * The one height every chip on the search lines is drawn to — offered, committed, and Clear.
 *
 * Pinned rather than left to content, for the reason that keeps catching this codebase out:
 * Material's `bodyLarge` carries `lineHeight = 24.sp`, and a `Text` that overrides `fontSize` alone
 * is laid out in a 24dp line box whatever size its letters are. Three chips with slightly different
 * padding therefore came out three slightly different heights, all of them taller than they looked
 * like they should be. Every label on these rows sets its own line height to match.
 */
internal val FilterChipHeight = 28.dp

/** A chip's padding, on the button that has to pretend to be one. */
private val FilterChipPadding = PaddingValues(horizontal = 10.dp)

/**
 * One committed filter: the axis in small type, the value in full, and an X.
 *
 * The axis is named because the same word can sit on two of them — a genre and a tag both called
 * "Classic" filter to different shelves, and a pill reading only "Classic" could not say which.
 */
@Composable
private fun FilterPill(token: FilterToken, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .height(FilterChipHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(AmberSoft)
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onRemove)
            .padding(start = 8.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(token.facet.label), color = Faint, fontSize = 9.5.sp, lineHeight = 11.sp)
        Text(
            displayValue(token.facet, token.value),
            color = Parchment,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp),
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(R.string.action_clear),
            tint = Muted,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * What the box offers for what has been typed: values to turn into filters.
 *
 * ONE SCROLLING LINE, all of them. It was a dropdown of up to 232dp dropped on top of the results it
 * exists to preview — and with the keyboard up almost nothing of the library was left underneath, so
 * the one thing that tells you whether a suggestion is the one you want was exactly what it covered.
 * A line costs a chip's height and never hides a book.
 *
 * Everything is reachable by scrolling rather than by expanding. A "+N" chip that opened the old
 * list was the first attempt: it kept the full list one tap away, at the price of a second mode to
 * be in and a second way out of it, for a row that can simply be pushed sideways.
 *
 * Shown only while the box is open, above the committed pills — see the control bar.
 */
@Composable
fun FilterSuggestions(
    suggestions: List<FilterSuggestion>,
    modifier: Modifier = Modifier,
    onPick: (FilterSuggestion) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestions.forEach { suggestion ->
            SuggestionChip(suggestion) { onPick(suggestion) }
        }
    }
}

/**
 * One offer: which axis, the value, and how many books it would leave.
 *
 * The axis is named for the same reason the committed pill names it — a genre and a tag both called
 * "Classic" filter to different shelves — and the count is what separates a suggestion worth taking
 * from one that would empty the shelf.
 */
@Composable
private fun SuggestionChip(suggestion: FilterSuggestion, onPick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(FilterChipHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onPick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            stringResource(suggestion.facet.label),
            color = Faint,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
        )
        Text(
            displayValue(suggestion.facet, suggestion.value),
            color = Parchment,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Capped so one long series name cannot fill the row and hide every offer behind it.
            // The row scrolls, so the rest are still reachable — they are just further away.
            modifier = Modifier.widthIn(max = 168.dp),
        )
        Text(suggestion.count.toString(), color = Muted, fontSize = 9.5.sp, lineHeight = 11.sp)
    }
}
