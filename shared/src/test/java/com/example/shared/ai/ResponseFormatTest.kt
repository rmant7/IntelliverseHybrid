package com.example.shared.ai

import com.example.shared.data.network.gemini_api.client.GeminiRequest
import com.example.shared.domain.ai.EnforcementLadder
import com.example.shared.domain.ai.FormatSupportErrors
import com.example.shared.domain.ai.JsonEnforcement
import com.example.shared.domain.ai.JsonSchemas
import com.example.shared.domain.ai.ResponseFormat
import com.example.shared.domain.usecases.ai.GroqRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseFormatTest {

    @Serializable
    data class Stop(val name: String, val tips: List<String> = emptyList())

    @Serializable
    enum class Kind { CITY, NATURE }

    @Serializable
    data class Answer(
        val titles: Map<String, String>,
        val stops: List<Stop>,
        val count: Int,
        val ratio: Double,
        val flag: Boolean,
        val kind: Kind,
        val note: String? = null,
        val extras: List<String> = emptyList(),
    )

    private val format = ResponseFormat.Json.of("answer", Answer.serializer())
    private val json = Json

    private fun JsonObject.obj(key: String) = getValue(key).jsonObject
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.names(key: String) = (get(key) as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()

    // --- schema from the serializer ---

    @Test
    fun schema_requires_exactly_the_fields_without_defaults() {
        val schema = format.schema
        assertEquals("object", schema.str("type"))
        assertEquals(setOf("titles", "stops", "count", "ratio", "flag", "kind"), schema.names("required"))
        val stop = schema.obj("properties").obj("stops").obj("items")
        assertEquals(setOf("name"), stop.names("required"))
    }

    @Test
    fun schema_maps_kotlin_types_to_json_schema_types() {
        val p = format.schema.obj("properties")
        assertEquals("array", p.obj("stops").str("type"))
        assertEquals("integer", p.obj("count").str("type"))
        assertEquals("number", p.obj("ratio").str("type"))
        assertEquals("boolean", p.obj("flag").str("type"))
        assertEquals(setOf("CITY", "NATURE"), p.obj("kind").getValue("enum").jsonArray.map { it.jsonPrimitive.content }.toSet())
        assertEquals("string", p.obj("note").str("type"))
        // Map<String, String>: an object with free keys, values still typed (decision (a)).
        val titles = p.obj("titles")
        assertEquals("object", titles.str("type"))
        assertEquals("string", titles.obj("additionalProperties").str("type"))
    }

    @Serializable
    data class Node(val next: Node? = null)

    @Test(expected = IllegalArgumentException::class)
    fun recursive_types_are_rejected() {
        JsonSchemas.of(Node.serializer().descriptor)
    }

    // --- Gemini request ---

    private val awkwardPrompt = "Say \"hi\" \\ then\nnew line\t{\"json\": true}"

    @Test
    fun gemini_text_request_carries_the_prompt_verbatim_and_no_generation_config() {
        val body = json.parseToJsonElement(GeminiRequest.buildGeminiRequest(awkwardPrompt, "sys", listOf("https://x/f\"1"))).jsonObject
        val parts = body.getValue("contents").jsonArray[0].jsonObject.getValue("parts").jsonArray
        assertEquals(awkwardPrompt, parts[0].jsonObject.str("text"))
        assertEquals("https://x/f\"1", parts[1].jsonObject.obj("file_data").str("file_uri"))
        assertEquals("sys", body.obj("system_instruction").getValue("parts").jsonArray[0].jsonObject.str("text"))
        assertNull(body["generationConfig"])
    }

    @Test
    fun gemini_json_request_asks_for_json_and_the_schema_then_less_when_told() {
        fun config(e: JsonEnforcement) = json.parseToJsonElement(
            GeminiRequest.buildGeminiRequest(awkwardPrompt, "", responseFormat = format, enforcement = e),
        ).jsonObject["generationConfig"]?.jsonObject

        val full = config(JsonEnforcement.SCHEMA)!!
        assertEquals("application/json", full.str("responseMimeType"))
        assertEquals(format.schema, full.obj("responseJsonSchema"))
        val mimeOnly = config(JsonEnforcement.JSON_ONLY)!!
        assertEquals("application/json", mimeOnly.str("responseMimeType"))
        assertNull(mimeOnly["responseJsonSchema"])
        assertNull(config(JsonEnforcement.PROMPT_ONLY))
    }

    // --- Groq request ---

    @Test
    fun groq_request_carries_each_enforcement_level() {
        fun rf(e: JsonEnforcement, f: ResponseFormat = format) =
            json.parseToJsonElement(GroqRequest.body("m", "", awkwardPrompt, emptyList(), f, e)).jsonObject["response_format"]?.jsonObject

        val schema = rf(JsonEnforcement.SCHEMA)!!
        assertEquals("json_schema", schema.str("type"))
        assertEquals("answer", schema.obj("json_schema").str("name"))
        assertEquals(format.schema, schema.obj("json_schema").obj("schema"))
        assertEquals("json_object", rf(JsonEnforcement.JSON_ONLY)!!.str("type"))
        assertNull(rf(JsonEnforcement.PROMPT_ONLY))
        assertNull("a text request never gets response_format", rf(JsonEnforcement.SCHEMA, ResponseFormat.Text))
    }

    @Test
    fun groq_text_request_is_unchanged_in_shape() {
        val body = json.parseToJsonElement(GroqRequest.body("model-x", "sys", awkwardPrompt, listOf("AAAA"))).jsonObject
        assertEquals("model-x", body.str("model"))
        assertEquals(GroqRequest.MAX_TOKENS.toString(), body.str("max_tokens"))
        val messages = body.getValue("messages").jsonArray
        assertEquals("system", messages[0].jsonObject.str("role"))
        val content = messages[1].jsonObject.getValue("content").jsonArray
        assertEquals("data:image/jpeg;base64,AAAA", content[0].jsonObject.obj("image_url").str("url"))
        assertEquals(awkwardPrompt, content[1].jsonObject.str("text"))
        assertNull(body["response_format"])
    }

    // --- "format not supported" is recognised narrowly ---

    private fun error(message: String, code: String? = null) =
        """{"error":{"message":${json.encodeToString(kotlinx.serialization.serializer<String>(), message)}${code?.let { ",\"code\":\"$it\"" } ?: ""}}}"""

    @Test
    fun only_an_explicit_unsupported_format_counts_for_gemini() {
        assertTrue(FormatSupportErrors.isGeminiFormatUnsupported(400, error("Invalid JSON payload received. Unknown name \"responseJsonSchema\" at 'generation_config': Cannot find field.")))
        assertTrue(FormatSupportErrors.isGeminiFormatUnsupported(400, error("JSON mode is not enabled for models/x")))
        assertFalse("a schema defect is not 'unsupported'", FormatSupportErrors.isGeminiFormatUnsupported(400, error("Invalid value at 'generation_config.response_json_schema.properties' (type)")))
        assertFalse(FormatSupportErrors.isGeminiFormatUnsupported(400, error("API key not valid")))
        assertFalse("unsupported, but not the format", FormatSupportErrors.isGeminiFormatUnsupported(400, error("Image input is not supported for this model")))
        assertFalse(FormatSupportErrors.isGeminiFormatUnsupported(429, error("responseJsonSchema is not supported")))
        assertFalse(FormatSupportErrors.isGeminiFormatUnsupported(400, "not json"))
    }

    @Test
    fun only_an_explicit_unsupported_format_counts_for_groq() {
        assertTrue(FormatSupportErrors.isGroqFormatUnsupported(400, error("This model does not support response format `json_schema`.")))
        assertTrue(FormatSupportErrors.isGroqFormatUnsupported(400, error("response_format json_object is not supported with this model")))
        assertFalse("output that failed validation is a real failure", FormatSupportErrors.isGroqFormatUnsupported(400, error("Failed to validate JSON: json_schema not supported field missing", code = "json_validate_failed")))
        assertFalse(FormatSupportErrors.isGroqFormatUnsupported(400, error("messages must contain the word json")))
        assertFalse("unsupported, but not the format", FormatSupportErrors.isGroqFormatUnsupported(400, error("image input is not supported by this model")))
        assertFalse(FormatSupportErrors.isGroqFormatUnsupported(404, error("json_schema not supported")))
    }

    // --- the ladder ---

    private val unsupported = IllegalStateException("unsupported")
    private val otherFailure = IllegalStateException("bad request")

    @Test
    fun the_ladder_steps_down_only_on_unsupported_and_remembers_it_per_model() {
        val ladder = EnforcementLadder()
        val tried = mutableListOf<JsonEnforcement>()
        val result = ladder.run("m1", format, { it === unsupported }) { level ->
            tried += level
            if (level == JsonEnforcement.SCHEMA) Result.failure(unsupported) else Result.success("ok")
        }
        assertEquals("ok", result.getOrNull())
        assertEquals(listOf(JsonEnforcement.SCHEMA, JsonEnforcement.JSON_ONLY), tried)
        assertEquals(JsonEnforcement.JSON_ONLY, ladder.start("m1", format))
        assertEquals("another model is unaffected", JsonEnforcement.SCHEMA, ladder.start("m2", format))
    }

    @Test
    fun any_other_failure_is_returned_without_a_weaker_retry() {
        val ladder = EnforcementLadder()
        val tried = mutableListOf<JsonEnforcement>()
        val result = ladder.run("m", format, { it === unsupported }) { level -> tried += level; Result.failure<String>(otherFailure) }
        assertTrue(result.exceptionOrNull() === otherFailure)
        assertEquals(listOf(JsonEnforcement.SCHEMA), tried)
        assertEquals(JsonEnforcement.SCHEMA, ladder.start("m", format))
    }

    @Test
    fun the_ladder_ends_at_prompt_only_and_text_never_enforces() {
        val ladder = EnforcementLadder()
        val tried = mutableListOf<JsonEnforcement>()
        ladder.run("m", format, { true }) { level -> tried += level; Result.failure<String>(unsupported) }
        assertEquals(JsonEnforcement.entries.toList(), tried)
        val textTried = mutableListOf<JsonEnforcement>()
        ladder.run("t", ResponseFormat.Text, { true }) { level -> textTried += level; Result.failure<String>(unsupported) }
        assertEquals(listOf(JsonEnforcement.PROMPT_ONLY), textTried)
    }
}
