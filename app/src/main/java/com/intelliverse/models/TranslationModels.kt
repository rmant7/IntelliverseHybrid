package com.intelliverse.models

/**
 * Local on-device translation model catalog -- ported from rmant7/AI's own
 * TranslationModels.kt (app/src/main/java/ai/localstudio/app/models/TranslationModels.kt).
 * Only the entries relevant to this app's translation use case are carried
 * over: TranslateGemma 4B, OmniTranslate 1.1, and MADLAD-400 3B (the only
 * MADLAD size the source project considers reasonably solid -- its own
 * comments flag 7B/10B as sharing a known ggml repack-kernel crash on the T5
 * encoder path, unfixed as of the commit this was ported from).
 */
object TranslationModels {
    val ALL: List<LocalModelSeed> = listOf(
        LocalModelSeed(
            id = "translategemma-4b",
            title = "TranslateGemma 4B",
            repoIds = listOf("42ailab/TranslateGemma-4B-GGUF"),
            paramsLabel = "4B · Gemma · decoder-only",
            note = "General-purpose translation, Gemma fine-tune.",
            approxSizeBytes = 2_450_000_000,
        ),
        LocalModelSeed(
            id = "omnitranslate-1-1",
            title = "OmniTranslate 1.1",
            repoIds = listOf("mradermacher/OmniTranslate-1.1-GGUF"),
            paramsLabel = "0.6B · Qwen3 · decoder-only",
            note = "Small, fast translation-only model.",
            approxSizeBytes = 500_000_000,
        ),
        LocalModelSeed(
            id = "madlad400-3b-mt-q4",
            title = "MADLAD-400 3B",
            repoIds = listOf(
                "mtsdurica/madlad400-3b-mt-Q4_K_M-GGUF",
                "jbochi/madlad400-3b-mt",
            ),
            paramsLabel = "3B · Q4 · T5 encoder-decoder",
            note = "Broad language coverage, T5 encoder-decoder architecture.",
            approxSizeBytes = 1_650_000_000,
            isT5EncoderDecoder = true,
        ),
    )

    fun byId(id: String): LocalModelSeed? = ALL.firstOrNull { it.id == id }
}
