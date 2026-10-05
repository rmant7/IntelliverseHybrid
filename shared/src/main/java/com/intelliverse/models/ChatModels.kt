package com.intelliverse.models

/**
 * On-device chat models -- the same entries rmant7/AI ships and checked on a
 * Pixel 10 Pro (text and translation pass there; images are not used in this
 * app yet). Each also translates, by instruction, so it is offered for both.
 * Smallest first.
 */
object ChatModels {
    private val CHAT_AND_TRANSLATION = setOf(ModelPurpose.CHAT, ModelPurpose.TRANSLATION)

    val ALL: List<LocalModelSeed> = listOf(
        LocalModelSeed(
            id = "qwen3.5-0.8b-q4",
            title = "Qwen3.5 0.8B",
            repoIds = listOf("unsloth/Qwen3.5-0.8B-GGUF"),
            paramsLabel = "0.8B · Q4",
            note = "Tiny and fast; short answers, weak on facts.",
            approxSizeBytes = 550_000_000,
            purposes = CHAT_AND_TRANSLATION,
        ),
        LocalModelSeed(
            id = "qwen3.5-4b-q4",
            title = "Qwen3.5 4B",
            repoIds = listOf("unsloth/Qwen3.5-4B-GGUF", "bartowski/Qwen3.5-4B-GGUF"),
            paramsLabel = "4B · Q4",
            note = "Good all-rounder for a phone; thinks before it answers.",
            approxSizeBytes = 2_500_000_000,
            purposes = CHAT_AND_TRANSLATION,
        ),
        LocalModelSeed(
            id = "gemma-4-e2b-it-q4",
            title = "Gemma 4 E2B",
            // Google's own QAT build first: the exact repository rmant7/AI checked on a Pixel 10 Pro.
            repoIds = listOf("google/gemma-4-E2B-it-qat-q4_0-gguf", "unsloth/gemma-4-E2B-it-GGUF"),
            paramsLabel = "E2B · Q4_0 QAT",
            note = "Google's small Gemma 4: fast, many languages.",
            approxSizeBytes = 3_350_000_000,
            purposes = CHAT_AND_TRANSLATION,
        ),
        LocalModelSeed(
            id = "gemma-4-e4b-it-q4",
            title = "Gemma 4 E4B",
            repoIds = listOf("unsloth/gemma-4-E4B-it-GGUF"),
            paramsLabel = "E4B · Q4",
            note = "Stronger Gemma 4; needs about 8 GB of free RAM.",
            approxSizeBytes = 4_980_000_000,
            purposes = CHAT_AND_TRANSLATION,
        ),
    )
}

/** Every model this app can install: chat models, then the translation-only ones. */
object LocalModelCatalog {
    val ALL: List<LocalModelSeed> = ChatModels.ALL + TranslationModels.ALL

    fun byId(id: String): LocalModelSeed? = ALL.firstOrNull { it.id == id }
}
