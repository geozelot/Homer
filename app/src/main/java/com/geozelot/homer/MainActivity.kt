package com.geozelot.homer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.geozelot.homer.ui.HomerApp
import com.geozelot.homer.ui.lock.BiometricGate
import com.geozelot.homer.ui.theme.HomerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Renders [HomerApp], which gates between login and library
 * based on the current Nextcloud account state. Extends [FragmentActivity] so the
 * optional [BiometricGate] can drive a BiometricPrompt.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomerTheme {
                BiometricGate {
                    HomerApp()
                }
            }
        }
    }
}
