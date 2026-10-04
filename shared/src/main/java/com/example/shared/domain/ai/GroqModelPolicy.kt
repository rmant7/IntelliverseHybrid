package com.example.shared.domain.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Which of a Groq account's models to try for a request, and in what order.
 * Groq's `GET /models` has no modality field, so vision support is known
 * only from [KNOWN_VISION_MODELS], guessed from names ([VISION_HINT]), or
 * learned: a model that rejects an image is remembered as text-only for the
 * process lifetime and not offered images again.
 */
class GroqModelPolicy {

    private val textOnly = ConcurrentHashMap.newKeySet<String>()

    /**
     * Candidates for one request. Non-chat models (speech, TTS, safety
     * classifiers) never: a guard model "answers" a prompt with `safe`.
     * With images: known vision models first, then name-hinted ones, then
     * the rest, minus every model already seen rejecting images. Text only:
     * the same order, nothing dropped but non-chat models.
     */
    fun candidates(discovered: List<String>, hasImages: Boolean): List<String> {
        val chat = discovered.distinct().filterNot { NON_CHAT.containsMatchIn(it) }
        val usable = if (hasImages) chat.filterNot { it in textOnly } else chat
        return usable.sortedBy { model ->
            when {
                model in KNOWN_VISION_MODELS -> 0
                VISION_HINT.containsMatchIn(model) -> 1
                else -> 2
            }
        }
    }

    fun markTextOnly(model: String) {
        textOnly += model
    }

    companion object {
        /** Confirmed to accept images (rmant7/AI's CloudProviders, Groq docs). */
        val KNOWN_VISION_MODELS = listOf(
            "qwen/qwen3.6-27b",
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "meta-llama/llama-4-maverick-17b-128e-instruct",
        )

        // Heuristic ranking only -- "qwen" also matches text-only Qwen models,
        // which is what learning via [markTextOnly] is for.
        val VISION_HINT = Regex("vision|llama-4|scout|maverick|qwen|-vl-|vl$", RegexOption.IGNORE_CASE)

        val NON_CHAT = Regex("whisper|tts|playai|orpheus|guard", RegexOption.IGNORE_CASE)

        private val lenient = Json { ignoreUnknownKeys = true }

        private val IMAGE_TERMS = listOf("image", "vision", "multimodal", "multi-modal")
        private val UNSUPPORTED_TERMS = listOf(
            "not supported", "unsupported", "does not support", "doesn't support", "not available", "not enabled", "only supports text",
        )

        /**
         * HTTP 400 saying this model cannot take image input at all: the
         * OpenAI-compatible "messages[1].content must be a string" (a
         * text-only model given content parts), or an error naming images
         * as unsupported. Not "invalid image" -- a broken picture fails on
         * every model and must not mark a vision model text-only.
         */
        fun isImageInputRejected(status: Int, body: String): Boolean {
            if (status != 400) return false
            val message = errorField(body, "message")?.lowercase() ?: return false
            if (errorField(body, "code") == "json_validate_failed") return false
            if ("content" in message && "must be a string" in message) return true
            return IMAGE_TERMS.any { it in message } && UNSUPPORTED_TERMS.any { it in message }
        }

        private fun errorField(body: String, field: String): String? = runCatching {
            val error = lenient.parseToJsonElement(body).jsonObject["error"] as? JsonObject
            error?.get(field)?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }
}
