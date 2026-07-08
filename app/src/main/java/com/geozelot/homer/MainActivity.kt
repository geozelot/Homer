package com.geozelot.homer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.geozelot.homer.ui.HomerApp
import com.geozelot.homer.ui.theme.HomerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Renders [HomerApp], which gates between login and library
 * based on the current Nextcloud account state.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomerTheme {
                HomerApp()
            }
        }
    }
}
