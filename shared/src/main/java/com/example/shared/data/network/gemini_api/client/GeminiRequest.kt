package com.example.shared.data.network.gemini_api.client

import com.example.shared.domain.ai.JsonEnforcement
import com.example.shared.domain.ai.ResponseFormat
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The `generateContent` request body, built as a JSON tree and serialized
 * by kotlinx.serialization -- never by string templating. The prompt and
 * system instruction are plain text: whatever quotes, backslashes or
 * newlines they contain are escaped here, exactly once. (Before, the body
 * was a string template and every prompt carried hand-escaped `\"` to
 * survive it; a single unescaped quote in user input broke the request.)
 */
object GeminiRequest {

    fun buildGeminiRequest(
        prompt: String,
        systemInstruction: String,
        fileUris: List<String> = emptyList(),
        responseFormat: ResponseFormat = ResponseFormat.Text,
        enforcement: JsonEnforcement = JsonEnforcement.PROMPT_ONLY,
    ): String = buildJsonObject {
        putJsonObject("system_instruction") {
            putJsonArray("parts") { addJsonObject { put("text", systemInstruction) } }
        }
        putJsonArray("contents") {
            addJsonObject {
                putJsonArray("parts") {
                    addJsonObject { put("text", prompt) }
                    fileUris.forEach { uri ->
                        addJsonObject {
                            putJsonObject("file_data") {
                                put("mime_type", "image/jpeg")
                                put("file_uri", uri)
                            }
                        }
                    }
                }
            }
        }
        if (responseFormat is ResponseFormat.Json && enforcement != JsonEnforcement.PROMPT_ONLY) {
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                if (enforcement == JsonEnforcement.SCHEMA) put("responseJsonSchema", responseFormat.schema)
            }
        }
    }.toString()
}
