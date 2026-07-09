package com.geozelot.homer.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Homer is dark-first by design (the candlelit palette). We map the locked tokens onto a
// single Material dark scheme and don't offer a light variant or dynamic color — the warm
// amber-on-near-black identity is the whole point.
private val CandlelitScheme = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    primaryContainer = AmberDeep,
    onPrimaryContainer = OnAmber,
    secondary = Amber,
    onSecondary = OnAmber,
    tertiary = Sage,
    onTertiary = OnAmber,
    background = Ground,
    onBackground = Parchment,
    surface = Surface1,
    onSurface = Parchment,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    outline = Line,
    outlineVariant = Line,
    error = Danger,
    onError = OnAmber,
    scrim = Studio,
)

/**
 * App theme — always the candlelit dark scheme. Custom [HomerTypography] pairs a serif
 * (titles/wordmark) with the system sans (chrome). System bars are transparent with light
 * icons to sit on the dark ground.
 */
@Composable
fun HomerTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Force light system-bar icons: the app is always dark, so even on a light-mode
            // device the bars must sit on our near-black ground. (enableEdgeToEdge already
            // makes the bars transparent.)
            val window = (view.context as Activity).window
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = false
            insets.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = CandlelitScheme,
        typography = HomerTypography,
        content = content,
    )
}
