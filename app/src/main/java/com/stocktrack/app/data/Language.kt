package com.stocktrack.app.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The language of the app's screens, chosen by the Admin in Settings. English texts are in
 * res/values/strings.xml and Lithuanian ones in res/values-lt/strings.xml.
 * Reports and backup files are always in English, so a backup restores whatever the language.
 */
object Language {
    const val ENGLISH = "en"
    const val LITHUANIAN = "lt"
    const val PREF_KEY = "language"

    private fun saved(context: Context): String =
        context.getSharedPreferences(AppSettings.FILE, Context.MODE_PRIVATE)
            .getString(PREF_KEY, ENGLISH) ?: ENGLISH

    /** A context whose texts are in the chosen language, whatever language the tablet itself uses. */
    fun context(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(saved(base)))
        return base.createConfigurationContext(config)
    }
}
