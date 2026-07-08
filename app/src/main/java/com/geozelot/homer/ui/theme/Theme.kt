package com.geozelot.homer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Accent,
    secondary = AccentLight,
    background = Parchment,
    onBackground = Ink,
    surface = Parchment,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = AccentLight,
    secondary = Accent,
)

/**
 * App theme. Honors Material You dynamic color on Android 12+ when available,
 * falling back to Homer's seed palette otherwise.
 */
@Composable
fun HomerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HomerTypography,
        content = content,
    )
}
