package com.geozelot.homer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.data.sync.facet.IndexActivity
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle

// ── Nothing to show ──────────────────────────────────────────────────────────
//
// The two screens that stand in for a library: one while it is still being read, one when a search
// matched nothing. Kept apart because they are different claims — "wait" and "there is none".

/**
 * Brief discovery phase shown while the library is being read from the database (or scanned), so
 * the empty-shelf screen never flashes on a launch that actually has books.
 */
@Composable
internal fun LibraryLoading(
    scanState: ScanState,
    indexActivity: IndexActivity,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Amber)
        Spacer(Modifier.height(16.dp))
        val label = when {
            scanState is ScanState.Scanning -> stringResource(
                R.string.home_scanning_progress,
                scanState.directoriesVisited,
                scanState.booksFound,
            )
            indexActivity == IndexActivity.READING -> stringResource(R.string.home_reading_index)
            else -> stringResource(R.string.home_opening_library)
        }
        Text(label, color = Muted, fontSize = 14.sp)
    }
}

@Composable
internal fun EmptyResults(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_no_matches), style = SerifTitle, color = Parchment)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_no_matches_hint), color = Muted, fontSize = 13.sp)
    }
}
