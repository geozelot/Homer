package com.geozelot.homer

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.geozelot.homer.ui.HomerApp
import com.geozelot.homer.ui.settings.AppLanguage
import com.geozelot.homer.ui.settings.DisplayScale
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
     * it (above that [AppLanguage.wrap] is the identity and the platform has already done it), and
     * Homer's own display size, which is the identity at 100%.
     *
     * Here and not in the Application: a display size is a fact about windows, and the application
     * context draws none. Notifications and workers are better off at the density the system gave.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DisplayScale.wrap(AppLanguage.wrap(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The volume keys belong to the book while Homer is on screen. Android only routes them to
        // media on its own while audio is actually coming out; paused, loading or between books, the
        // keys changed the RINGER from inside an audiobook player — the volume panel showed the
        // ring slider, and the book was just as loud when play was pressed again.
        volumeControlStream = AudioManager.STREAM_MUSIC
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
