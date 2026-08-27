package com.geozelot.homer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.storage.StorageMigrator
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Muted

/**
 * Hosts the storage-change dialogs **above the whole navigation graph**.
 *
 * These must not live inside a single screen: the change is now started from the settings pages, so
 * a dialog scoped to the library screen would never appear — and the load-vs-replace prompt is a
 * question the flow *waits on*, so missing it would silently stall the move. Keeping them here
 * means the prompt and the progress overlay show wherever the user happens to be.
 */
@Composable
fun StorageDialogsHost(viewModel: HomeViewModel) {
    val pendingStorage by viewModel.pendingStorageChange.collectAsStateWithLifecycle()
    val migration by viewModel.migrationProgress.collectAsStateWithLifecycle()

    pendingStorage?.let {
        StorageConflictDialog(
            onLoad = viewModel::loadPendingStorage,
            onReplace = viewModel::replacePendingStorage,
            onCancel = viewModel::cancelPendingStorage,
        )
    }
    migration?.let { MigrationDialog(it) }
}

/** Asked when the chosen storage folder already holds a Homer library. */
@Composable
private fun StorageConflictDialog(onLoad: () -> Unit, onReplace: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.home_storage_conflict_title)) },
        text = { Text(stringResource(R.string.home_storage_conflict_body)) },
        confirmButton = { HomerTextButton(onClick = onLoad) { Text(stringResource(R.string.home_storage_load)) } },
        dismissButton = {
            Row {
                HomerTextButton(onClick = onReplace) { Text(stringResource(R.string.home_storage_replace)) }
                HomerTextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** Blocking overlay shown while a storage move runs (not cancelable). */
@Composable
private fun MigrationDialog(progress: StorageMigrator.Progress) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.home_migration_title)) },
        text = {
            Column {
                Text(progress.label, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = Amber,
                    )
                    Text(
                        stringResource(R.string.home_migration_files, progress.done, progress.total),
                        color = Faint,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Amber)
                }
            }
        },
        confirmButton = {},
    )
}
