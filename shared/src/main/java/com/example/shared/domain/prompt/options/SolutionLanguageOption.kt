package com.example.shared.domain.prompt.options

import android.content.Context
import com.example.shared.R
import java.util.Locale

enum class SolutionLanguageOption(
    val arrayIndex: Int,
    val languageName: String,
    val languageTag: String,
    val locale: String,
    val isRtl: Boolean
) {
    AMHARIC(0, "Amharic language", "am-ET", "am", true),
    ARABIC(1, "Arabic language", "ar-AE", "ar", true),
    BENGALI(2, "Bengali language", "bn-BD", "bn", false),
    BULGARIAN(3, "Bulgarian language", "bg-BG", "bg", false),
    CHINESE_SIMPLIFIED(4, "Chinese Simplified language", "zh-CN", "zh", false),
    CROATIAN(5, "Croatian language", "hr-HR", "hr", false),
    CZECH(6, "Czech language", "cs-CZ", "cs", false),
    DANISH(7, "Danish language", "da-DK", "da", false),
    DUTCH(8, "Dutch language", "nl-NL", "nl", false),
    ENGLISH(9, "English language", "en-US", "en", false),
    ESTONIAN(10, "Estonian language", "et-EE", "et", false),
    FINNISH(11, "Finnish language", "fi-FI", "fi", false),
    FRENCH(12, "French language", "fr-FR", "fr", false),
    GERMAN(13, "German language", "de-DE", "de", false),
    GREEK(14, "Greek language", "el-GR", "el", false),
    GUJARATI(15, "Gujarati language", "gu-IN", "gu", false),
    HEBREW(16, "Hebrew language", "iw-IL", "he", true),
    HINDI(17, "Hindi language", "hi-IN", "hi", false),
    HUNGARIAN(18, "Hungarian language", "hu-HU", "hu", false),
    INDONESIAN(19, "Indonesian language", "id-ID", "id", false),
    ITALIAN(20, "Italian language", "it-IT", "it", false),
    JAPANESE(21, "Japanese language", "ja-JP", "ja", false),
    KANNADA(22, "Kannada language", "kn-IN", "kn", false),
    KOREAN(23, "Korean language", "ko-KR", "ko", false),
    LATVIAN(24, "Latvian language", "lv-LV", "lv", false),
    LITHUANIAN(25, "Lithuanian language", "lt-LT", "lt", false),
    MALAY(26, "Malay language", "ms-MY", "ms", false),
    MARATHI(27, "Marathi language", "mr-IN", "mr", false),
    NORWEGIAN(28, "Norwegian language", "no-NO", "no", false),
    PASHTO(29, "Pashto language", "ps-AF", "ps", true),
    PERSIAN(30, "Persian (Farsi)", "fa-IR", "fa", true),
    POLISH(31, "Polish language", "pl-PL", "pl", false),
    PORTUGUESE(32, "Portuguese language", "pt-PT", "pt", false),
    ROMANIAN(33, "Romanian language", "ro-RO", "ro", false),
    RUSSIAN(34, "Russian language", "ru-RU", "ru", false),
    SERBIAN(35, "Serbian language", "sr-RS", "sr", false),
    SLOVAK(36, "Slovak language", "sk-SK", "sk", false),
    SLOVENIAN(37, "Slovenian language", "sl-SI", "sl", false),
    SPANISH(38, "Spanish language", "es-ES", "es", false),
    SWAHILI(39, "Swahili language", "sw-TZ", "sw", false),
    SWEDISH(40, "Swedish language", "sv-SE", "sv", false),
    TAGALOG(41, "Tagalog (Filipino)", "tl-PH", "tl", false),
    TAMIL(42, "Tamil language", "ta-IN", "ta", false),
    TELUGU(43, "Telugu language", "te-IN", "te", false),
    THAI(44, "Thai language", "th-TH", "th", false),
    TURKISH(45, "Turkish language", "tr-TR", "tr", false),
    UKRAINIAN(46, "Ukrainian language", "uk-UA", "uk", false),
    URDU(47, "Urdu language", "ur-PK", "ur", true),
    VIETNAMESE(48, "Vietnamese language", "vi-VN", "vi", false);

    fun getString(context: Context): String {
        val solutionLanguageArray = context.resources.getStringArray(R.array.languages)
        return solutionLanguageArray[arrayIndex]
    }

    companion object {

        val DEFAULT: SolutionLanguageOption
            get() = SolutionLanguageOption.ENGLISH

        fun getByIndex(index: Int): SolutionLanguageOption {
            return entries[index]
        }

        fun fromLocale(locale: Locale): SolutionLanguageOption? {
            val language = locale.language
            // Handle Hebrew special case
            if (language == "iw" || language == "he") {
                return HEBREW
            }
            // General case
            return entries.find { it.locale == language }
        }
    }
}