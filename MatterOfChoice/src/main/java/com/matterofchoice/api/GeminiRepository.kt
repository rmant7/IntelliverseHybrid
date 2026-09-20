package com.matterofchoice.api

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.matterofchoice.model.Case
import java.io.IOException
import java.util.UUID
object Prompts {
    const val baseCasePrompt = """
        You are designing scenarios for an educational simulation game.
        Generate EXACTLY 6 life situations. 
        Each situation must have 8 behavioral options.
        For each option, rate: health, wealth, relationships, happiness, knowledge, karma, time_management, environmental_impact, personal_growth, and social_responsibility.
        Indicate which option number is the best ('optimal').

        Return ONLY valid JSON, starting with [ and ending with ].
        JSON structure:
        [
          {
            "case": "situation description",
            "options": [
              {
                "number": 1,
                "option": "description of action",
                "health": 0-10,
                "wealth": 0-10,
                ...
              }
            ],
            "optimal": 3
          }
        ]
    """

    fun analysisPrompt(role: String, aspect: String, language: String, data: String) = """
        Analyze player decisions as a '$role' expert.
        Each record in data contains the case, all options, and the user's chosen answer.
        Focus on the player's overall $aspect.
        Write in $language.

        Return ONLY valid JSON:
        {
          "overall_judgement": "summary",
          "cases": [
            {
              "case_description": "...",
              "player_choice": "...",
              "optimal_choice": "...",
              "analysis": "..."
            }
          ]
        }

        Data:
        $data
    """
}

class GeminiRepository {

    private val gson = Gson()

    private val model = GenerativeModel(
        modelName = "gemini-flash-lite-latest",
        apiKey = com.example.shared.BuildConfig.gemini_api_key,
        generationConfig = generationConfig {
            temperature = 1f
            topP = 0.9f
            topK = 40
        },
        requestOptions = RequestOptions(
            timeout = 180_000
        )
    )

    /**
     * Generate new cases for the game.
     */
    suspend fun generateCases(
        language: String,
        subject: String,
        difficulty: String,
        questionType: String,
        subType: String,
        age: Int,
        sex: String,
        previousAnswers: Map<String, String>,
        previousCases: List<Case>
    ): List<Case> {
        val result = generateOrAnalyze(
            mode = "generate",
            language = language,
            subject = subject,
            difficulty = difficulty,
            questionType = questionType,
            subType = subType,
            age = age,
            sex = sex,
            previousAnswers = previousAnswers,
            previousCases = previousCases
        )
        @Suppress("UNCHECKED_CAST")
        return result as List<Case>
    }

    /**
     * Submit player answers for AI-based analysis.
     */
    suspend fun submitAnalysis(
        answers: Map<String, String>,
        allCases: List<Case>,
        role: String,
        questionType: String,
        language: String
    ): AnalysisResultResponse {
        val result = generateOrAnalyze(
            mode = "analyze",
            language = language,
            questionType = questionType,
            previousAnswers = answers,
            previousCases = allCases,
            role = role
        )
        return result as AnalysisResultResponse
    }

    /**
     * Shared core logic for both generation and analysis.
     */
    private suspend fun generateOrAnalyze(
        mode: String,
        language: String = "English",
        subject: String = "Life",
        difficulty: String = "Medium",
        questionType: String = "behavioral",
        subType: String = "default",
        age: Int = 25,
        sex: String = "any",
        previousAnswers: Map<String, String> = emptyMap(),
        previousCases: List<Case> = emptyList(),
        role: String = "Psychologist"
    ): Any {
        val prompt = when (mode) {
            "generate" -> buildGeneratePrompt(language, subject, difficulty, questionType, subType, age, sex, previousAnswers, previousCases)
            "analyze" -> {
                val aspect = when (questionType) {
                    "behavioral" -> "behavioral tendencies"
                    "study" -> "learning pattern"
                    "hiring" -> "job suitability"
                    else -> "performance"
                }

                val dataJson = gson.toJson(previousCases.map {
                    mapOf(
                        "case" to it.case,
                        "options" to it.options,
                        "user_choice" to previousAnswers[it.case_id]
                    )
                })
                Prompts.analysisPrompt(role, aspect, language, dataJson)
            }

            else -> throw IllegalArgumentException("Invalid mode: $mode. Use 'generate' or 'analyze'.")
        }

        Log.d("GeminiRepository", "Executing $mode prompt...")

        var responseText: String? = null
        try {
            val response = model.generateContent(prompt)
            responseText = response.text ?: throw IOException("Empty response from Gemini")
            Log.d("GeminiRepository", "Gemini raw response (${responseText.length} chars)")

            val cleanJson = extractJson(responseText)

            return if (mode == "generate") {
                val listType = object : TypeToken<List<Case>>() {}.type
                gson.fromJson<List<Case>>(cleanJson, listType).map {
                    it.copy(case_id = UUID.randomUUID().toString())
                }
            } else {
                gson.fromJson(cleanJson, AnalysisResultResponse::class.java)
            }
        } catch (e: JsonSyntaxException) {
            Log.e("GeminiRepository", "JSON parsing failed: ${e.message}\nResponse: $responseText")
            throw IOException("Gemini returned invalid JSON.", e)
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Gemini error during $mode: ${e.message}", e)
            throw e
        }
    }

    private fun buildGeneratePrompt(
        language: String,
        subject: String,
        difficulty: String,
        questionType: String,
        subType: String,
        age: Int,
        sex: String,
        previousAnswers: Map<String, String>,
        previousCases: List<Case>
    ): String {
        val sb = StringBuilder(Prompts.baseCasePrompt)
        sb.append("\nRespond in $language for a $age-year-old $sex. Subject: $subject, Difficulty: $difficulty, Type: $subType.")

        if (previousCases.isNotEmpty()) {
            sb.append("\n\nPrevious context:\n")
            previousCases.forEach { case ->
                val answer = previousAnswers[case.case_id]?.getOrNull(0)?.minus('A')?.plus(1)
                sb.append("Case: ${case.case}\n")
                sb.append("Chosen option: ${answer ?: "N/A"}\n")
            }
            sb.append("\nGenerate 6 new cases based on similar themes but avoid repetition. Maintain JSON format exactly.")
        }

        return sb.toString()
    }

    private fun extractJson(raw: String?): String {
        if (raw == null) throw IOException("Empty Gemini response")

        var clean = raw.trim()
            .replace(Regex("(?s)```json"), "")
            .replace(Regex("(?s)```"), "")
            .trim()

        if (clean.startsWith("\"") && clean.endsWith("\"")) {
            clean = clean.substring(1, clean.length - 1)
                .replace("\\\"", "\"")
                .replace("\\n", "")
        }

        return clean.trim()
    }
}
