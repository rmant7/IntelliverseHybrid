package com.example.shared.data.network.gemini_api.client

import kotlinx.serialization.Serializable

@kotlinx.serialization.Serializable
data class GeminiJsonResponse(
    val candidates: List<Candidate?>? = null,
    val promptFeedback: PromptFeedback? = null,
)

/** Present (with [PromptFeedback.blockReason]) when the PROMPT itself, not any candidate, got blocked -- [candidates] is then absent entirely. */
@kotlinx.serialization.Serializable
data class PromptFeedback(
    val blockReason: String? = null,
)

/**
 * Default value of content was assigned to null
 * to prevent missing field error
 */
@kotlinx.serialization.Serializable
data class Candidate(
    val content: Content? = null,
    // Why THIS candidate has no usable text when content/parts end up
    // empty -- "SAFETY" (safety filter), "MAX_TOKENS" (ran out of budget,
    // a real risk for a thinking model that spends part of it on
    // reasoning before ever writing the answer), "RECITATION", etc.
    // Silently returning "no answer" here used to look identical whether
    // the model said nothing or explicitly refused/ran out of room.
    val finishReason: String? = null,
)

@kotlinx.serialization.Serializable
data class Content(
    val parts: List<Part>? = null
)

@Serializable
data class Part(
    val text: String? = null
)