package com.geozelot.homer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.ControlPillHeight
import com.geozelot.homer.ui.components.DropdownChip
import com.geozelot.homer.ui.components.pressFeedback
import com.geozelot.homer.ui.components.rememberTapInteraction
import com.geozelot.homer.ui.components.rememberTextWidth
import com.geozelot.homer.ui.components.tapTarget
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberDeep
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SectionLabel
import com.geozelot.homer.ui.theme.Surface1
import com.geozelot.homer.ui.theme.Surface2

// ── The control bar ──────────────────────────────────────────────────────────
//
// The strip under the header: how the library is arranged, how it is searched, and which of the
// two views is showing. One file because those controls share a pill geometry and a tap height,
// and letting them drift apart is what would make them look assembled rather than designed.

/**
 * The library's header and its three controls — shelve, series, sort — plus the view toggle.
 *
 * The prose summary that used to sit beneath is gone: the header carries the count, and the chips
 * already say what they are set to, so it was restating both in a full sentence.
 */
@Composable
internal fun LibraryControlBar(
    count: Int,
    searching: Boolean,
    sort: LibrarySort,
    shelving: LibraryShelving,
    series: LibraryDepth,
    gridView: Boolean,
    /** Committed filters, drawn between the header and the chips. */
    tokens: List<FilterToken>,
    shown: Int,
    total: Int,
    query: String,
    searchOpen: Boolean,
    /** Whether the row is currently showing the three settings instead of the chips. */
    arrangeOpen: Boolean,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onToggleArrange: () -> Unit,
    onCloseSearch: () -> Unit,
    onRemoveToken: (FilterToken) -> Unit,
    onClearFilter: () -> Unit,
    /** Committed by the keyboard's own action key — see [InlineSearchField]. */
    onCommitQuery: () -> Unit,
    suggestions: List<FilterSuggestion>,
    onPickSuggestion: (FilterSuggestion) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onShelfChange: (LibraryShelving) -> Unit,
    onSeriesChange: (LibraryDepth) -> Unit,
    onToggleView: (Boolean) -> Unit,
    /**
     * Short viewport: the header is a line of this row rather than a line of its own. Help and
     * settings are NOT here — they are on the turned bar down the side, which exists whether or
     * not there is a library for this row to be about.
     */
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    // No inset of its own. The bar carries the grid's own horizontal padding from its call site, so
    // an extra 2dp here put the search chip 2dp right of the covers it filters and the header 4dp
    // right of both — the section label keeps its own 2dp, which every header on the screen shares.
    // Always "Library". It titles the same region whatever is filtered, and renaming it to
    // "Results" made the shelf look like a different place rather than the same one with less on
    // it. The COUNT carries that instead.
    val header = if (searching) {
        // Plural on the TOTAL: "41 of 313 books" is a statement about the shelf, and it is the
        // shelf's size that decides whether the noun is one book or many.
        pluralStringResource(R.plurals.home_section_library_filtered, total, shown, total)
    } else {
        pluralStringResource(R.plurals.home_section_library, count, count)
    }
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        // A line of its own only where there is height for one. Compact, it leads the control row
        // instead — and while the field is open it steps aside entirely rather than pushing the
        // library down by its own height to reappear, which is the move this bar was built to stop
        // making. Nothing is lost: a filter with pills carries its own count beside Clear.
        if (!compact) {
            SectionLabelRow(
                header,
                topPadding = 8.dp,
                bottomPadding = 4.dp,
                // Stays large while scrolling. Unlike the listening panel, this header is not inside
                // anything that collapses — it titles the list being scrolled, so it holds its size.
                large = true,
            )
        }
        // Open, the field REPLACES the chips in place — same position, no back arrow. It used to
        // be a full OutlinedTextField, half again as tall as the row it sat in, so opening search
        // shunted the whole library down the screen and closing it shunted it back. A control that
        // moves everything else to appear is a control you brace for.
        if (searchOpen) {
            SearchEnclosure(
                query = query,
                onQueryChange = onQueryChange,
                onClose = onCloseSearch,
                onCommit = onCommitQuery,
                suggestions = suggestions,
                onPickSuggestion = onPickSuggestion,
            )
        } else if (arrangeOpen) {
            ArrangeField(
                sort = sort,
                shelving = shelving,
                series = series,
                onSortChange = onSortChange,
                onShelfChange = onShelfChange,
                onSeriesChange = onSeriesChange,
                onCollapse = onToggleArrange,
            )
        } else if (compact) {
            CompactControlRow(
                header = header,
                filtered = tokens.isNotEmpty(),
                gridView = gridView,
                onOpenSearch = onOpenSearch,
                onToggleArrange = onToggleArrange,
                onToggleView = onToggleView,
            )
        } else Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drawn as a chip like its neighbours, but filled rather than outlined: it is the only
            // control here that changes what the list CONTAINS — the others only rearrange it — and
            // the fill is what says so at a glance. Amber once anything is filtered.
            SearchChip(active = tokens.isNotEmpty(), onClick = onOpenSearch)
            // One chip, until it is asked to be three — see [ArrangeField].
            Box(
                modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                ArrangeChip(open = false, onClick = onToggleArrange)
            }
            ViewToggleGroup(gridView = gridView, onToggleView = onToggleView)
        }
        // BELOW the controls, not above them. Above, they pushed the chips away from the header
        // every time one was added; below, the bar keeps its place and the pills grow into the gap
        // before the list.
        FilterPills(
            tokens = tokens,
            shown = shown,
            total = total,
            onRemove = onRemoveToken,
            onClear = onClearFilter,
        )
    }
}

/**
 * The library's controls on a screen with no height to spare: one 48dp row in place of an 82dp
 * band, with the header on it rather than above it.
 *
 * The label leads and takes the slack, giving it back first: on a window narrow as well as short it
 * ellipsises away to nothing while the chips, which are the only things here you cannot do without,
 * keep their full size.
 */
@Composable
private fun CompactControlRow(
    header: String,
    filtered: Boolean,
    gridView: Boolean,
    onOpenSearch: () -> Unit,
    onToggleArrange: () -> Unit,
    onToggleView: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Label, search and arrange travel together on the left: the two chips act on the list the
        // label is counting, so they belong beside the count rather than across the row from it.
        // The view toggle stays at the far end — it changes how the shelf is DRAWN rather than what
        // is on it, which is the one control here that is not about the count.
        //
        // Grouped inside a weighted Row so the slack falls between the two groups: the label takes
        // what it needs (`fill = false`) and gives it back first, ellipsising on the narrowest
        // window this layout runs on rather than pushing the chips off the end.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = header.uppercase(),
                style = SectionLabel,
                fontSize = SectionLabelLargeSize,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(start = 2.dp),
            )
            SearchChip(active = filtered, onClick = onOpenSearch)
            ArrangeChip(open = false, onClick = onToggleArrange)
        }
        ViewToggleGroup(gridView = gridView, onToggleView = onToggleView)
    }
}

/**
 * The one band a run of adjacent controls is drawn inside.
 *
 * PAINTED behind rather than applied with `Modifier.border`, because a real border wraps the layout
 * — and the layout has to stay 48dp tall to keep every segment tappable. Drawing it lets the band be
 * chip-height while the tap targets stay full size, which is the same split [DropdownChip] makes
 * with its own pill inside a 48dp box.
 *
 * Shared by the view toggle and by the shelve/depth/sort chips, so "these three belong together" is
 * said the same way in both places and cannot drift into two slightly different pills.
 */
private fun Modifier.controlGroupPill(): Modifier = drawBehind {
    val h = ControlPillHeight.toPx()
    val top = (size.height - h) / 2f
    val stroke = 1.dp.toPx()
    val radius = CornerRadius(8.dp.toPx())
    drawRoundRect(Surface1, Offset(0f, top), Size(size.width, h), radius)
    drawRoundRect(
        color = Line,
        topLeft = Offset(stroke / 2f, top + stroke / 2f),
        size = Size(size.width - stroke, h - stroke),
        cornerRadius = radius,
        style = Stroke(stroke),
    )
}

/** The one control that opens [ArrangeBand]. Shows no value: the band is where values are read. */
@Composable
private fun ArrangeChip(open: Boolean, onClick: () -> Unit) {
    val interaction = rememberTapInteraction()
    Box(
        modifier = Modifier
            .sizeIn(minHeight = ControlTapHeight, minWidth = 44.dp)
            .tapTarget(interaction, onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .height(ControlPillHeight)
                .clip(RoundedCornerShape(8.dp))
                // Lit while its band is open, the same way the search chip is lit while filtering:
                // the control that produced the thing below stays visibly responsible for it.
                .background(if (open) AmberSoft else Surface1)
                .border(1.dp, if (open) AmberDeep else Line, RoundedCornerShape(8.dp))
                .pressFeedback(interaction)
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                tint = if (open) Amber else Muted,
                modifier = Modifier.size(14.dp),
            )
            Text(
                stringResource(R.string.home_chip_arrange),
                color = if (open) Amber else Muted,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * The three settings, inside a field of their own.
 *
 * ## What it is
 *
 * The Arrange chip, expanded: same height, same corner, now stretched across the whole control row
 * with the settings laid out inside it — exactly as the search field does one state over, which is
 * also why it is drawn like one.
 *
 * It was tried in the segment between the search glyph and the view toggle, keeping both neighbours
 * in place. That segment is about 190dp on a small phone, and three values fit it only by
 * ellipsising the ones worth reading — a setting you cannot read is a setting you have to open to
 * check, which is the opposite of what putting them on the bar was for. Across the row each gets a
 * comfortable 110dp, and the neighbours come back the moment it closes.
 *
 * ## Why the settings inside it wear no outline
 *
 * A box inside a box reads as two controls, and three of them reads as four. Without their own
 * pills the field is the control and these are its parts. It is also what buys the width: that
 * segment is about 190dp on a small phone, and three outlined chips carrying real values —
 * "Gestapelt" is not a short word — do not fit it.
 *
 * ## An even third each
 *
 * Each setting takes `weight(1f)`, and inside its share the chip spans the full width: the category
 * begins at the share's left edge and the chevron ends at its right. So the three chevrons land on
 * the same three tick marks whatever the values happen to say, which is what makes the row read as
 * evenly divided rather than as three chips of assorted lengths.
 *
 * The value is the half that gives way. Three fully-drawn categories read as three settings, where
 * three shortened ones read as three different things — and a clipped value is still recognisable,
 * with the chip a tap away from showing it whole.
 *
 * ## Why fixed shares are also what made the values appear at all
 *
 * The run scrolled first, and inside a `horizontalScroll` a Row is measured against an infinite
 * width — under which `DropdownChip`'s label, which is `weight(1f, fill = false)` so it can give
 * way on a crowded bar, resolves to no width at all. The values were not merely cramped, they were
 * absent. Three fixed thirds constrain the labels properly, so each one is shown and ellipsised
 * within its own share instead of vanishing into an unbounded row.
 *
 * ## Why the three cannot simply be one menu
 *
 * Shelve, depth and sort are orthogonal — sections, depth, order — so merging them multiplies:
 * four shelvings times three depths is twelve entries to pick one thing from. They were a dialog
 * once, which fixed the crowding by putting a modal in front of the library to change how the
 * library looks; you could not see what you were arranging while you arranged it.
 *
 * Each chip names its axis in words rather than with a glyph. A glyph was what fitted while the
 * field sat between its neighbours, and it asks the reader to know what a stack of layers means;
 * across the row there is room to say "Shelve".
 */
@Composable
private fun ArrangeField(
    sort: LibrarySort,
    shelving: LibraryShelving,
    series: LibraryDepth,
    onSortChange: (LibrarySort) -> Unit,
    onShelfChange: (LibraryShelving) -> Unit,
    onSeriesChange: (LibraryDepth) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolved through the context because `labelOf` is a plain lambda, not a composable.
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Same inset as the search field, and for the same reason — see ControlRowInset. With
            // it the field measures 48dp overall, exactly the row it replaced.
            .padding(vertical = ControlRowInset)
            .height(ControlPillHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
            // Amber, like the search field it is a sibling of: both are a chip that became a field,
            // and the accent is what this app uses to mean "this is live".
            .border(1.dp, AmberDeep, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Inside the outline, in the search field's leading slot and at its width — so the control
        // that leaves a mode is in the same place, and looks the same, in both of them.
        Box(
            modifier = Modifier
                .size(width = LeadingActionWidth, height = ControlPillHeight)
                .clickable(onClick = onCollapse),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = Muted,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownChip(
            label = stringResource(shelving.label),
            options = LibraryShelving.entries.toList(),
            selected = shelving,
            labelOf = { context.getString(it.label) },
            onSelect = onShelfChange,
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Category,
            iconDescription = stringResource(R.string.arrange_shelve),
            menuHeader = stringResource(R.string.arrange_shelve_by),
            bordered = false,
        )
        DropdownChip(
            label = stringResource(series.label),
            options = LibraryDepth.entries.toList(),
            selected = series,
            labelOf = { context.getString(it.label) },
            onSelect = onSeriesChange,
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Layers,
            iconDescription = stringResource(R.string.arrange_group),
            menuHeader = stringResource(R.string.arrange_group_by),
            bordered = false,
        )
        // Only the sorts that still do something — see LibrarySort.offeredFor.
        DropdownChip(
            label = stringResource(sort.label),
            options = LibrarySort.offeredFor(shelving),
            selected = sort,
            labelOf = { context.getString(it.label) },
            onSelect = onSortChange,
            modifier = Modifier.weight(1f),
            icon = Icons.AutoMirrored.Filled.Sort,
            iconDescription = stringResource(R.string.arrange_sort),
            menuHeader = stringResource(R.string.arrange_sort_by),
            bordered = false,
        )
    }
}

/**
 * The search control, drawn as a filled chip.
 *
 * Filled where the chips beside it are outlined, because it does a different KIND of thing: the
 * others choose how the list is arranged, this one chooses what is in it. Same pill height as every
 * other control on the row, so the bar is one consistent band rather than a line of mismatched
 * boxes.
 */
@Composable
private fun SearchChip(active: Boolean, onClick: () -> Unit) {
    val interaction = rememberTapInteraction()
    Box(
        modifier = Modifier
            // Chip-height pill inside a full-height tap target, the same split DropdownChip makes
            // — so the press is shown on the pill, not on the target. See TapFeedback.kt.
            .sizeIn(minHeight = ControlTapHeight, minWidth = 44.dp)
            .tapTarget(interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(ControlPillHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(if (active) AmberSoft else Surface2)
                // Amber whether or not anything is filtered. The hairline every other control
                // wears said "one more of these" about the one control on the row that changes
                // what the list CONTAINS; the accent is what the rest of the app uses to mean
                // live, and this is the chip worth finding without looking for it.
                .border(1.dp, Amber, RoundedCornerShape(8.dp))
                .pressFeedback(interaction)
                // Wider than a glyph needs. It is the control that opens the box, so it gets a
                // little more presence than the chips that merely rearrange the list.
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = stringResource(R.string.home_cd_search),
                // Muted, the same as the glyphs in the chips beside it. Parchment is the primary
                // TEXT tone and read as plain white next to them, which made the icon the loudest
                // thing on the row while the border around it was the quietest.
                tint = if (active) Amber else Muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The search field, sized and shaped as one of the chips it replaces.
 *
 * A [BasicTextField] rather than an OutlinedTextField: the Material field carries its own label
 * slot, its own 56dp minimum and its own padding, none of which fit inside a 28dp pill. What is
 * wanted here is a chip somebody can type into.
 *
 * A back arrow leads, where the magnifier used to. The magnifier was decoration: the field is
 * plainly a field, it was opened from a magnifier one tap ago, and it spent the one slot at the head
 * of the pill saying what the reader had just done rather than offering them a way out. Closing had
 * been left to the X, a tap on the library, or the back gesture — none of them visible.
 *
 * Which frees the X to mean the one thing an X in a text field means everywhere else: clear the
 * text. It no longer has to double as the close button, so it is drawn only when there is something
 * to clear, and its label is simply true.
 */
/**
 * The search field and what it is offering, inside one outline.
 *
 * ## Why they share a box
 *
 * Suggestions and committed filters were the same shape in the same place, distinguished only by
 * colour — so which band did what had to be learned, and could not be seen. Enclosing the offers
 * makes the distinction structural: the box IS the search, everything inside it is something search
 * is proposing, and the chips left outside it are the only ones actually doing anything to the
 * library. That reads before the convention is known, and it survives a dim screen.
 *
 * It matters most with nothing typed, which is the state a reader meets first: a bare field over a
 * loose band of chips gives no clue what the chips are, where a field that visibly contains them
 * does.
 *
 * The field inside draws no border of its own — the enclosure carries it — or the box would have a
 * box in it.
 */
@Composable
private fun SearchEnclosure(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onCommit: () -> Unit,
    suggestions: List<FilterSuggestion>,
    onPickSuggestion: (FilterSuggestion) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Before the shape, so the BORDER moves down rather than the content inside it.
            .padding(vertical = ControlRowInset)
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
            .border(1.dp, AmberDeep, RoundedCornerShape(8.dp)),
    ) {
        InlineSearchField(
            query = query,
            onQueryChange = onQueryChange,
            onClose = onClose,
            onCommit = onCommit,
            enclosed = true,
        )
        // Only when there is something to offer: an empty band under a rule is a box that looks
        // broken rather than one that has nothing to say.
        if (suggestions.isNotEmpty()) {
            HorizontalDivider(color = Line)
            FilterSuggestions(
                suggestions = suggestions,
                onPick = onPickSuggestion,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun InlineSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    /** Drawn inside [SearchEnclosure], which carries the outline — so this one draws none. */
    enclosed: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        modifier = modifier.sizeIn(minHeight = if (enclosed) ControlPillHeight else ControlTapHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ControlPillHeight)
                .then(
                    if (enclosed) {
                        Modifier
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface2)
                            .border(1.dp, AmberDeep, RoundedCornerShape(8.dp))
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Holds the arrow's width open, the same way the trailing spacer does for the X.
            Spacer(modifier = Modifier.width(LeadingActionWidth))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.home_search_placeholder),
                        color = Faint,
                        fontSize = 12.sp,
                        // The field below builds its style from scratch and so gets the font's own
                        // line box; this one inherits the theme's 24sp one. Unset, the placeholder
                        // and the text replacing it sat on two different lines.
                        lineHeight = 14.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Parchment, fontSize = 12.sp, lineHeight = 14.sp),
                    cursorBrush = SolidColor(Amber),
                    // The key that was doing nothing. On a single-line field the IME shows an action
                    // in place of a newline, and it went unhandled — so pressing it dismissed the
                    // keyboard and threw the query's momentum away. It keeps the words instead.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onCommit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            // Holds the X's width open so no typed text can end up underneath its tap target —
            // otherwise tapping the last word of a query would clear it instead of placing a cursor.
            Spacer(modifier = Modifier.width(TrailingActionWidth))
        }
        // Both targets sit OUTSIDE the 28dp pill so they can be real ones. The glyphs belong inside
        // it, but a 16dp clickable is not a control anybody can hit; the row is 48dp and only the
        // pill is short, so each target takes the row's full height with its glyph centred in it.
        // Exactly the split DropdownChip makes: chip-height paint, full-height touch.
        //
        // ENCLOSED, they are the pill's own height instead — because these targets are children of
        // the Box, so a 48dp one stretches it to 48 and centres the input line in the middle of
        // that. Which is how the search field's text came to sit 10dp below the arrange field's,
        // and below the icon it replaced. Inside the enclosure the slop is the enclosure's inset.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = LeadingActionWidth, height = if (enclosed) ControlPillHeight else ControlTapHeight)
                // Clipped before the tap, so the press is a rounded patch inside a rounded field
                // rather than a hard-cornered rectangle in the corner of one.
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.home_cd_close_search),
                tint = Muted,
                modifier = Modifier.size(16.dp),
            )
        }
        // Only when there is something to clear. An X on an empty field is a control that either
        // does nothing or does something else — and "something else" is what it used to do.
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(width = TrailingActionWidth, height = if (enclosed) ControlPillHeight else ControlTapHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * The arrow's slot at the head of the search pill, and the X's at its tail.
 *
 * Both 44dp, the width the view toggle's own segments settled on. The trailing one is reserved even
 * while the X is hidden, so the text does not reflow the moment a first character is typed.
 */
private val LeadingActionWidth = 44.dp
private val TrailingActionWidth = 44.dp

/**
 * How an opened collection reads: as its threads, or as one numbered run.
 *
 * Deliberately the DEPTH control's own glyph and its own two words. This is not a new idea to learn
 * — "series or flat" is exactly what the depth chip in the control bar asks of the whole library,
 * and this asks it of one collection. Same question, smaller scope, so it should not look like a
 * different question.
 *
 * A toggle rather than a menu: there are two answers, and a dropdown to choose between two is a menu
 * where a switch would do.
 *
 * Drawn only where it would change something. A collection whose books are in no sub-series has one
 * reading, and offering a choice between it and itself is worse than offering nothing.
 */
@Composable
internal fun CollectionOrderChip(flat: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    // The collection's OWN name for itself, not "flat". Read as one run this shelf is being treated
    // as the collection it is, and "flat" describes what happened to the threads rather than what
    // the reader gets — which is the whole collection, in its own order.
    val asCollection = stringResource(R.string.depth_collection)
    val asSeries = stringResource(R.string.depth_series)
    // One width for both labels, so the chip does not resize under the finger that just tapped it —
    // and so the chevron beside it does not shift every time this is used.
    val labelWidth = rememberTextWidth(
        listOf(asCollection, asSeries),
        TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),
    )
    val interaction = rememberTapInteraction()
    Box(
        modifier = modifier
            .sizeIn(minHeight = ControlTapHeight, minWidth = 44.dp)
            // The target takes the tap and shows nothing; the pill shows the press. Without the
            // split the ripple filled the whole 48dp target around a 26dp pill, which reads as
            // having missed the control and hit the header behind it. See TapFeedback.kt.
            .tapTarget(interaction) { onChange(!flat) },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(ControlPillHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface1)
                .border(1.dp, Line, RoundedCornerShape(8.dp))
                .pressFeedback(interaction)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                Icons.Filled.Layers,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(14.dp),
            )
            Text(
                if (flat) asCollection else asSeries,
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                modifier = Modifier.width(labelWidth),
            )
        }
    }
}

/** Whether threading this shelf would actually separate anything. */
internal fun LibraryEntry.Series.hasThreads(): Boolean =
    isCollection && books.any { it.series != null } && books.mapNotNull { it.series }.distinct().size +
        (if (books.any { it.series == null }) 1 else 0) > 1

/**
 * Grid / list, drawn to the same height as the chips next to it.
 *
 * The outline is painted behind rather than applied with `Modifier.border`, because a real border
 * wraps the layout — and the layout has to stay 48dp tall to keep both halves tappable. Drawing it
 * lets the pill be chip-height while the tap targets stay full size, which is the same split
 * DropdownChip makes with its own 26dp pill inside a 48dp box.
 */
@Composable
private fun ViewToggleGroup(gridView: Boolean, onToggleView: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.controlGroupPill(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewToggleButton(Icons.Filled.GridView, selected = gridView, desc = stringResource(R.string.home_cd_grid_view)) {
            onToggleView(true)
        }
        ViewToggleButton(Icons.AutoMirrored.Filled.ViewList, selected = !gridView, desc = stringResource(R.string.home_cd_list_view)) {
            onToggleView(false)
        }
    }
}

@Composable
private fun ViewToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    desc: String,
    onClick: () -> Unit,
) {
    val interaction = rememberTapInteraction()
    Box(
        modifier = Modifier
            // The segment was 32×28dp, well under the 48dp minimum touch target. The icon keeps its
            // size; only the tappable segment grows — and the press is shown on the pill below,
            // not on this, or it flashes a rectangle a head taller than the chips beside it.
            .size(width = ViewToggleSegment, height = ControlTapHeight)
            .tapTarget(interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = ViewToggleSegment - 2.dp, height = ControlPillHeight - 2.dp)
                .clip(RoundedCornerShape(7.dp))
                .then(if (selected) Modifier.background(AmberSoft) else Modifier)
                .pressFeedback(interaction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                tint = if (selected) Amber else Faint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** One half of the view toggle. Named because the pill inside it is drawn 2dp narrower. */
private val ViewToggleSegment = 44.dp

/** Books across the visible entries — the count while a search is narrowing the library. */
internal fun List<LibraryEntry>.bookCount(): Int = sumOf { entry ->
    when (entry) {
        is LibraryEntry.Header -> 0
        is LibraryEntry.Standalone -> 1
        is LibraryEntry.Series -> entry.books.size
    }
}
