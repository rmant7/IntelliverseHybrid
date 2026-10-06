package com.intelliverse.localai

import ai.localstudio.sdk.LocalCapability
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * What to do when no on-device model can take a request now: which catalog
 * models would fit this phone's free memory (biggest first), or -- when even
 * the smallest would not -- how much memory to free for it.
 */
data class LocalModelAdvice(
    val capability: LocalCapability,
    /** Why nothing installed can take it: the router's own words, figures included. */
    val why: String,
    /** Models that would fit now, biggest first. */
    val fitting: List<ModelOption>,
    /** When nothing fits: the smallest suitable model, and [freeBytes] how much more memory it needs. */
    val smallest: ModelOption?,
    val freeBytes: Long?,
    val availableBytes: Long,
)

/** One catalog model the person can get: download it, or (vision) add its vision part to the one they have. */
data class ModelOption(
    val modelId: String,
    val title: String,
    /** RAM it would need here: measured when its weights are in, else estimated. */
    val needBytes: Long,
    /** The weights are on the phone; only the vision part is missing. */
    val needsVisionPart: Boolean,
)

/**
 * The way back to the Models screen from inside a mini-app: a request to
 * open it on one model; the app's navigation follows it (see Navigation()).
 */
object LocalModelNavigation {
    private val _requests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val requests: SharedFlow<String> = _requests

    fun openModel(capability: LocalCapability, modelId: String) {
        val purpose = when (capability) {
            LocalCapability.VISION -> "VISION"
            LocalCapability.TRANSLATION -> "TRANSLATION"
            LocalCapability.TEXT -> "CHAT"
        }
        _requests.tryEmit("models?purpose=$purpose&model=$modelId")
    }
}
