package com.intelliverse.llama

import com.intelliverse.models.LocalModelSeed

/**
 * Model-specific prompt shapes -- ported (simplified) from rmant7/AI's own
 * TranslationActivity.kt buildPrompt()/buildOmniTranslatePrompt(). That
 * project builds the target language code from a full language picker with
 * its own ISO-639-3/script mapping; this quick-test path instead takes
 * whatever language code/name the caller typed directly, since there's no
 * language picker here yet.
 */
object TranslationPrompts {
    fun buildPrompt(seed: LocalModelSeed, targetLang: String, text: String): String = when {
        // MADLAD-400's own T5 tag format -- no chat framing at all.
        seed.isT5EncoderDecoder -> "<2$targetLang> $text"
        // OmniTranslate's model card documents this exact "Translate to X:"
        // shape as performing much better than an ordinary chat instruction.
        seed.id == "omnitranslate-1-1" -> "Translate to $targetLang: $text"
        // Ordinary decoder-only chat GGUF (TranslateGemma) -- a plain
        // instruction through its own chat template.
        else -> "Translate the following text to $targetLang. Reply with only the translation, no explanation:\n\n$text"
    }
}
