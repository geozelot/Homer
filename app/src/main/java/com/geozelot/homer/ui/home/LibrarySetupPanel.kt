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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle

/**
 * What an empty shelf means, when it is empty for a reason other than being empty.
 *
 * It used to be the whole of first run: a sweep for candidate libraries, a list to choose from, and
 * a folder to type when the sweep found nothing. All of that is the setup flow's now — it happens
 * before the library is ever shown, where it can also ask about write access, the owner's rules and
 * where progress lives. What is left here are the two states that are still about the shelf: a crawl
 * that has been asked for and cannot start yet, and a library that really is empty.
 */
@Composable
fun LibrarySetupPanel(
    scanPending: Boolean,
    wifiOnly: Boolean,
    readsOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    // A crawl has been asked for and has not started — offline, or waiting for Wi-Fi. The shelf is
    // empty because nothing has read the library YET, which is the opposite of what "Homer found no
    // audiobooks" says, and it is what a first run on a bad connection looked like.
    if (scanPending) {
        Waiting(
            label = stringResource(if (wifiOnly) R.string.sync_waiting_wifi else R.string.sync_waiting_network),
            modifier = modifier,
        )
    } else {
        EmptyShelf(readsOnly, modifier)
    }
}

@Composable
private fun Waiting(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Amber)
        Spacer(Modifier.height(16.dp))
        Text(label, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyShelf(readsOnly: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (readsOnly) R.string.home_empty_reader_title else R.string.home_empty_title),
            style = SerifTitle,
            color = Parchment,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (readsOnly) R.string.home_empty_reader_hint else R.string.home_empty_hint),
            color = Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}
