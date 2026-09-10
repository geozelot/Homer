package com.geozelot.homer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.HomerSwitch
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.SectionLabel

// ── Top bar ──────────────────────────────────────────────────────────────────

@Composable
internal fun PlayerTopBar(
    started: Boolean,
    offline: Boolean,
    downloading: Boolean,
    canShowDetails: Boolean,
    onBack: () -> Unit,
    onMarkCompleted: () -> Unit,
    onToggleOffline: () -> Unit,
    onDetails: () -> Unit,
    onBookmarks: () -> Unit,
    onHelp: () -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.action_back), tint = Muted)
        }
        Text(stringResource(R.string.player_now_playing), style = SectionLabel, color = Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Beside the menu rather than inside it: it explains what is on the screen, which is
            // not the same kind of thing as the actions the menu holds.
            IconButton(onClick = onHelp) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = stringResource(R.string.home_cd_help),
                    tint = Muted,
                )
            }
            Box {
                IconButton(onClick = { overflowOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more), tint = Muted)
                }
                // The same order as the library's own book menu, and for the same reason a menu has an
                // order at all: what you look at, then what you change, then what this device does with
                // the file. Reading is first because it is what most taps are after; the destructive
                // one sits behind a rule of its own.
                DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                    // DETAILS, not Edit. The library's own menus lead here too, and editing is one
                    // level inside it — which is the right order: you look at a book before deciding it
                    // is wrong. Reaching Edit directly from the player skipped the looking, and made the
                    // player the one place where a book's facts were unreachable while it was playing.
                    if (canShowDetails) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_details)) },
                            onClick = {
                                onDetails()
                                overflowOpen = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_bookmarks)) },
                        onClick = {
                            onBookmarks()
                            overflowOpen = false
                        },
                    )
                    if (started) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mark_completed)) },
                            onClick = {
                                onMarkCompleted()
                                overflowOpen = false
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_offline)) },
                        trailingIcon = {
                            if (downloading) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Amber, strokeWidth = 2.dp)
                            } else {
                                HomerSwitch(checked = offline, onCheckedChange = {
                                    onToggleOffline()
                                    overflowOpen = false
                                })
                            }
                        },
                        // Dismiss like the other items: leaving the menu open invited repeated taps
                        // that queued download → delete → download.
                        onClick = {
                            onToggleOffline()
                            overflowOpen = false
                        },
                    )
                }
            }
        }
    }
}
