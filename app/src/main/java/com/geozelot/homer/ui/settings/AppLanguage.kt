package com.geozelot.homer.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * A language Homer's interface can be shown in.
 *
 * [tag] is a BCP-47 language tag, or empty for "follow the system" — which is what
 * [AppCompatDelegate] uses to mean "no app-specific choice", so the empty tag is not a sentinel
 * this file invented.
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
         * The language in force, read from the platform rather than from Homer's own settings.
         *
         * Deliberately not persisted in DataStore. Android 13+ stores the choice itself and shows
         * it in system settings, and AppCompat's backport persists it below that (see the
         * `AppLocalesMetadataHolderService` declaration in the manifest) — so a copy here would be
         * a second source of truth that drifts the moment the user changes it from the system
         * screen instead of from Homer.
         */
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales()
                .takeIf { !it.isEmpty }
                ?.get(0)
                ?.language
                ?: return SYSTEM
            return entries.firstOrNull { it.tag == tag } ?: SYSTEM
        }

        /** Applies [language]; the platform recreates the activity so strings re-resolve. */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                if (language == SYSTEM) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(language.tag)
                },
            )
        }
    }
}
