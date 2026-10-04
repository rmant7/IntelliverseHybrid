package com.example.shared.domain.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * How strictly a provider is asked for structured output, strongest first.
 * A [ResponseFormat.Json] request starts at [SCHEMA]; a [ResponseFormat.Text]
 * one is always [PROMPT_ONLY].
 */
enum class JsonEnforcement {
    /** Native JSON plus the schema (Gemini `responseJsonSchema`, Groq `json_schema`). */
    SCHEMA,

    /** Native JSON syntax only (Gemini `responseMimeType`, Groq `json_object`). */
    JSON_ONLY,

    /** No provider-side constraint -- the prompt alone asks for JSON (the behaviour before structured output). */
    PROMPT_ONLY,
}

/**
 * Steps a request down to a weaker [JsonEnforcement] ONLY when the provider
 * said, explicitly, that this model/endpoint does not support the format
 * just asked for -- never on any other failure. A real defect in a schema
 * or a request (any other 400) must surface as the provider's failure, not
 * be quietly retried with a weaker contract.
 *
 * Remembers a step-down per model for the process lifetime, so a model that
 * rejected `json_schema` once isn't asked again on every request.
 */
class EnforcementLadder {
    private val ceiling = ConcurrentHashMap<String, JsonEnforcement>()

    fun start(model: String, format: ResponseFormat): JsonEnforcement = when (format) {
        ResponseFormat.Text -> JsonEnforcement.PROMPT_ONLY
        is ResponseFormat.Json -> ceiling[model] ?: JsonEnforcement.SCHEMA
    }

    /** The next weaker level after [current] was explicitly rejected for [model]; null when there is none. */
    fun stepDown(model: String, current: JsonEnforcement): JsonEnforcement? {
        val next = when (current) {
            JsonEnforcement.SCHEMA -> JsonEnforcement.JSON_ONLY
            JsonEnforcement.JSON_ONLY -> JsonEnforcement.PROMPT_ONLY
            JsonEnforcement.PROMPT_ONLY -> return null
        }
        ceiling[model] = next
        return next
    }

    /**
     * Runs [attempt] at the strongest level allowed for [model], stepping
     * down only while [isFormatUnsupported] says the failure was exactly that.
     */
    inline fun <T> run(
        model: String,
        format: ResponseFormat,
        isFormatUnsupported: (Throwable) -> Boolean,
        attempt: (JsonEnforcement) -> Result<T>,
    ): Result<T> {
        var level = start(model, format)
        while (true) {
            val result = attempt(level)
            val failure = result.exceptionOrNull() ?: return result
            if (!isFormatUnsupported(failure)) return result
            level = stepDown(model, level) ?: return result
        }
    }
}

/**
 * Reads a provider's error body for an explicit "this response format is not
 * supported here" -- deliberately narrow: the error must name the format
 * mechanism AND say it is unsupported/unknown. Anything else (a malformed
 * schema, output that failed validation, quota, auth...) is not this.
 */
object FormatSupportErrors {

    private val lenient = Json { ignoreUnknownKeys = true }

    private val GEMINI_FORMAT_FIELDS = listOf(
        "responsejsonschema", "response_json_schema", "responseschema", "response_schema",
        "responsemimetype", "response_mime_type", "json mode",
    )
    private val GROQ_FORMAT_TERMS = listOf("response_format", "response format", "json_schema", "json_object")
    private val UNSUPPORTED_TERMS = listOf(
        "not supported", "unsupported", "does not support", "doesn't support", "not available for",
        "unknown name", "cannot find field", "not enabled",
    )

    /** Gemini: HTTP 400 whose `error.message` names a JSON-output field and says it isn't supported/known. */
    fun isGeminiFormatUnsupported(status: Int, body: String): Boolean =
        status == 400 && mentionsUnsupported(errorMessage(body), GEMINI_FORMAT_FIELDS)

    /**
     * Groq: HTTP 400 whose `error.message` names the response format and says
     * it isn't supported. `json_validate_failed` -- the model's output did not
     * match the schema -- is a real failure of this answer, never a step-down.
     */
    fun isGroqFormatUnsupported(status: Int, body: String): Boolean {
        if (status != 400) return false
        if (errorField(body, "code") == "json_validate_failed") return false
        return mentionsUnsupported(errorMessage(body), GROQ_FORMAT_TERMS)
    }

    private fun mentionsUnsupported(message: String?, formatTerms: List<String>): Boolean {
        val text = message?.lowercase() ?: return false
        return formatTerms.any { it in text } && UNSUPPORTED_TERMS.any { it in text }
    }

    private fun errorMessage(body: String): String? = errorField(body, "message")

    private fun errorField(body: String, field: String): String? = runCatching {
        val error = lenient.parseToJsonElement(body).jsonObject["error"] as? JsonObject
        error?.get(field)?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
