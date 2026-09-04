package com.geozelot.homer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.LeadingIconInset
import com.geozelot.homer.ui.components.ScreenInset
import com.geozelot.homer.ui.theme.Ground
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle

/**
 * The frame every settings screen shares: back arrow + serif title over a scrollable body.
 *
 * A settings page is a destination, not a bottom sheet — the sheets this replaced measured their
 * content against a bounded height and silently crushed the last children to zero. The body here
 * always scrolls, and carries the system-bar + IME insets so a text field is never under the
 * keyboard and no control hides behind the navigation bar.
 */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * One standing fact about this page's whole subject, set under the title.
     *
     * For the thing that qualifies everything below it rather than belonging to any one group.
     * Upkeep's "last full crawl 2 days ago, from Pixel 7" is the case it was built for: it is what
     * authorises deletion, so it explains every count on the page — and it had been sitting at the
     * bottom, under the last action, where it read as a footnote to Sync rather than as a caption for
     * the library.
     *
     * Outside the scroll on purpose. A standing fact that scrolls away stops being one.
     */
    status: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = LeadingIconInset, end = ScreenInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = Muted,
                )
            }
            // The title and its status share a column so the status aligns under the title's own
            // left edge rather than under the back arrow — the alternative is hard-coding the icon
            // button's 48dp and having it drift the first time that changes.
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    title,
                    style = SerifTitle.copy(fontSize = 22.sp),
                    color = Parchment,
                )
                status?.let {
                    Text(
                        it,
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = ScreenInset)
                .padding(top = 8.dp, bottom = 32.dp),
            content = content,
        )
    }
}
