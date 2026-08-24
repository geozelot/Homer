package com.geozelot.homer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.data.library.DiscoveredLibrary
import com.geozelot.homer.ui.components.DiscoveredLibraryCard
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle

/**
 * What a brand-new install sees instead of an empty shelf: the answer, if there is one, or the one
 * question that has to be asked.
 *
 * The old first run put an empty library in front of the user with a button to settings, and every
 * device therefore crawled the whole tree from scratch — including the second one, standing next to
 * a finished index on the same server. This is the same sweep the settings page has always run,
 * moved to the moment it decides something.
 */
@Composable
fun LibrarySetupPanel(
    setup: LibrarySetup,
    onAdopt: (String) -> Unit,
    onNameFolder: () -> Unit,
    isShare: Boolean,
    scanPending: Boolean,
    wifiOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    // A crawl has been asked for and has not started — offline, or waiting for Wi-Fi. The shelf is
    // empty because nothing has read the library YET, which is the opposite of what "Homer found no
    // audiobooks" says, and it is what a first run on a bad connection looked like.
    if (scanPending && setup == LibrarySetup.Ready) {
        Waiting(
            label = stringResource(if (wifiOnly) R.string.sync_waiting_wifi else R.string.sync_waiting_network),
            modifier = modifier,
        )
        return
    }
    when (setup) {
        // Adopting takes as long as reading the index; both are "hold on" states with nothing to
        // decide, and neither should flash a question the app is about to answer itself.
        LibrarySetup.Unknown, LibrarySetup.Looking, LibrarySetup.Adopting ->
            Waiting(
                label = stringResource(
                    if (setup == LibrarySetup.Adopting) {
                        R.string.setup_adopting
                    } else {
                        R.string.setup_looking
                    },
                ),
                modifier = modifier,
            )

        is LibrarySetup.Choose -> Choose(setup.candidates, onAdopt, onNameFolder, isShare, modifier)
        LibrarySetup.NothingFound -> NameTheFolder(onAdopt, isShare, modifier)
        // A library that has been scanned and really is empty is a fact, not a question.
        LibrarySetup.Ready -> EmptyShelf(modifier)
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

/**
 * More than one plausible library. Each card carries what the folder already holds — an index, a
 * book count, whose folder it is — because that is the whole basis for choosing between them.
 */
@Composable
private fun Choose(
    candidates: List<DiscoveredLibrary>,
    onAdopt: (String) -> Unit,
    onNameFolder: () -> Unit,
    isShare: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.setup_choose_title), style = SerifTitle, color = Parchment)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_choose_body), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(8.dp))
        candidates.forEach { library ->
            DiscoveredLibraryCard(
                library = library,
                onUse = onAdopt,
                actionLabel = stringResource(R.string.setup_use_this),
            )
        }
        // The way out. Every library is invisible to the sweep until Homer has read it once, so a
        // list with no way past it would strand exactly the person setting up their first library.
        TextButton(onClick = onNameFolder, contentPadding = SettingsActionPadding) {
            Text(stringResource(R.string.setup_none_of_these), color = Amber, fontSize = 13.sp)
        }
        SharingNote(isShare, Modifier.padding(top = 12.dp))
    }
}

/** Nothing on the server carries an index, so the one thing Homer cannot guess is the folder. */
@Composable
private fun NameTheFolder(onAdopt: (String) -> Unit, isShare: Boolean, modifier: Modifier = Modifier) {
    var folder by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.setup_folder_title), style = SerifTitle, color = Parchment)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_folder_body), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = folder,
            onValueChange = { folder = it },
            label = { Text(stringResource(R.string.sync_library_folder)) },
            placeholder = { Text(stringResource(R.string.sync_library_folder_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        // Blank is a real answer — the whole drive — so this is never disabled.
        Button(onClick = { onAdopt(folder.trim().trim('/')) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.setup_scan_folder))
        }
        SharingNote(isShare, Modifier.padding(top = 16.dp))
    }
}

/**
 * Says that adopting switches sharing on, before the tap rather than after.
 *
 * Adopting has to turn it on — it is what lets the next device skip the crawl, and leaving it off
 * is how one device ended up doing all the work for ever — but writing a file into someone's folder
 * is not something to do quietly. A share link is not written to at all, so it says nothing.
 */
@Composable
private fun SharingNote(isShare: Boolean, modifier: Modifier = Modifier) {
    if (isShare) return
    Text(
        stringResource(R.string.setup_sharing_note),
        color = Muted,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = modifier,
    )
}

/** The shelf really is empty, and a scan has already said so. */
@Composable
private fun EmptyShelf(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_empty_title), style = SerifTitle, color = Parchment)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_empty_hint),
            color = Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}
