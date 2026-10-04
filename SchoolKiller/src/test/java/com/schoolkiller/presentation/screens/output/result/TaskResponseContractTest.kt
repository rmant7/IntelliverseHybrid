package com.schoolkiller.presentation.screens.output.result

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

/** The TaskSolutionResponse contract: what Gemini/Groq are asked for, and what the decoder accepts. */
class TaskResponseContractTest {

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

    private fun decode(json: String) = decodeTaskSolutionResponse(json)

    @Test
    fun no_qr_or_barcode_content_is_a_valid_answer() {
        val text = decode("""{"titles":{},"solutions":["x = 5"]}""").first
        assertTrue(text, "x = 5" in text)
    }

    @Test
    fun the_solutions_themselves_stay_required() {
        assertFails("no solutions", """{"titles":{},"qrContents":[],"barcodeContents":[]}""")
        assertFails("solutions of the wrong type", """{"titles":{},"solutions":[{"step":1}]}""")
        assertFails("malformed", """{"titles":{},"solutions":["x"""")
    }

    @Test
    fun the_contract_schema_matches_what_the_decoder_enforces() {
        val schema = ResponseFormat.Json.of("task_solution", TaskSolutionResponse.serializer()).schema
        assertEquals(setOf("titles", "solutions"), required(schema))
    }
}
