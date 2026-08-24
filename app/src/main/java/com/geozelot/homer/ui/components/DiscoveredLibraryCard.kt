package com.geozelot.homer.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.data.library.DiscoveredLibrary
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface1

/**
 * One folder the discovery sweep found, with what it already holds and a way to adopt it.
 *
 * Shared between the Library screen and first-run setup on purpose: the two are asking the same
 * question — is this the library? — and a card that looked different in each place would suggest
 * they were different decisions.
 */
@Composable
fun DiscoveredLibraryCard(
    library: DiscoveredLibrary,
    onUse: (String) -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = stringResource(R.string.sync_use_as_library),
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (library.isCurrentRoot) AmberSoft else Surface1)
            .border(1.dp, if (library.isCurrentRoot) Amber else Line, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                library.relativePath.ifEmpty { stringResource(R.string.sync_home_files_root) },
                color = Parchment,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (library.isCurrentRoot) TagChip(stringResource(R.string.sync_tag_in_use), OnAmber, Amber)
        }
        Text(
            libraryDetail(context, library),
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
        if (!library.isCurrentRoot) {
            TextButton(
                onClick = { onUse(library.relativePath) },
                contentPadding = SettingsActionPadding,
            ) { Text(actionLabel) }
        }
    }
}

/** What the folder is, and what it already holds — the whole basis for adopting it. */
private fun libraryDetail(context: Context, library: DiscoveredLibrary): String = buildString {
    append(
        when (library.kind) {
            DiscoveredLibrary.Kind.FILES_ROOT -> context.getString(R.string.sync_kind_files_root)
            DiscoveredLibrary.Kind.LIBRARY_ROOT -> context.getString(R.string.sync_kind_library_root)
            DiscoveredLibrary.Kind.SHARED_FOLDER -> context.getString(R.string.sync_kind_shared_folder)
        },
    )
    if (library.hasSharedCatalog) {
        append(context.getString(R.string.set_detail_shared_index))
        library.bookCount?.let {
            append(context.resources.getQuantityString(R.plurals.sync_detail_book_count, it, it))
        }
    }
    if (library.hasPrivateIndex) append(context.getString(R.string.sync_detail_private_progress))
    library.owner?.let { append(context.getString(R.string.sync_detail_owner, it)) }
}
