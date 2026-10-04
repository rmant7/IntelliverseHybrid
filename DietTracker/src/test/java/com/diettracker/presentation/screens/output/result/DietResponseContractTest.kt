package com.diettracker.presentation.screens.output.result

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

/** The DietSolutionResponse contract: what Gemini/Groq are asked for, and what the decoder accepts. */
class DietResponseContractTest {

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

    private fun decode(json: String) = decodeDietSolutionResponse(json)
    private val nutrient = """{"amount":"10 g","digestedAmount":"9 g"}"""
    private val total = """{"amount":"10 g","digestedAmount":"9 g","dailyPercentage":"5%"}"""
    private val food = """{"food":"Apple","calories":"52","digestedCalories":"50","macronutrients":{"Protein":$nutrient},"micronutrients":{}}"""
    private fun answer(extra: String = "", foods: String = "[$food]") =
        """{"titles":{},"foods":$foods,"bmi":"22","bmiClassification":"normal","digestedCalories":"50","digestedMacronutrients":{"Protein":$total},"digestedMicronutrients":{},"adjustedCalories":"2000"$extra}"""

    @Test
    fun no_health_tips_is_a_complete_answer() {
        val text = decode(answer()).first
        assertTrue(text, "Apple" in text)
    }

    @Test
    fun the_nutrition_figures_stay_required() {
        assertFails("no bmi", answer().replace(""""bmi":"22",""", ""))
        assertFails("a nutrient that is not an object", answer(foods = """[{"food":"Apple","calories":"52","digestedCalories":"50","macronutrients":{"Protein":"10 g"},"micronutrients":{}}]"""))
        assertFails("malformed", answer().dropLast(2))
    }

    @Test
    fun the_contract_schema_matches_what_the_decoder_enforces() {
        val schema = ResponseFormat.Json.of("diet_solution", DietSolutionResponse.serializer()).schema
        assertEquals(
            setOf("titles", "foods", "bmi", "bmiClassification", "digestedCalories", "digestedMacronutrients", "digestedMicronutrients", "adjustedCalories"),
            required(schema),
        )
        // Free-key nutrient maps: object with typed values (decision (a)).
        val macro = schema.prop("foods", "[]", "macronutrients")
        assertEquals("object", macro.getValue("type").jsonPrimitive.content)
        assertEquals(setOf("amount", "digestedAmount"), required(macro.getValue("additionalProperties").jsonObject))
    }
}
