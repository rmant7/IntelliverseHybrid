package com.example.shared.domain.usecases

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SpeechConverter @Inject constructor(
    @ApplicationContext val context: Context
) : TextToSpeech.OnInitListener {

    // Preferred engine
    private object Engine {
        const val PICO = "com.svox.pico"
        const val GOOGLE = "com.google.android.tts"
    }

    private var textToSpeech: TextToSpeech? = null
    private var locale: Locale? = null
    var onUtteranceFinished: (String?) -> Unit = {}

    fun initialize() {
        textToSpeech = TextToSpeech(context, this, Engine.GOOGLE)
    }

    /**
     * True when the engine can speak [locale] itself, or -- now that
     * LanguageRegistry offers 418 languages instead of the old 49 -- when
     * TtsVoiceFallback knows a real, actually-available closest-sounding
     * substitute (e.g. Haitian Creole has no voice of its own, but its
     * lexifier French does, and TtsVoiceFallback says so). A caller that
     * only cares "can this be spoken at all" doesn't need to know which of
     * the two it got.
     */
    fun isLanguageSupported(locale: Locale): Boolean = resolveSpeakableLocale(locale) != null

    fun setLanguage(locale: Locale): Boolean {
        val resolvedLocale = resolveSpeakableLocale(locale) ?: return false
        if (textToSpeech != null) textToSpeech!!.setLanguage(resolvedLocale)
        else this.locale = resolvedLocale // Set language inside onInit()
        return true
    }

    /** [locale] if the engine can actually speak it right now, else its
     * TtsVoiceFallback substitute if THAT is actually available -- never a
     * locale this hasn't just confirmed live against the engine. */
    private fun resolveSpeakableLocale(locale: Locale): Locale? {
        if (isDirectlyAvailable(locale)) return locale
        val fallbackCode = TtsVoiceFallback.closestAvailable(locale.language) ?: return null
        val fallbackLocale = Locale(fallbackCode)
        return fallbackLocale.takeIf { isDirectlyAvailable(it) }
    }

    private fun isDirectlyAvailable(locale: Locale): Boolean {
        val result = textToSpeech?.isLanguageAvailable(locale)
        val langAvailableResults = arrayOf(
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        )
        return langAvailableResults.contains(result)
    }


    fun synthesizeToFile(text: String, fileName: String) {
        val dir = context.getExternalFilesDir(null)
        val path = "${dir}/${fileName}"
        textToSpeech?.synthesizeToFile(
            preprocessText(text), null, File(path), fileName
        ) // Async method
        Timber.d("File with path $path will be created.")
    }



    // Stop generating any utterance
    fun stopUtterance() {
        textToSpeech?.stop()
    }


    fun shutdown() {
        onUtteranceFinished = {}
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Timber.d("Successfully initialized SpeechConverter")
            textToSpeech!!.setOnUtteranceProgressListener(
                createUtteranceProgressListener()
            )
            if (locale != null) textToSpeech!!.setLanguage(locale)
        } else {
            Timber.d("SpeechConverter wasn't initialized")
        }
    }

    // Track speaking or synthesizing to file processes.
    private fun createUtteranceProgressListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Timber.d("Started utterance.")
            }

            override fun onDone(utteranceId: String?) {
                Timber.d("Finished utterance.")
                onUtteranceFinished(utteranceId)
            }

            @Deprecated(
                "Deprecated in Java", ReplaceWith(
                    "Timber.d(\"Error while producing utterance.\")",
                    "timber.log.Timber"
                )
            )
            override fun onError(utteranceId: String?) {
            }
        }
    }

    private fun preprocessText(text: String): String {
        val iconPattern = """[📅📋🔸🛑💡🏨🔗]""".toRegex()

        val cleanedText = text
            .replace(iconPattern, "")
            .replace("""\$\$?(.*?)\$\$?""".toRegex()) { matchResult ->
                matchResult.groupValues[1]
            }
            .replace("""[*_~`>|\\]""".toRegex(), "")
            .replace("""^#{1,6} """.toRegex(RegexOption.MULTILINE), "")
            .replace("""\[(.*?)]\((.*?)\)""".toRegex(), "$1")
            .replace("""!\[(.*?)]\((.*?)\)""".toRegex(), "$1")
            .replace("""`{3}.*?`{3}""".toRegex(RegexOption.DOT_MATCHES_ALL), "")
            .replace("""`.*?`""".toRegex(), "")
            .replace("""-{3,}""".toRegex(), "")

        return cleanedText.trim()
    }

}