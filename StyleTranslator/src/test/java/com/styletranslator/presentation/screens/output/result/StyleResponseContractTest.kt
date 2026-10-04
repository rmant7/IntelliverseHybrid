package com.styletranslator.presentation.screens.output.result

import com.example.shared.domain.ai.ResponseFormat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The StyleSolutionResponse contract: what Gemini/Groq are asked for, and what the decoder accepts. */
class StyleResponseContractTest {

    private fun required(schema: JsonObject): Set<String> =
        (schema["required"] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()

    private fun JsonObject.prop(vararg path: String): JsonObject =
        path.fold(this) { node, key -> if (key == "[]") node.getValue("items").jsonObject else node.getValue("properties").jsonObject.getValue(key).jsonObject }

    private fun assertFails(why: String, json: String) {
        try {
            decode(json)
            fail("$why: expected the decoder to reject it")
        } catch (e: SerializationException) {
            // the provider result fails; other providers carry on
        } catch (e: IllegalArgumentException) {
            // kotlinx reports some malformed input this way
        }
    }

    private fun decode(json: String) = decodeStyleSolutionResponse(json)

    @Test
    fun a_translation_decodes() {
        assertTrue("Hola" in decode("""{"titles":{},"translatedText":"Hola"}""").first)
    }

    @Test
    fun the_translation_stays_required() {
        assertFails("no translatedText", """{"titles":{}}""")
        assertFails("translatedText of the wrong type", """{"titles":{},"translatedText":["Hola"]}""")
    }

    @Test
    fun the_contract_schema_matches_what_the_decoder_enforces() {
        val schema = ResponseFormat.Json.of("style_solution", StyleSolutionResponse.serializer()).schema
        assertEquals(setOf("titles", "translatedText"), required(schema))
    }
}
