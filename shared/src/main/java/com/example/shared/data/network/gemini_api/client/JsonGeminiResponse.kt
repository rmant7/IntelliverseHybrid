package com.example.shared.data.network.gemini_api.client

import kotlinx.serialization.Serializable

@kotlinx.serialization.Serializable
data class GeminiJsonResponse(
    val candidates: List<Candidate?>? = null
)

/**
 * Default value of content was assigned to null
 * to prevent missing field error
 */
@kotlinx.serialization.Serializable
data class Candidate(
    val content: Content? = null
)

@kotlinx.serialization.Serializable
data class Content(
    val parts: List<Part>? = null
)

@Serializable
data class Part(
    val text: String? = null
)