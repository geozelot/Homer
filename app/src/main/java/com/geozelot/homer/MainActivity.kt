package com.geozelot.homer

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.geozelot.homer.ui.HomerApp
import com.geozelot.homer.ui.settings.AppLanguage
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
    /**
     * Applies the chosen interface language below Android 13, where there is no framework API for
     * it. Above that [AppLanguage.wrap] is the identity and the platform has already done it.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

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
