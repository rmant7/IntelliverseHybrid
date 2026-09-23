package com.matterofchoice.utils
import android.content.Context
import android.content.SharedPreferences


object LanguagePreferenceHelper {

    private const val PREFS_NAME = "app_language_prefs"
    private const val SELECTED_LANGUAGE_KEY = "selected_language_code"
    const val DEFAULT_APP_LANGUAGE_CODE = "en" // Your app's desired default
    const val SYSTEM_DEFAULT_MARKER_CODE = ""

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedLanguage(context: Context): String { // Returns non-nullable String
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SELECTED_LANGUAGE_KEY, null) ?: DEFAULT_APP_LANGUAGE_CODE
    }

    fun setSelectedLanguage(context: Context, languageCode: String) { // Takes non-nullable String
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SELECTED_LANGUAGE_KEY, languageCode).apply()
    }
}
