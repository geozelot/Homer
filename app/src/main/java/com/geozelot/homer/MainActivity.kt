package com.geozelot.homer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.geozelot.homer.ui.theme.HomerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Real navigation (library → book → player) lands in a later
 * phase; P0 renders a themed placeholder to confirm the toolchain, Hilt wiring, and
 * Compose surface all build and run.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Homer",
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            text = "Nextcloud audiobooks",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    HomerTheme {
        Text("Homer")
    }
}
