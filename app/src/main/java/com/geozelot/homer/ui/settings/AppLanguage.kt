package com.geozelot.homer.ui.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * A language Homer's interface can be shown in.
 *
 * [tag] is a BCP-47 language tag, empty for "follow the system".
 *
 * The list has to stay in step with `res/xml/locales_config.xml`: that file is what Android 13+
 * reads to offer Homer its own entry under Settings › Apps › Homer › Language, and a language
 * offered here but missing there is selectable in-app and invisible to the system.
 */
enum class AppLanguage(val tag: String, val label: String) {
    /** Whatever the device is set to, falling back to English for anything not translated. */
    SYSTEM("", "System"),
    ENGLISH("en", "English"),
    GERMAN("de", "Deutsch"),
    ;

    companion object {
        /**
         * Why this talks to the platform rather than to `AppCompatDelegate`.
         *
         * `AppCompatDelegate.setApplicationLocales` finds the system's locale manager by iterating
         * the AppCompat **delegates** that activities have registered — and Homer has none, because
         * `MainActivity` is a `FragmentActivity` and the app theme is a platform Material theme, on
         * purpose. With no delegate registered the lookup returns null and the call does nothing at
         * all, silently: no exception, no log line, the setting simply never changes.
         *
         * Making the activity an `AppCompatActivity` would fix it and would drag AppCompat theming
         * into an app that deliberately has none. So Homer does what AppCompat would have done:
         * the framework's own API above Android 13, and its own small implementation below.
         */
        private const val PREFS = "homer_locale"
        private const val KEY_TAG = "app_locale_tag"

        /** The language in force. */
        fun current(context: Context): AppLanguage {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java)
                    ?.applicationLocales
                    ?.takeIf { !it.isEmpty }
                    ?.get(0)
                    ?.language
            } else {
                stored(context)?.language
            } ?: return SYSTEM
            return entries.firstOrNull { it.tag == tag } ?: SYSTEM
        }

        /**
         * Applies [language].
         *
         * Above Android 13 the framework owns the choice: it stores it, shows it in system settings,
         * and restarts the activity itself. Below that Homer stores it and restarts the activity by
         * hand — [wrap] is what actually puts the locale into the resources on the way back up.
         */
        fun apply(context: Context, language: AppLanguage) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                    if (language == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
                return
            }
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .apply { if (language == SYSTEM) remove(KEY_TAG) else putString(KEY_TAG, language.tag) }
                .apply()
            // Only the activity is recreated. The application's base context was wrapped once at
            // startup and cannot be re-wrapped in place, so the chosen language reaches
            // notifications and workers on the next launch rather than this instant — which is why
            // this branch does not exist above Android 13, where the framework restarts the process
            // state properly.
            findActivity(context)?.recreate()
        }

        /**
         * The context a component should run against: the same one below Android 13 unless a
         * language has been chosen, in which case a copy carrying it.
         *
         * Called from `attachBaseContext` in both the Application and the Activity, so the choice
         * reaches Compose's `stringResource` AND the strings that workers and notifications build
         * off the application context.
         *
         * Above Android 13 this is the identity: the framework has already applied the per-app
         * locale to everything by the time any of this runs, and layering a second copy on top
         * would only pin the app to a locale the user can still change from system settings.
         */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val locale = stored(base) ?: return base
            Locale.setDefault(locale)
            val configuration = android.content.res.Configuration(base.resources.configuration)
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            return base.createConfigurationContext(configuration)
        }

        private fun stored(context: Context): Locale? =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TAG, null)
                ?.takeIf { it.isNotBlank() }
                ?.let(Locale::forLanguageTag)

        /** The Activity behind a Compose `LocalContext`, which is usually a wrapper around one. */
        private fun findActivity(context: Context): Activity? {
            var current = context
            while (current is ContextWrapper) {
                if (current is Activity) return current
                current = current.baseContext
            }
            return null
        }
    }
}
