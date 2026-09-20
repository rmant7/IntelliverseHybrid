package com.diettracker.presentation.screens.output.result

import androidx.lifecycle.SavedStateHandle
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.domain.usecases.ai.client.GeminiUseCaseClient
import com.example.shared.domain.usecases.ai.GigaChatUseCase
import com.example.shared.domain.usecases.ai.GrokUseCase
import com.example.shared.domain.usecases.ai.OpenAiUseCase
import com.example.shared.domain.usecases.AudioPlayer
import com.example.shared.domain.usecases.ImageUtils
import com.example.shared.ads.InterstitialAdUseCase
import com.example.shared.domain.prompt.options.PhysicalActivityOption
import com.example.shared.presentation.screens.output.result.BaseResultViewModel
import com.example.shared.presentation.screens.output.result.doubleQuotes
import com.example.shared.presentation.screens.output.result.jsonResponseLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
class ResultViewModel @Inject constructor(
    imageUtils: ImageUtils,
    geminiUseCaseClient: GeminiUseCaseClient,
    openAiUseCase: OpenAiUseCase,
    grokUseCase: GrokUseCase,
    gigaChatUseCase: GigaChatUseCase,
    interstitialAdUseCase: InterstitialAdUseCase,
    speechConverter: SpeechConverter,
    audioPlayer: AudioPlayer,
    savedStateHandle: SavedStateHandle
) : BaseResultViewModel(imageUtils, geminiUseCaseClient, openAiUseCase, grokUseCase, gigaChatUseCase, interstitialAdUseCase, speechConverter, audioPlayer, savedStateHandle) {

    override val audioPrefixName: String
        get() = "diettracker"
    private var physicalActivity: String? = null
    private var gender: String? = null
    private var age: Int? = null
    private var height: Int? = null
    private var weight: Int? = null

    init {

        savedStateHandle.get<String>("physicalActivity")?.let { physicalActivity = it }
        savedStateHandle.get<String>("gender")?.let { gender = it }
        savedStateHandle.get<String>("age")?.toIntOrNull()?.let { age = it }
        savedStateHandle.get<String>("height")?.toIntOrNull()?.let { height = it }
        savedStateHandle.get<String>("weight")?.toIntOrNull()?.let { weight = it }

        fun buildSolvingPrompt(): String {
            val description = if (imageUsed) {
                "foods are provided in the attached images. ${addProperties(userTask, additional = true)}"
            } else if (passedEditedResult.isNotBlank()) {
                "$passedEditedResult\n\n${addProperties(userTask, additional = true)}"
            } else {
                addProperties(userTask, additional = false)
            }

            return """
            
    - Provide detailed nutritional estimations. The response should include detected foods, calorie breakdown, macronutrients, micronutrients, digestion efficiency, BMI-based analysis, and dietary recommendations.
      All nutritional values (calories, macronutrients, micronutrients) must be **scaled according to the total detected food quantity** (weight/size).
      For example, if 1.2 kg of Kiwi is detected, the response should calculate calories, protein, and micronutrients for the **entire 1.2 kg**, not per unit, such as 100g.
      The total values (e.g., \"digestedCalories\", \"digestedMacronutrients\", \"digestedMicronutrients\") **should not always be a direct sum of individual food values**.
      Consider synergistic effects (e.g., vitamin C improves iron absorption) and nutrient competition (e.g., calcium can inhibit iron absorption).
    ${if(imageUsed) {"""- Estimate the amount of the detected foods.
    - For each food, provide **either**:
      - The weight (if the food is best measured by weight, e.g., meat, cheese, flour).
      - The size (if the food is best measured by volume or dimensions, e.g., a milk carton, a loaf of bread).
      - **Do not include both weight and size—return only the most relevant measurement for each food.**"""} else ""}
      The digestion efficiency for each nutrient should take into account the combination of foods detected, adjusting absorption rates where necessary.
    - The response must **translate** food names (inside \"foods\"), macronutrient and micronutrient names (inside \"macronutrients\", \"micronutrients\", \"digestedMacronutrients\", \"digestedMicronutrients\"), and section titles (inside \"titles\") into ${selectedLanguage.languageName}.
    - The response **must include a \"titles\" section** containing localized section headers. **Use the exact keys from the example JSON below for \"titles\"**.

    Strictly format the response as a JSON object with the following structure:
    - \"titles\" (Map<String, String>) – A map of **localized section headers** (must match the keys in the example JSON).
    - \"foods\" (List<Map<String, Any>>) – A list of detected foods, where each food item includes:
       - \"food\" (String) – The translated name of the detected food.
       - \"calories\" (String) – The estimated calorie count.
       - \"digestedCalories\" (String) – The actual calories digested for this food, considering digestion efficiency and fiber content.
       - \"macronutrients\" (Map<String, Map<String, String>>) – The macronutrient content (\"carbohydrates\", \"protein\", and \"fat\", translated) for each food, including its original and digested amounts.
          - \"amount\" (String) – The total macronutrient amount in this food (e.g., \"92 mg\").
          - \"digestedAmount\" (String) – The estimated absorbed amount from this food (e.g., \"80 mg\").
       - \"micronutrients\" (Map<String, Map<String, String>>) – The micronutrient content (translated) for each food, including its original and digested amounts.
          - \"amount\" (String) – The total micronutrient amount in this food (e.g., \"92 mg\").
          - \"digestedAmount\" (String) – The estimated absorbed amount from this food (e.g., \"80 mg\").
       ${if (imageUsed) {"""- \"amount\" (String) – the estimated quantity of the food detected (e.g., \"3 apples\", \"2 slices\", \"1 loaf\").
       - \"measurement\" (Map<String, String>) – either:
         - \"weight\" (String) – the estimated weight of the food (e.g., \"200g\", \"1.5kg\"), **if applicable**.
         - **or**
         - \"size\" (String) – the estimated size of the food (e.g., \"500ml carton\", \"15cm loaf\"), **if applicable**.
       - **Only one of \"weight\" or \"size\" should be present in the \"measurement\" map.** If weight is relevant, size should be omitted, and vice versa."""} else ""}
    - \"bmi\" (String) – The Body Mass Index (BMI), calculated as weight (kg) / height (m)^2.
    - \"bmiClassification\" (String) – The BMI category (e.g., \"Underweight\", \"Normal weight\", \"Overweight\", \"Obese\").
    - \"digestedCalories\" (String) – The total actual calories digested, considering digestion efficiency, fiber content, and food interactions.
    - \"digestedMacronutrients\" (Map<String, Map<String, String>>) – The total absorbed macronutrients from all detected foods, considering digestion efficiency and food interactions.
       - \"amount\" (String) – The total macronutrient amount from all foods combined (e.g., \"92 mg\").
       - \"digestedAmount\" (String) - The total estimated absorbed amount from all foods combined (e.g., \"80 mg\").
       - \"dailyPercentage\" (String) - The percentage of the recommended daily intake from all digested foods combined (e.g., \"102%\").
    - \"digestedMicronutrients\" (Map<String, Map<String, String>>) – The total absorbed micronutrients from all detected foods, considering digestion efficiency and food interactions.
       - \"amount\" (String) – The total micronutrient amount from all foods combined (e.g., \"92 mg\").
       - \"digestedAmount\" (String) - The total estimated absorbed amount from all foods combined (e.g., \"80 mg\").
       - \"dailyPercentage\" (String) - The percentage of the recommended daily intake from all digested foods combined (e.g., \"102%\").
    - \"adjustedCalories\" (String) – The estimated daily caloric intake required based on the user's information. This represents the number of calories the user should consume per day to maintain their current weight.
    - \"healthTips\" (List<String>) – Tips for improvement based on the user's information.
    
    **User Information:**
            ${if (description.isNotBlank()) "- **Foods:** $description" else ""}
            ${if (gender != null) {"- **Gender:** $gender"} else ""}
            ${if (age != null) {"- **Age:** $age"} else ""}
            ${if (height != null) {"- **Height:** $height cm"} else ""}
            ${if (weight != null) {"- **Weight:** $weight kg"} else ""}
            ${if (physicalActivity != null) {"- **Physical Activity Level:** $physicalActivity ${when (physicalActivity) {
                    PhysicalActivityOption.SEDENTARY.activityLevel -> "(Minimal activity, desk job, no exercise)"
                    PhysicalActivityOption.LIGHTLY_ACTIVE.activityLevel -> "(Exercise 1-3 times/week)"
                    PhysicalActivityOption.MODERATE_ACTIVE.activityLevel -> "(Exercise 3-5 times/week)"
                    PhysicalActivityOption.VERY_ACTIVE.activityLevel -> "(Exercise 6-7 times/week)"
                    PhysicalActivityOption.EXTREMELY_ACTIVE.activityLevel -> "(Professional athletes, highly intense physical work)"
                    else -> throw IllegalStateException("Unexpected physical activity level: $physicalActivity")
                }}"} else ""}
                

    Example Output:
    {
      \"titles\": {
          \"digested_calories\": \"<'Digested Calories & Nutrient Intake' translated>\",
          \"total_digested_calories\": \"<'Total Digested Calories' translated>\",
          \"daily_caloric_intake\": \"<'Total Daily Caloric Intake Required' translated>\",
          \"nutrients\": \"<'Nutrients Overview' translated>\",
          \"total_macronutrients\": \"<'Total Macronutrient Intake' translated>\",
          \"total_micronutrients\": \"<'Total Micronutrient Intake' translated>\",
          \"total_digested_amount\": \"<'Total Digested Amount' translated>\",
          \"macronutrients\": \"<'Macronutrients' translated>\",
          \"micronutrients\": \"<'Micronutrients' translated>\",
          \"bmi_info\": \"<'BMI Information' translated>\",
          \"bmi\": \"<'BMI' translated>\",
          \"bmi_classification\": \"<'BMI Classification' translated>\",
          \"detected_foods\": \"<'Detected Foods' translated>\",
          \"food\": \"<'Food' translated>\",
          \"calories\": \"<'Calories' translated>\",
          \"digested_calories\": \"<'Digested Calories' translated>\",
          \"amount\": \"<'Amount' translated>\",
          \"digested_amount\": \"<'Digested Amount' translated>\",
          \"daily_percentage\": \"<'Percentage of Recommended Daily Intake' translated>\",
          \"health_tips\": \"<'Health & Diet Tips' translated>\",
          \"measurement\": \"<'Measurement' translated>\",
          \"weight\": \"<'Weight' translated>\",
          \"size\": \"<'Size' translated>\"
      },
      \"foods\": [
        {
          \"food\": \"<'Grilled Chicken Breast' translated>\",
          \"calories\": \"165\",
          \"digestedCalories\": \"155\",
          \"macronutrients\": {
            \"<'Carbohydrates' translated>\": { \"amount\": \"0 g\", \"digestedAmount\": \"0 g\" },
            \"<'Protein' translated}>\": { \"amount\": \"31 g\", \"digestedAmount\": \"28 g\" },
            \"<'Fat' translated>\": { \"amount\": \"3.6 g\", \"digestedAmount\": \"3.2 g\" }
          },
          \"micronutrients\": {
            \"<'Vitamin B6' translated>\": { \"amount\": \"0.9 mg\", \"digestedAmount\": \"0.8 mg\" },
            \"<'Iron' translated>\": { \"amount\": \"0.9 mg\", \"digestedAmount\": \"0.75 mg\" }
          },
          ${if (imageUsed) {"""\"amount\": \"1 grilled chicken breast\",
          \"measurement\": {
            \"weight\": \"180g\"
          }"""} else ""}
        },
        {
          \"food\": \"<'Brown Rice' translated>\",
          \"calories\": \"215\",
          \"digestedCalories\": \"190\",
          \"macronutrients\": {
            \"<'Carbohydrates' translated>\": { \"amount\": \"45 g\", \"digestedAmount\": \"40 g\" },
            \"<'Protein' translated>\": { \"amount\": \"5 g\", \"digestedAmount\": \"4.5 g\" },
            \"<'Fat' translated>\": { \"amount\": \"1.8 g\", \"digestedAmount\": \"1.5 g\" }
          },
          \"micronutrients\": {
            \"<'Magnesium' translated>\": { \"amount\": \"84 mg\", \"digestedAmount\": \"75 mg\" },
            \"<'Zinc' translated>\": { \"amount\": \"1.2 mg\", \"digestedAmount\": \"1.0 mg\" }
          },
          ${if (imageUsed) {"""\"amount\": \"1 cup cooked brown rice\",
          \"measurement\": {
            \"size\": \"250ml\"
          }"""} else ""}
        },
        {
          \"food\": \"<'Avocado' translated>\",
          \"calories\": \"234\",
          \"digestedCalories\": \"225\",
          \"macronutrients\": {
            \"<'Carbohydrates' translated>\": { \"amount\": \"12 g\", \"digestedAmount\": \"10 g\" },
            \"<'Protein' translated>\": { \"amount\": \"3 g\", \"digestedAmount\": \"2.5 g\" },
            \"<'Fat' translated>\": { \"amount\": \"21 g\", \"digestedAmount\": \"18 g\" }
          },
          \"micronutrients\": {
            \"<'Potassium' translated>\": { \"amount\": \"708 mg\", \"digestedAmount\": \"640 mg\" },
            \"<'Vitamin K' translated>\": { \"amount\": \"21 µg\", \"digestedAmount\": \"18 µg\" }
          },
          ${if (imageUsed) {"""\"amount\": \"1 avocado\",
          \"measurement\": {
            \"weight\": \"220g\"
          }"""} else ""}
        }
      ],
      \"bmi\": \"24.8\",
      \"bmiClassification\": \"Normal weight\",
      \"digestedCalories\": \"750\",
      \"digestedMacronutrients\": {
        \"<'Carbohydrates' translated>\": { \"amount\": \"57 g\", \"digestedAmount\": \"50 g\", \"dailyPercentage\": \"16%\" },
        \"<'Protein' translated>\": { \"amount\": \"39 g\", \"digestedAmount\": \"35 g\", \"dailyPercentage\": \"70%\" },
        \"<'Fat' translated>\": { \"amount\": \"26.4 g\", \"digestedAmount\": \"22.7 g\", \"dailyPercentage\": \"35%\" }
      },
      \"digestedMicronutrients\": {
        \"<'Vitamin B6' translated>\": { \"amount\": \"1.8 mg\", \"digestedAmount\": \"0.8 mg\", \"dailyPercentage\": \"62%\" },
        \"<'Iron' translated>\": { \"amount\": \"1.8 mg\", \"digestedAmount\": \"0.75 mg\", \"dailyPercentage\": \"9%\" },
        \"<'Magnesium' translated>\": { \"amount\": \"159 mg\", \"digestedAmount\": \"75 mg\", \"dailyPercentage\": \"19%\" },
        \"<'Zinc' translated>\": { \"amount\": \"2.2 mg\", \"digestedAmount\": \"1.0 mg\", \"dailyPercentage\": \"10%\" },
        \"<'Potassium' translated>\": { \"amount\": \"1450 mg\", \"digestedAmount\": \"640 mg\", \"dailyPercentage\": \"14%\" },
        \"<'Vitamin K' translated>\": { \"amount\": \"39 µg\", \"digestedAmount\": \"18 µg\", \"dailyPercentage\": \"20%\" }
      },
      \"adjustedCalories\": \"2500\",
      \"healthTips\": [
        \"Increase fiber intake by adding vegetables and whole grains.\",
        \"Ensure a balanced ratio of protein and healthy fats for muscle maintenance.\",
        \"Stay hydrated to support digestion and nutrient absorption.\"
      ]
    }
    
    $doubleQuotes
    ${jsonResponseLanguage(selectedLanguage.languageName)}
    If no foods detected, return an empty json response.
""".trimIndent()

        }
        prompt = buildSolvingPrompt()
    }

    override fun decodeSolutionResponse(response: String): Pair<String, String> = decodeDietSolutionResponse(response)
}