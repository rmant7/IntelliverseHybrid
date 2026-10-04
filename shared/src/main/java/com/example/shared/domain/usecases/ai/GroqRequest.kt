package com.example.shared.domain.usecases.ai

import com.example.shared.domain.ai.JsonEnforcement
import com.example.shared.domain.ai.ResponseFormat
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Groq's `/chat/completions` request body (OpenAI-compatible) -- images (if
 * any) as inline `data:` URIs, no external hosting required. A
 * [ResponseFormat.Json] adds `response_format` at the given
 * [JsonEnforcement]: `json_schema` (non-strict: strict mode would reject
 * the free-key maps these response models contain), `json_object`, or
 * nothing.
 */
object GroqRequest {

    // Without this, Groq applies its own per-model server-side default --
    // confirmed too small via a real device log: a genuine, successful
    // response (a multi-day trip itinerary, for OneClickTrip) got cut off
    // mid-JSON, failing to decode with "Expected end of the object '}', but
    // had 'EOF' instead". 8192 comfortably covers this app's largest
    // structured response shape with room to spare.
    const val MAX_TOKENS = 8192

    fun body(
        modelName: String,
        systemInstruction: String,
        prompt: String,
        imagesBase64: List<String>,
        responseFormat: ResponseFormat = ResponseFormat.Text,
        enforcement: JsonEnforcement = JsonEnforcement.PROMPT_ONLY,
    ): String = buildJsonObject {
        put("model", modelName)
        put("max_tokens", MAX_TOKENS)
        putJsonArray("messages") {
            if (systemInstruction.isNotBlank()) {
                addJsonObject {
                    put("role", "system")
                    put("content", systemInstruction)
                }
            }
            addJsonObject {
                put("role", "user")
                if (imagesBase64.isEmpty()) {
                    put("content", prompt)
                } else {
                    put(
                        "content",
                        buildJsonArray {
                            imagesBase64.forEach { base64Data ->
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$base64Data") }
                                }
                            }
                            addJsonObject {
                                put("type", "text")
                                put("text", prompt)
                            }
                        },
                    )
                }
            }
        }
        if (responseFormat is ResponseFormat.Json) {
            when (enforcement) {
                JsonEnforcement.SCHEMA -> putJsonObject("response_format") {
                    put("type", "json_schema")
                    putJsonObject("json_schema") {
                        put("name", responseFormat.name)
                        put("schema", responseFormat.schema)
                    }
                }
                JsonEnforcement.JSON_ONLY -> putJsonObject("response_format") { put("type", JsonPrimitive("json_object")) }
                JsonEnforcement.PROMPT_ONLY -> Unit
            }
        }
    }.toString()
}
