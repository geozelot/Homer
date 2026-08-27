package com.geozelot.homer.ui.storage

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Ground
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle
import com.geozelot.homer.ui.theme.Surface1
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A minimal on-device folder browser for choosing an all-files storage location — the fallback
 * when the SAF picker won't grant a folder. First ensures MANAGE_EXTERNAL_STORAGE is granted
 * (sending the user to the system settings screen), then lets them navigate the filesystem and
 * pick (or create) a folder. Drawn as a full-screen overlay over the library.
 */
@Composable
fun StorageBrowserScreen(onPicked: (String) -> Unit, onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Ground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            val context = LocalContext.current

            // Re-check access whenever we come back from the system settings screen.
            var resumeTick by remember { mutableIntStateOf(0) }
            val lifecycleOwner = LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            val hasAccess = remember(resumeTick) {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Muted)
                }
                Text(stringResource(R.string.storage_choose_folder_title), style = SerifTitle.copy(fontSize = 22.sp), color = Parchment, modifier = Modifier.padding(start = 4.dp))
            }

            if (!hasAccess) {
                Text(
                    stringResource(R.string.storage_grant_rationale),
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                )
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }.onFailure {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
                ) { Text(stringResource(R.string.storage_grant_button)) }
                return@Column
            }

            val root = remember { Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0") }
            var dir by remember { mutableStateOf(root) }
            val scope = rememberCoroutineScope()
            // Enumerate directories off the main thread — listFiles() touches the disk and would
            // block composition (an ANR on a large/slow folder) if done inline.
            val entries by produceState(initialValue = emptyList<File>(), dir, resumeTick) {
                value = withContext(Dispatchers.IO) {
                    dir.listFiles()?.filter { it.isDirectory && !it.isHidden }?.sortedBy { it.name.lowercase() } ?: emptyList()
                }
            }

            Text(dir.absolutePath, color = Amber, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

            // Content padding + spacing: the listing used to read as one solid block of Surface1
            // with its first row butted right up against the path above it.
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (dir.parentFile != null && dir.absolutePath != root.absolutePath) {
                    item {
                        FolderRow(name = "..", onClick = { dir.parentFile?.let { dir = it } })
                    }
                }
                items(entries, key = { it.absolutePath }) { child ->
                    FolderRow(name = child.name, onClick = { dir = child })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomerTextButton(onClick = {
                    scope.launch {
                        val created = withContext(Dispatchers.IO) { File(dir, "Homer").apply { mkdirs() } }
                        if (withContext(Dispatchers.IO) { created.isDirectory }) dir = created
                    }
                }) { Text(stringResource(R.string.storage_new_folder)) }
                Button(
                    onClick = {
                        scope.launch {
                            if (withContext(Dispatchers.IO) { dir.canWrite() }) onPicked(dir.absolutePath)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
                ) { Text(stringResource(R.string.storage_use_folder)) }
            }
        }
    }
}

@Composable
private fun FolderRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Each folder is its own rounded card (with the hairline outline the rest of the app
            // uses for grouped content), so rows read as separate targets.
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
        Text(name, color = Parchment, fontSize = 14.sp, fontFamily = if (name == "..") FontFamily.Monospace else FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
