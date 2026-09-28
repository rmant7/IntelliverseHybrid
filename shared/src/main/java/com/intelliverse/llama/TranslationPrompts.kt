package com.intelliverse.llama

import com.intelliverse.models.LocalModelSeed

/**
 * Model-specific prompt shapes -- ported (simplified) from rmant7/AI's own
 * TranslationActivity.kt buildPrompt()/buildOmniTranslatePrompt(). That
 * project builds the target language code from a full language picker with
 * its own ISO-639-3/script mapping; this quick-test path instead takes
 * whatever language code/name the caller typed directly, mapped through
 * [OMNITRANSLATE_CODES] for the one model that actually requires that exact
 * format.
 */
object TranslationPrompts {
    /**
     * [targetLangCode] is a bare ISO-639-1 code ("he") -- what MADLAD's tag
     * format and the OmniTranslate lookup table both key on. [targetLangName]
     * is the full English name ("Hebrew language") -- what an ordinary chat
     * model actually needs: a real device test asked TranslateGemma for "he"
     * verbatim and got Arabic back (a bare 2-letter code is genuinely
     * ambiguous to a small model outside a fixed code table, unlike
     * OmniTranslate/MADLAD which were fine-tuned on exact codes). Matches
     * rmant7/AI's own proven buildChatPrompt, which always passed a full
     * language name, never a bare code, to this class of model.
     */
    fun buildPrompt(seed: LocalModelSeed, targetLangCode: String, targetLangName: String, text: String): String = when {
        // MADLAD-400's own T5 tag format -- no chat framing at all.
        seed.isT5EncoderDecoder -> "<2$targetLangCode> $text"
        // OmniTranslate's model card documents an ISO-639-3 + script code
        // ("rus_Cyrl", not "ru") as performing much better than a bare
        // 2-letter code or language name -- confirmed on a real device: a
        // plain "ru" was silently misread by the model itself as "sul_Latn"
        // (Surigaonon), producing nonsense. Falls back to whatever was
        // typed for a language not in this short table, rather than
        // blocking the test entirely.
        seed.id == "omnitranslate-1-1" -> {
            val code = OMNITRANSLATE_CODES[targetLangCode.trim().lowercase()] ?: targetLangCode
            "Translate to $code: $text"
        }
        // Ordinary decoder-only chat GGUF (TranslateGemma) -- a plain
        // instruction through its own chat template, full language name.
        else -> "Translate the following text to $targetLangName. Reply with only the translation, no explanation:\n\n$text"
    }

    /**
     * OmniTranslate (a Qwen3 fine-tune) emits a `<think>...</think>`
     * reasoning block before its actual answer -- confirmed on a real
     * device. Stripped before display; this quick-test screen shows the
     * final answer, not the model's reasoning trace.
     */
    fun stripThinking(text: String): String =
        text.replace(Regex("(?s)<think>.*?</think>"), "").trim()

    // A short table of common languages, not a full ISO-639-3 mapping --
    // matches this quick-test screen's own scope (a raw text field, no
    // language picker). Unmapped input is passed through as-is.
    private val OMNITRANSLATE_CODES = mapOf(
        "ru" to "rus_Cyrl",
        "en" to "eng_Latn",
        "he" to "heb_Hebr",
        "es" to "spa_Latn",
        "fr" to "fra_Latn",
        "de" to "deu_Latn",
        "zh" to "zho_Hans",
        "ar" to "arb_Arab",
        "pt" to "por_Latn",
        "ja" to "jpn_Jpan",
        "ko" to "kor_Hang",
        "it" to "ita_Latn",
        "tr" to "tur_Latn",
        "pl" to "pol_Latn",
        "uk" to "ukr_Cyrl",
        "nl" to "nld_Latn",
    )
}
