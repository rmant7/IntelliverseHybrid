package com.matterofchoice.api

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.matterofchoice.model.Case
import timber.log.Timber
import java.io.IOException
import java.util.UUID
object Prompts {
    // Mirrors the persona/context/format split of the original Python
    // implementation's gen_cases() (unified_server/apps/MatterOfChoice/utils.py),
    // which is the version the user confirmed actually worked correctly on
    // real devices. Python builds one context sentence per questionType and
    // feeds it straight into the prompt, rather than prepending a separate
    // "theme directive" -- that separate-directive approach is what this
    // replaces.
    const val personaInstruction =
        "You are an Expert Behavioral Analyst for the IntelliVerse project. " +
        "Your role is to analyze human decision-making by creating complex, realistic scenarios."

    fun generationContext(questionType: String, subject: String, difficulty: String, age: Int): String = when (questionType) {
        "study" -> "Create 6 study-based questions about $subject at $difficulty difficulty level, appropriate for a $age-year-old."
        "hiring" -> "Create 6 job interview scenario questions about $subject at $difficulty difficulty level, appropriate for a $age-year-old."
        else -> "Create 6 realistic behavioral scenario questions about $subject at $difficulty difficulty level, appropriate for a $age-year-old."
    }

    // The scoring dimensions below aren't part of the Python schema (Python's
    // game only needs the 'optimal' index), but Game.kt sums them to compare
    // the player's pick against the optimal one, so they stay.
    const val caseFormatInstruction = """
        For each case, provide exactly 8 different response options.
        For each option, rate: health, wealth, relationships, happiness, knowledge, karma, time_management, environmental_impact, personal_growth, and social_responsibility.
        Indicate which option number is best ('optimal').

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

    fun languageDirective(language: String) =
        "Strictly generate ALL case text and option text in $language (the Solution Language). " +
        "Do NOT use any other language. Ignore the current UI language."

    fun analysisPrompt(role: String, aspect: String, language: String, data: String) = """
        Analyze player decisions as a '$role' expert.
        Each record in data contains the case, all options, and the user's chosen answer.
        Focus on the player's overall $aspect.
        Write in $language.

        DO NOT ANALYZE ANY CASE WHERE THE USER DID NOT ANSWER THE QUESTION -- leave it out of
        the JSON and focus only on answered ones.

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
            "generate" -> {
                // Timber.i, not .d -- AppLogTree only forwards INFO+ into the
                // persistent on-device log (see its doc comment), so this is
                // the only way to actually see, after the fact on a real
                // device, what was sent for a given generation.
                Timber.i(
                    "GeminiRepository: generate request -- language=$language, subject=$subject, " +
                    "difficulty=$difficulty, questionType=$questionType, subType=$subType, age=$age, " +
                    "sex=$sex, previousCases=${previousCases.size}"
                )
                buildGeneratePrompt(language, subject, difficulty, questionType, subType, age, sex, previousAnswers, previousCases)
            }
            "analyze" -> {
                // Wording matches Python's judgement_aspect in app.py exactly.
                val aspect = when (questionType) {
                    "study" -> "knowledge and learning style"
                    "hiring" -> "suitability for the job"
                    else -> "behavioral tendencies"
                }

                // Python filters to cases the user actually answered before
                // building analysis_data_str -- an unanswered case has no
                // user_choice and would otherwise skew/confuse the analysis.
                val answeredCases = previousCases.filter { previousAnswers[it.case_id] != null }
                Timber.i(
                    "GeminiRepository: analyze request -- role=$role, questionType=$questionType, " +
                    "aspect=$aspect, language=$language, answeredCases=${answeredCases.size}/${previousCases.size}"
                )
                val dataJson = gson.toJson(answeredCases.map {
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

        Timber.i("GeminiRepository: $mode prompt sent to Gemini:\n${truncateForLog(prompt)}")

        var responseText: String? = null
        try {
            val response = model.generateContent(prompt)
            responseText = response.text ?: throw IOException("Empty response from Gemini")
            Timber.i("GeminiRepository: $mode raw response (${responseText.length} chars):\n${truncateForLog(responseText)}")

            val cleanJson = extractJson(responseText)

            return if (mode == "generate") {
                val listType = object : TypeToken<List<Case>>() {}.type
                val cases = gson.fromJson<List<Case>>(cleanJson, listType).map {
                    it.copy(case_id = UUID.randomUUID().toString())
                }
                Timber.i("GeminiRepository: parsed ${cases.size} cases from response")
                cases
            } else {
                val result = gson.fromJson(cleanJson, AnalysisResultResponse::class.java)
                Timber.i(
                    "GeminiRepository: parsed analysis -- ${result.cases.size} case entries, " +
                    "overall_judgement length=${result.overall_judgement?.length ?: 0}"
                )
                result
            }
        } catch (e: JsonSyntaxException) {
            Timber.e(e, "GeminiRepository: JSON parsing failed during $mode. Response: $responseText")
            throw IOException("Gemini returned invalid JSON.", e)
        } catch (e: Exception) {
            logGeminiError(mode, e)
            throw e
        }
    }

    /**
     * Gemini's own SDK surfaces a non-2xx HTTP response as an exception whose
     * message is the raw error body (e.g. `{"error": {"code": 400, "message":
     * "...", "status": "FAILED_PRECONDITION"}}`). Pulling code/status out with
     * a regex -- rather than catching a specific SDK exception subtype -- keeps
     * this working regardless of which exception class the SDK actually throws
     * for a given failure, and still logs something useful (just without a
     * code/status) for failures that never got an HTTP response at all, like a
     * timeout or connection reset.
     */
    private fun logGeminiError(mode: String, e: Exception) {
        val raw = e.message ?: e.toString()
        val code = Regex("\"code\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)
        val status = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)

        if (code != null || status != null) {
            Timber.e(
                e,
                "GeminiRepository: API error during $mode -- code=${code ?: "?"} status=${status ?: "?"}"
            )
        } else {
            Timber.e(e, "GeminiRepository: call failed during $mode (no structured error code -- likely network/timeout)")
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
        val sb = StringBuilder(Prompts.personaInstruction)
        sb.append("\n\n")
        sb.append(Prompts.generationContext(questionType, subject, difficulty, age))
        sb.append(" Subtype: $subType. Audience gender: $sex.")
        sb.append("\n\n")
        sb.append(Prompts.caseFormatInstruction)
        sb.append("\n\n")
        sb.append(Prompts.languageDirective(language))

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

    /**
     * The on-device log is a plain text file read in-app -- an untruncated
     * multi-thousand-character prompt or response on every single generation
     * call would drown everything else in it within a few turns.
     */
    private fun truncateForLog(text: String, maxChars: Int = 4000): String =
        if (text.length <= maxChars) text else text.take(maxChars) + "... [truncated, ${text.length} chars total]"

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
