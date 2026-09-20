package com.matterofchoice.utils

import android.content.res.Resources
import androidx.compose.ui.geometry.isEmpty


import android.content.Context
import android.content.res.Configuration // Import Configuration
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.text.intl.LocaleList
import androidx.core.os.LocaleListCompat
import java.util.Locale // Import Locale
object LocaleHelper {

    fun setLocale(context: Context, languageCode: String?): Context {
        if (languageCode != null) {
            LanguagePreferenceHelper.setSelectedLanguage(context.applicationContext, languageCode)
        }
        Log.d("LocaleHelper", "Persisted language: $languageCode")

        val localeListToApply = if (!languageCode.isNullOrEmpty()) {
            LocaleListCompat.forLanguageTags(languageCode)
        } else {
            LocaleListCompat.getEmptyLocaleList()
        }

        AppCompatDelegate.setApplicationLocales(localeListToApply)
        Log.d("LocaleHelper", "Called AppCompatDelegate.setApplicationLocales. Effective app locales now: ${AppCompatDelegate.getApplicationLocales()}")
        return updateResources(context, languageCode)
    }
    fun onAttach(baseContext: Context): Context {
        val persistedLanguage = LanguagePreferenceHelper.getSelectedLanguage(baseContext.applicationContext)
        Log.d("LocaleHelper", "onAttach - Persisted language: $persistedLanguage")

        val localeListToApply = if (!persistedLanguage.isNullOrEmpty()) {
            LocaleListCompat.forLanguageTags(persistedLanguage)
        } else {
            LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(localeListToApply)
        Log.d("LocaleHelper", "onAttach - Called AppCompatDelegate.setApplicationLocales. Effective app locales: ${AppCompatDelegate.getApplicationLocales()}")

        return updateResources(baseContext, persistedLanguage)
    }

    @Suppress("DEPRECATION") // For Locale.setDefault and updateConfiguration on older APIs
    private fun updateResources(context: Context, languageCode: String?): Context {
        val resources: Resources = context.resources
        val configuration: Configuration = resources.configuration

        val localeToSet: Locale? = if (!languageCode.isNullOrEmpty()) {
            Locale.forLanguageTag(languageCode)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Resources.getSystem().configuration.locales[0]
            } else {
                Resources.getSystem().configuration.locale
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeListToApplyOnConfig = if (!languageCode.isNullOrEmpty()) {
                val locale = Locale.forLanguageTag(languageCode)
                android.os.LocaleList(locale)
            } else {
                Resources.getSystem().configuration.locales
            }
            configuration.setLocales(localeListToApplyOnConfig)
        } else {
            if (localeToSet != null) {
                configuration.setLocale(localeToSet)
            }
        }

        return context.createConfigurationContext(configuration)
    }

    fun applyPersistedAppCompatDelegate(appContext: Context) {
        val selectedLangCode = LanguagePreferenceHelper.getSelectedLanguage(appContext)
        Log.d("LocaleHelper", "applyPersistedAppCompatDelegate: $selectedLangCode")
        val localeListToApply = if (!selectedLangCode.isNullOrEmpty()) {
            LocaleListCompat.forLanguageTags(selectedLangCode)
        } else {
            LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(localeListToApply)
        Log.d("LocaleHelper", "applyPersistedAppCompatDelegate - Effective app locales: ${AppCompatDelegate.getApplicationLocales()}")
    }
}
