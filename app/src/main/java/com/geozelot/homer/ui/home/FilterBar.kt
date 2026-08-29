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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * What the box offers for what has been typed: values to turn into filters.
 *
 * A ROW OF CHIPS, not a dropdown. The dropdown was up to 232dp of menu dropped directly on top of
 * the results it exists to preview — and with the keyboard up there was next to nothing left of the
 * library underneath it, so the one thing that tells you whether a suggestion is the one you want
 * was the thing it covered. A single scrolling line costs about a sixth of that and never hides a
 * book.
 *
 * The full list is still there, one tap away behind a "+N" chip, because the chips give up two
 * things the list had: you can only see a few at once, and a long value is cut short. Both matter
 * exactly when a query is ambiguous, which is when somebody actually reads this.
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
    // Keyed on the suggestions themselves, so typing another character collapses it again: the
    // expanded list answers a question about THIS query, and a new query has not asked it yet.
    var expanded by remember(suggestions) { mutableStateOf(false) }

    if (expanded) {
        SuggestionList(suggestions, modifier, onPick)
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestions.take(SuggestionChips).forEach { suggestion ->
            SuggestionChip(suggestion) { onPick(suggestion) }
        }
        if (suggestions.size > SuggestionChips) {
            MoreSuggestionsChip(suggestions.size - SuggestionChips) { expanded = true }
        }
    }
}

/**
 * How many suggestions the row shows before the rest go behind "+N".
 *
 * Not a width calculation — the row scrolls, so more would fit. It is a reading limit: past about
 * four, scanning a horizontal line is slower than reading a vertical list, which is the point at
 * which the full list is the better answer and should be offered rather than approximated.
 */
private const val SuggestionChips = 4

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
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onPick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(stringResource(suggestion.facet.label), color = Faint, fontSize = 9.5.sp)
        Text(
            displayValue(suggestion.facet, suggestion.value),
            color = Parchment,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Capped so one long series name cannot take the whole row and hide the three offers
            // behind it. A value cut short here is exactly what "+N" is for.
            modifier = Modifier.widthIn(max = 168.dp),
        )
        Text(suggestion.count.toString(), color = Muted, fontSize = 9.5.sp)
    }
}

/** The way back to the full list, and the only hint that there IS more than the row shows. */
@Composable
private fun MoreSuggestionsChip(count: Int, onClick: () -> Unit) {
    val label = pluralStringResource(R.plurals.filter_suggestions_more, count, count)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "+$count",
            color = Amber,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            // On the TEXT, not on the clickable row above it. A description on the row is merged
            // WITH this text rather than replacing it, so the chip announced itself twice — "3 more
            // suggestions, plus 3". Set here it overrides what this node would otherwise read, and
            // the row keeps its click action.
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/**
 * Every suggestion, as the vertical list this used to be.
 *
 * Still capped and still scrolling inside the cap: a common substring must not be able to push the
 * library off the screen. It is only ever reached deliberately now, which is what makes the cost of
 * covering the list acceptable — somebody who taps "+N" has said the offers matter more than the
 * results for a moment.
 */
@Composable
private fun SuggestionList(
    suggestions: List<FilterSuggestion>,
    modifier: Modifier,
    onPick: (FilterSuggestion) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            .heightIn(max = 232.dp)
            .verticalScroll(rememberScrollState()),
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
