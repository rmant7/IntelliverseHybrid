package com.oneclicktrip.presentation.screens.output.result

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

/** The TripSolutionResponse contract: what Gemini/Groq are asked for, and what the decoder accepts. */
class TripResponseContractTest {

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

    private fun decode(json: String) = decodeTripSolutionResponse(json)
    private val summary = """"summary":{"totalCost":"1","visitedDestinations":"2","transitTime":"3","transportTypes":"4","lodgingTypes":"5","categories":"6"}"""
    private val activity = """{"name":"Colosseum","description":"d","time":"10:00"}"""

    @Test
    fun a_day_without_activities_or_accommodations_is_a_valid_day() {
        val text = decode("""{"titles":{},"days":[{"activities":[$activity],"accommodations":[]},{}],$summary}""").first
        assertTrue(text, "Colosseum" in text)
    }

    @Test
    fun genuinely_required_data_still_fails() {
        assertFails("no summary", """{"titles":{},"days":[]}""")
        assertFails("activity without a name", """{"titles":{},"days":[{"activities":[{"description":"d","time":"t"}]}],$summary}""")
    }

    @Test
    fun wrong_nesting_and_malformed_json_still_fail() {
        // Seen on a real device (Gemini): key/value pairs straight inside the midwayStops array.
        assertFails("pairs inside an array", """{"titles":{},"days":[{"activities":[{"name":"a","description":"d","time":"t","midwayStops":["name": "x"]}]}],$summary}""")
        assertFails("a string where a stop object belongs", """{"titles":{},"days":[{"activities":[{"name":"a","description":"d","time":"t","midwayStops":["x"]}]}],$summary}""")
        assertFails("truncated", """{"titles":{},"days":[""")
    }

    @Test
    fun the_contract_schema_matches_what_the_decoder_enforces() {
        val schema = (ResponseFormat.Json.of("trip_solution", TripSolutionResponse.serializer())).schema
        assertEquals(setOf("titles", "days", "summary"), required(schema))
        assertEquals(emptySet<String>(), required(schema.prop("days", "[]")))
        assertEquals(setOf("name", "description", "time"), required(schema.prop("days", "[]", "activities", "[]")))
        assertEquals(setOf("name", "description", "time"), required(schema.prop("days", "[]", "activities", "[]", "midwayStops", "[]")))
    }
}
