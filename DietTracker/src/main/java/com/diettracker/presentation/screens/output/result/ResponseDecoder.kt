package com.diettracker.presentation.screens.output.result

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class Measurement(
    val weight: String? = null,
    val size: String? = null
)

@Serializable
data class NutrientDetails(
    val amount: String,
    val digestedAmount: String
)

@Serializable
data class NutrientDetailsTotal(
    val amount: String,
    val digestedAmount: String,
    val dailyPercentage: String
)

@Serializable
data class FoodItem(
    val food: String,
    val calories: String,
    val digestedCalories: String,
    val macronutrients: Map<String, NutrientDetails>,
    val micronutrients: Map<String, NutrientDetails>,
    val amount: String? = null,
    val measurement: Measurement? = null,
)

@Serializable
data class DietSolutionResponse(
    val titles: Map<String, String>,
    val foods: List<FoodItem>,
    val bmi: String,
    val bmiClassification: String,
    val digestedCalories: String,
    val digestedMacronutrients: Map<String, NutrientDetailsTotal>,
    val digestedMicronutrients: Map<String, NutrientDetailsTotal>,
    val adjustedCalories: String,
    val healthTips: List<String>
)

private val json = Json { ignoreUnknownKeys = true }

fun decodeDietSolutionResponse(jsonResponse: String): Pair<String, String> {
    val cleanedJson = jsonResponse.trim()
        .removeSurrounding("```json", "```")
        .trim()

    val dietSolutionResponse: DietSolutionResponse = json.decodeFromString(cleanedJson)

    return buildString {
        appendLine("\n${dietSolutionResponse.titles["digested_calories"]}:")
        appendLine("- ${dietSolutionResponse.titles["total_digested_calories"]}: ${dietSolutionResponse.digestedCalories}")
        appendLine("- ${dietSolutionResponse.titles["daily_caloric_intake"]}: ${dietSolutionResponse.adjustedCalories}")

        appendLine("\n${dietSolutionResponse.titles["nutrients"]}:")

        appendLine("- ${dietSolutionResponse.titles["total_macronutrients"]}:")
        dietSolutionResponse.digestedMacronutrients.forEach { (name, details) ->
            appendLine("    - $name: ${details.amount} (${dietSolutionResponse.titles["total_digested_amount"]}: ${details.digestedAmount}, ${dietSolutionResponse.titles["daily_percentage"]}: ${details.dailyPercentage})")
        }

        appendLine("- ${dietSolutionResponse.titles["total_micronutrients"]}:")
        dietSolutionResponse.digestedMicronutrients.forEach { (name, details) ->
            appendLine("    - $name: ${details.amount} (${dietSolutionResponse.titles["total_digested_amount"]}: ${details.digestedAmount}, ${dietSolutionResponse.titles["daily_percentage"]}: ${details.dailyPercentage})")
        }

        appendLine("\n${dietSolutionResponse.titles["detected_foods"]}:")
        dietSolutionResponse.foods.forEach { food ->
            appendLine("• ${food.food}")
            appendLine("    - ${dietSolutionResponse.titles["calories"]}: ${food.calories}")
            appendLine("    - ${dietSolutionResponse.titles["digested_calories"]}: ${food.digestedCalories}")

            if (food.macronutrients.isNotEmpty()) {
                appendLine("    - ${dietSolutionResponse.titles["macronutrients"]}:")
                food.macronutrients.forEach { (name, details) ->
                    appendLine("        - $name: ${details.amount} (${dietSolutionResponse.titles["digested_amount"]}: ${details.digestedAmount})")
                }
            }

            if (food.micronutrients.isNotEmpty()) {
                appendLine("    - ${dietSolutionResponse.titles["micronutrients"]}:")
                food.micronutrients.forEach { (name, details) ->
                    appendLine("        - $name: ${details.amount} (${dietSolutionResponse.titles["digested_amount"]}: ${details.digestedAmount})")
                }
            }
        }

        appendLine("\n${dietSolutionResponse.titles["bmi_info"]}:")
        appendLine("- ${dietSolutionResponse.titles["bmi"]}: ${dietSolutionResponse.bmi}")
        appendLine("- ${dietSolutionResponse.titles["bmi_classification"]}: ${dietSolutionResponse.bmiClassification}")

        if (dietSolutionResponse.healthTips.isNotEmpty()) {
            appendLine("\n${dietSolutionResponse.titles["health_tips"]}:")
            dietSolutionResponse.healthTips.forEach { tip ->
                appendLine("• $tip")
            }
        }
    } to buildString {
        dietSolutionResponse.foods.forEach { food ->
            // if we are in the case where image is used and there is an information extracted out of it
            // (which means amount is not null), generate the following string
            if (food.amount != null) {
                appendLine("${dietSolutionResponse.titles["food"]}: ${food.food}")
                appendLine("${dietSolutionResponse.titles["amount"]}: ${food.amount}")

                food.measurement?.weight?.let { weight ->
                    appendLine("${dietSolutionResponse.titles["measurement"]}: ${dietSolutionResponse.titles["weight"]} - $weight")
                }
                food.measurement?.size?.let { size ->
                    appendLine("${dietSolutionResponse.titles["measurement"]}: ${dietSolutionResponse.titles["size"]} - $size")
                }

                appendLine("")
            }
        }
    }
}