package com.geozelot.homer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.geozelot.homer.ui.components.CoverImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.ui.formatCompactDuration

@Composable
fun HomeScreen(
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val bookCount by viewModel.bookCount.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val libraryRoot by viewModel.libraryRoot.collectAsStateWithLifecycle()

    val scanning = scanState is ScanState.Scanning

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Library", style = MaterialTheme.typography.headlineMedium)
                account?.let {
                    Text(
                        "${it.loginName} · ${it.serverUrl.substringAfter("://")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = viewModel::logout) { Text("Log out") }
        }

        OutlinedTextField(
            value = libraryRoot,
            onValueChange = viewModel::onLibraryRootChange,
            label = { Text("Library folder") },
            placeholder = { Text("e.g. Audiobooks (blank = whole drive)") },
            singleLine = true,
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = viewModel::scan, enabled = !scanning) {
                Text(if (scanning) "Scanning…" else "Scan library")
            }
            ScanStatus(scanState = scanState, bookCount = bookCount)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        if (books.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No books yet. Set your library folder and scan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(books, key = { it.id }) { book ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBookClick(book.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverImage(
                            model = book.coverModel,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                book.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    append(book.author ?: "Unknown author")
                                    append(" · ")
                                    append(if (book.isMultiFile) "${book.fileCount} files" else "single file")
                                    book.totalDurationMs?.takeIf { it > 0 }?.let {
                                        append(" · ")
                                        append(formatCompactDuration(it))
                                    }
                                    book.timeLeftMs?.let { left ->
                                        append(" · ")
                                        append(if (left <= 0) "finished" else "${formatCompactDuration(left)} left")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanStatus(scanState: ScanState, bookCount: Int) {
    when (val state = scanState) {
        is ScanState.Scanning -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            Text(
                "${state.directoriesVisited} folders · ${state.booksFound} books",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is ScanState.Done -> Text(
            "$bookCount books indexed",
            style = MaterialTheme.typography.bodySmall,
        )
        is ScanState.Error -> Text(
            state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        ScanState.Idle -> if (bookCount > 0) {
            Text("$bookCount books", style = MaterialTheme.typography.bodySmall)
        }
    }
}
