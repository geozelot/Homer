package com.geozelot.homer.ui.settings

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import kotlin.math.roundToInt

/**
 * Homer's own display size: everything it draws, larger or smaller, the way Android's
 * Settings › Display › Display size does it for the whole phone.
 *
 * ## How
 *
 * The same way the system setting does it: by changing the density the activity's resources report.
 * Every dp and sp in the app is converted to pixels through that density, so text, icons, touch
 * targets and spacing all move together, and a phone 411dp wide at 100% is 316dp wide at 130% —
 * which the layouts that choose between a phone and a tablet arrangement then see, exactly as they
 * would under the system setting.
 *
 * The screen's size in dp is rescaled alongside the density rather than left to Android, because
 * Android computes it from the REAL density and never recomputes it for an overridden one. Left
 * alone, the help card, the details card and the player's sheets — which size themselves from
 * `screenWidthDp` — would ask for a width the screen no longer has.
 *
 * ## Why the activity's configuration and not Compose's density
 *
 * Overriding `LocalDensity` at the root of the composition was the lighter option, and it stops at
 * the edge of the window: every dialog, menu and popup opens a window of its own whose composition
 * re-reads the density from its context. The confirm dialogs, the book menu and every dropdown
 * would have stayed at the old size inside a rescaled app.
 *
 * The override is computed afresh each time the activity is created, and Homer declares no
 * `configChanges` — so a rotation, a window resize or a change to the system's own display size
 * all recreate the activity and pass through [wrap] again. A new value is applied the same way:
 * [apply] recreates the activity, as a language change does.
 *
 * Not scaled, because Homer does not draw them: the status bar, notifications, the lock-screen
 * controls, and the system's own sheets (the biometric prompt, the folder picker).
 *
 * Stored in SharedPreferences rather than the settings DataStore for the reason [AppLanguage] is:
 * `attachBaseContext` runs before anything else, and has to have the answer synchronously.
 */
object DisplayScale {
    const val DEFAULT = 100
    const val MIN = 80
    const val MAX = 130
    const val STEP = 5

    private const val PREFS = "homer_display"
    private const val KEY_PERCENT = "display_scale_percent"

    /** The display size in force, in percent. */
    fun current(context: Context): Int = snap(
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PERCENT, DEFAULT),
    )

    /** Stores [percent] and redraws the activity at it. */
    fun apply(context: Context, percent: Int) {
        val value = snap(percent)
        if (value == current(context)) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { if (value == DEFAULT) remove(KEY_PERCENT) else putInt(KEY_PERCENT, value) }
        context.findActivity()?.recreate()
    }

    /**
     * The context the activity should run against: [base] itself at 100%, otherwise a copy whose
     * resources report the scaled density and the screen size measured in it.
     */
    fun wrap(base: Context): Context {
        val percent = current(base)
        if (percent == DEFAULT) return base
        return base.createConfigurationContext(scaled(base.resources.configuration, percent))
    }

    /** [source] as it would read at [percent] of its density. */
    internal fun scaled(source: Configuration, percent: Int): Configuration {
        // Nothing to scale from. A real activity is always told its density; this is for the one
        // that is not, which would otherwise divide by zero below.
        if (source.densityDpi == Configuration.DENSITY_DPI_UNDEFINED) return source
        val dpi = (source.densityDpi * percent / 100f).roundToInt()
        // From the dpi actually applied, not from the percentage: the dpi is an integer, and the
        // screen measured against it has to agree with it to the pixel.
        val ratio = source.densityDpi.toFloat() / dpi
        // Down, never up. A screen reported a dp wider than it is lays out a dp past its edge.
        fun rescale(dp: Int) = if (dp == Configuration.SCREEN_WIDTH_DP_UNDEFINED) dp else (dp * ratio).toInt()
        return Configuration(source).apply {
            densityDpi = dpi
            screenWidthDp = rescale(source.screenWidthDp)
            screenHeightDp = rescale(source.screenHeightDp)
            smallestScreenWidthDp = rescale(source.smallestScreenWidthDp)
        }
    }

    /** Onto the slider's own steps and inside its range, whatever a stored value says. */
    internal fun snap(percent: Int): Int {
        val stepped = MIN + ((percent - MIN).toFloat() / STEP).roundToInt() * STEP
        return stepped.coerceIn(MIN, MAX)
    }
}
