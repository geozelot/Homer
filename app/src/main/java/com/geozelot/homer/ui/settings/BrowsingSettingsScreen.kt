package com.geozelot.homer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsRow
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.components.SettingsSwitchRow
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface2
import kotlin.math.roundToInt

/**
 * How THIS phone shows the library: how large Homer draws, the jump-to-letter lane, and how authors
 * are filed and written. Never anything the library itself carries — that is the line every page
 * under "On this device" sits on.
 */
@Composable
fun BrowsingSettingsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fastScroll by viewModel.fastScroll.collectAsStateWithLifecycle()
    val authorFiling by viewModel.authorFiling.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.set_browsing_title), onBack, modifier) {
        SettingsSectionHeader(stringResource(R.string.set_browsing_display_header))
        DisplaySizeRow()

        SettingsDivider()
        SettingsSectionHeader(stringResource(R.string.set_browsing_scroll_header))
        SettingsSwitchRow(
            label = stringResource(R.string.settings_fast_scroll),
            checked = fastScroll,
            onCheckedChange = viewModel::setFastScroll,
            description = stringResource(R.string.settings_fast_scroll_desc),
        )

        SettingsDivider()
        SettingsSectionHeader(stringResource(R.string.set_browsing_authors_header))
        SettingsSwitchRow(
            label = stringResource(R.string.settings_author_by_surname),
            checked = authorFiling.bySurname,
            onCheckedChange = viewModel::setAuthorBySurname,
            description = stringResource(R.string.settings_author_by_surname_desc),
        )
        // Only while the list is actually in that order. Writing names back to front in a list
        // sorted by given name would be showing the index of a different arrangement — so the
        // switch is not merely ignored when it does not apply, it is not offered.
        if (authorFiling.bySurname) {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_author_show_filed),
                checked = authorFiling.showFiled,
                onCheckedChange = viewModel::setAuthorShowFiled,
                description = stringResource(R.string.settings_author_show_filed_desc),
            )
        }
    }
}

/**
 * Homer's own display size, as a slider over [DisplayScale]'s steps.
 *
 * Applied when the thumb is LET GO, not while it moves. Applying redraws the whole activity at the
 * new size — this slider included — so a live preview would pull the track out from under the
 * finger dragging it.
 */
@Composable
private fun DisplaySizeRow() {
    val context = LocalContext.current
    // Read once: a change recreates the activity, and this page with it.
    val applied = remember { DisplayScale.current(context) }
    var draft by remember { mutableIntStateOf(applied) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsRow(label = stringResource(R.string.settings_display_size)) {
            // Only once there is something to go back from. 100% is a tick on the slider too, but
            // one among eleven, and the way back should not need finding.
            if (applied != DisplayScale.DEFAULT) {
                HomerTextButton(
                    onClick = { DisplayScale.apply(context, DisplayScale.DEFAULT) },
                    contentPadding = SettingsActionPadding,
                ) {
                    Text(stringResource(R.string.action_reset), color = Amber, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                stringResource(R.string.settings_display_size_value, draft),
                color = Parchment,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = draft.toFloat(),
            onValueChange = { draft = DisplayScale.snap(it.roundToInt()) },
            onValueChangeFinished = { DisplayScale.apply(context, draft) },
            valueRange = DisplayScale.MIN.toFloat()..DisplayScale.MAX.toFloat(),
            // Positions BETWEEN the two ends, which is what Material counts.
            steps = (DisplayScale.MAX - DisplayScale.MIN) / DisplayScale.STEP - 1,
            colors = SliderDefaults.colors(
                thumbColor = Amber,
                activeTrackColor = Amber,
                inactiveTrackColor = Surface2,
                activeTickColor = OnAmber,
                inactiveTickColor = Faint,
            ),
        )
        SettingsExplanation(stringResource(R.string.settings_display_size_desc))
    }
}
