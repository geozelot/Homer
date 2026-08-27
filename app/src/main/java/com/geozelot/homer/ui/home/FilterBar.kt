package com.geozelot.homer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface2

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
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tokens.forEach { token -> FilterPill(token) { onRemove(token) } }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.filter_count, shown, total),
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.filter_clear_all),
                color = Amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

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
        Text(token.value, color = Parchment, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                    suggestion.value,
                    color = Parchment,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(suggestion.count.toString(), color = Muted, fontSize = 11.sp)
            }
        }
    }
}
