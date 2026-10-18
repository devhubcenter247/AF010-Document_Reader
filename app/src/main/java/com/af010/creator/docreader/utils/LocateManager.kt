package com.af010.creator.docreader.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object LocateManager {
    private const val PREF_NAME = "data"
    private const val KEY_LANGUAGE = "KEY_LANGUAGE"

    /**
     * Where the user's chosen language lives, when the app keeps it outside this module
     * (set once in Application.onCreate). Null falls back to what [saveLocale] stored.
     */
    var languageProvider: ((Context) -> String?)? = null

    private var cacheLocateInit = ""

    // In-memory copy so attachBaseContext doesn't hit SharedPreferences for every activity.
    @Volatile
    private var savedLanguage: String? = null


    fun initDeviceLocate() {
        cacheLocateInit = Resources.getSystem().configuration.locales[0].language
    }

    fun getDeviceLanguageCode(): String =
        cacheLocateInit.ifEmpty { Resources.getSystem().configuration.locales[0].language }



    fun createLocale(context: Context, languageCode: String): Context {
        val parts = languageCode.split("_")
        val locale = if (parts.size > 1) {
            val region = parts[1].removePrefix("r")
            Locale.Builder()
                .setLanguage(parts[0])
                .setRegion(region)
                .build()
        } else {
            Locale.Builder()
                .setLanguage(parts[0])
                .build()
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(context.packageName + PREF_NAME, Context.MODE_PRIVATE)
}