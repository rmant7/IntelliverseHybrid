package com.intelliverse.models

/**
 * One entry in the local on-device translation-model catalog. Modeled after
 * rmant7/AI's own LocalModelSeed (app/src/main/java/ai/localstudio/app/models/LocalModels.kt)
 * but stripped of the capability-routing fields that only make sense with
 * that app's CapabilityRouter/ModelSelector -- this app has no such router,
 * a model here is picked directly by [id].
 */
data class LocalModelSeed(
    val id: String,
    val title: String,
    val repoIds: List<String>,
    val paramsLabel: String,
    val note: String = "",
    val approxSizeBytes: Long,
    val contextTokens: Int = 4096,
    val quantPriority: List<String>? = null,
    /** True for T5 encoder-decoder models (MADLAD-400) -- these need a
     * different native generation call than an ordinary decoder-only chat
     * model once local inference exists; recorded now so the catalog entry
     * already carries this fact. */
    val isT5EncoderDecoder: Boolean = false,
)
