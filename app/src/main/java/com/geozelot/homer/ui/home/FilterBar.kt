package com.geozelot.homer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
            HomerTextButton(onClick = onClear, contentPadding = SettingsActionPadding) {
                Text(
                    stringResource(R.string.filter_clear_all),
                    color = Amber,
                    fontSize = 11.sp,
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
private val PillRowsMax = 84.dp

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
            .clip(RoundedCornerShape(8.dp))
            .background(AmberSoft)
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onRemove)
            .padding(start = 8.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(token.facet.label), color = Faint, fontSize = 9.5.sp)
        Text(
            displayValue(token.facet, token.value),
            color = Parchment,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
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
 * What the box offers for what has been typed: values to turn into filters, above the plain search.
 *
 * Shown only while something is typed, and dismissed by committing one or by clearing the field —
 * a suggestion list that outlives its query is a menu covering the list you were trying to read.
 */
@Composable
fun FilterSuggestions(
    suggestions: List<FilterSuggestion>,
    modifier: Modifier = Modifier,
    onPick: (FilterSuggestion) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            // Capped so a common substring cannot push the library off the screen; the list scrolls
            // inside the cap rather than growing past it.
            .heightIn(max = 232.dp),
    ) {
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(suggestion.facet.label),
                    color = Faint,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    displayValue(suggestion.facet, suggestion.value),
                    color = Parchment,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(suggestion.count.toString(), color = Muted, fontSize = 11.sp)
            }
        }
    }
}
