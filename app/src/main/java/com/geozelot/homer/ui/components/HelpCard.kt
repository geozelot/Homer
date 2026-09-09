package com.geozelot.homer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.LineShelf
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SectionLabel
import com.geozelot.homer.ui.theme.SerifTitle
import com.geozelot.homer.ui.theme.Surface2

/**
 * What this screen is, what its marks mean, and what can be done with it.
 *
 * ## Why a card and not a manual
 *
 * Homer says a great deal in glyphs — a bracket for a series, a bracket inside a bracket for a
 * collection, a number in a corner that means different things on different shelves. Every one of
 * those decisions is defensible and none of them is guessable, and a mark nobody can decode is
 * decoration. This is where they are decoded, one line each.
 *
 * ## Why it changes with the screen
 *
 * A help card that describes everything describes nothing: the reader is looking at ONE arrangement
 * and wants to know what THAT means. So the library's card answers for the view and the arrangement
 * currently in force — the corner numbers section is absent when nothing is numbered, the shelf
 * section is absent when nothing is shelved — and the player's answers for the player.
 *
 * It is deliberately short. Anybody reading this has a book they would rather be listening to.
 */

/** One titled group of lines. */
@Composable
private fun HelpSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = SectionLabel,
        color = Faint,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
    content()
}

/**
 * A line of explanation, with the mark it is explaining set beside it.
 *
 * [category] names the thing before describing it — "Author — who wrote it" — because the mark and
 * the sentence between them still leave the reader to infer the WORD, and the word is what they
 * will see everywhere else in the app: on a details chip, on a filter pill, in the arrange menu.
 * Naming it here is what ties the glyph to the rest of the vocabulary.
 */
@Composable
private fun HelpMark(icon: ImageVector?, text: String, badge: String? = null, category: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.width(34.dp).padding(top = 1.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when {
                icon != null -> Icon(icon, contentDescription = null, tint = Parchment, modifier = Modifier.size(15.dp))
                badge != null -> Text(
                    badge,
                    color = Parchment,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Surface2)
                        .border(1.dp, LineShelf, RoundedCornerShape(999.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            text = if (category == null) {
                AnnotatedString(text)
            } else {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Parchment, fontWeight = FontWeight.SemiBold)) {
                        append(category)
                    }
                    append(" — ")
                    append(text)
                }
            },
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A line with no mark of its own — something the screen DOES rather than something it shows. */
@Composable
private fun HelpLine(text: String) = HelpMark(icon = null, text = text, badge = "·")

/** The frame both cards share: sized to the screen, one way out, in the place every card puts it. */
@Composable
private fun HelpCard(title: String, onDismiss: () -> Unit, body: @Composable () -> Unit) {
    val width = (LocalConfiguration.current.screenWidthDp.dp * 0.94f).coerceAtMost(560.dp)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.62f
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(width),
        title = { Text(title, style = SerifTitle, color = Parchment, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top,
            ) { body() }
        },
        confirmButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Amber) }
        },
    )
}

/**
 * The library's card, answering for the arrangement actually on screen.
 *
 * @param gridView which view is showing — the two put the same facts in different places.
 * @param shelved whether the list is broken into shelves at all.
 * @param stacked whether series and collections are stacked into one item, or spread out flat.
 * @param numbered whether corner numbers are being drawn under the current arrangement.
 */
@Composable
fun LibraryHelpCard(
    gridView: Boolean,
    shelved: Boolean,
    stacked: Boolean,
    numbered: Boolean,
    onDismiss: () -> Unit,
) {
    HelpCard(stringResource(R.string.help_library_title), onDismiss) {
        Text(
            stringResource(
                if (gridView) R.string.help_library_lead_grid else R.string.help_library_lead_list,
            ),
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )

        HelpSection(stringResource(R.string.help_section_marks)) {
            HelpMark(
                HomerIcons.SeriesBracket,
                stringResource(R.string.help_mark_series),
                category = stringResource(R.string.filter_facet_series),
            )
            HelpMark(
                HomerIcons.CollectionBracket,
                stringResource(R.string.help_mark_collection),
                category = stringResource(R.string.filter_facet_collection),
            )
            HelpMark(
                HomerIcons.Author,
                stringResource(R.string.help_mark_author),
                category = stringResource(R.string.filter_facet_author),
            )
            HelpMark(
                HomerIcons.Genre,
                stringResource(R.string.help_mark_genre),
                category = stringResource(R.string.filter_facet_genre),
            )
        }

        HelpSection(stringResource(R.string.help_section_covers)) {
            HelpMark(HomerIcons.SeriesShelf, stringResource(R.string.help_cover_shelf))
            if (numbered) {
                HelpMark(icon = null, badge = "#3", text = stringResource(R.string.help_cover_index))
            } else {
                HelpMark(icon = null, badge = "#3", text = stringResource(R.string.help_cover_index_off))
            }
        }

        HelpSection(stringResource(R.string.help_section_doing)) {
            if (stacked) HelpLine(stringResource(R.string.help_do_open_shelf))
            HelpLine(stringResource(R.string.help_do_chip_filter))
            HelpLine(stringResource(R.string.help_do_long_press))
            HelpLine(stringResource(R.string.help_do_search))
            HelpLine(
                stringResource(
                    if (shelved) R.string.help_do_arrange_shelved else R.string.help_do_arrange,
                ),
            )
        }
    }
}

/** The player's card. */
@Composable
fun PlayerHelpCard(onDismiss: () -> Unit) {
    HelpCard(stringResource(R.string.help_player_title), onDismiss) {
        Text(
            stringResource(R.string.help_player_lead),
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )

        HelpSection(stringResource(R.string.help_section_marks)) {
            HelpMark(
                HomerIcons.Author,
                stringResource(R.string.help_mark_author),
                category = stringResource(R.string.filter_facet_author),
            )
            HelpMark(
                HomerIcons.SeriesBracket,
                stringResource(R.string.help_mark_series_player),
                category = stringResource(R.string.filter_facet_series),
            )
            HelpMark(
                HomerIcons.CollectionBracket,
                stringResource(R.string.help_mark_collection_player),
                category = stringResource(R.string.filter_facet_collection),
            )
        }

        HelpSection(stringResource(R.string.help_section_doing)) {
            HelpLine(stringResource(R.string.help_player_chapters))
            HelpLine(stringResource(R.string.help_player_settings))
            HelpLine(stringResource(R.string.help_player_sleep))
            HelpLine(stringResource(R.string.help_player_mark))
            HelpLine(stringResource(R.string.help_player_swipe))
        }
    }
}
