package com.matterofchoice.api

import android.content.Context
import com.example.shared.data.keys.ApiKeyRotator
import com.example.shared.data.keys.ApiProviderIds
import com.example.shared.data.keys.BundledApiKeyStore
import com.example.shared.data.keys.BundledApiKeys
import com.example.shared.data.keys.PrefsApiKeyStore
import com.example.shared.data.network.gigachat.GigaChatTokenProvider
import com.example.shared.domain.usecases.ai.GigaChatUseCase
import com.example.shared.domain.usecases.ai.GroqUseCase
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.matterofchoice.model.Case
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    //
    // One caveat: Python's persona_instruction is the single hardcoded
    // "Behavioral Analyst" line below for every questionType, study and
    // hiring included -- ported faithfully at first, but real-device
    // feedback correctly flagged that framing as wrong for a study/hiring
    // set (the very first thing the model reads calls itself a behavioral
    // analyst, which plausibly biases it back toward behavioral content
    // even though generationContext() below does say "study-based
    // questions"). Made this questionType-aware instead, which the Python
    // version never was.
    fun personaInstruction(questionType: String): String = when (questionType) {
        "study" -> "You are an expert teacher and examiner for the IntelliVerse project. " +
            "Your role is to directly teach and test a learner's actual knowledge of the subject they choose."
        "hiring" -> "You are an Expert Recruitment Analyst for the IntelliVerse project. " +
            "Your role is to create realistic hiring and job-interview scenarios."
        else -> "You are an Expert Behavioral Analyst for the IntelliVerse project. " +
            "Your role is to analyze human decision-making by creating complex, realistic scenarios."
    }

    fun generationContext(questionType: String, subject: String, difficulty: String, age: Int): String = when (questionType) {
        // Real-device feedback: with the shared JSON schema's old "situation
        // description" / "description of action" wording (see
        // caseFormatInstruction below), study mode kept generating scenarios
        // ABOUT studying (e.g. "what's the best way to learn $subject") --
        // advice on how to learn, not actual $subject content. Spelling out
        // exactly what "case" and "option" mean here overrides that generic
        // scenario framing for this one questionType.
        "study" -> "Directly teach and test the learner's own knowledge of $subject itself, at $difficulty " +
            "difficulty level, appropriate for a $age-year-old. Each \"case\" must be an actual $subject " +
            "question, fact, vocabulary item, grammar point, or problem to solve -- NOT a scenario about " +
            "study habits, learning strategies, or the best way to learn. Each \"option\" is a candidate " +
            "answer to that exact question, with exactly one of them correct."
        "hiring" -> "Create 6 job interview scenario questions about $subject at $difficulty difficulty level, appropriate for a $age-year-old."
        else -> "Create 6 realistic behavioral scenario questions about $subject at $difficulty difficulty level, appropriate for a $age-year-old."
    }

    // The scoring dimensions below aren't part of the Python schema (Python's
    // game only needs the 'optimal' index), but Game.kt sums them to compare
    // the player's pick against the optimal one, so they stay. "case"/"option"
    // themselves are deliberately neutral placeholders (matching Python's own
    // generation_instruction, which uses bare "..." here) rather than
    // "situation description"/"description of action" -- that more specific
    // wording (a Kotlin-only addition, not from Python) is what pushed every
    // questionType toward a decision-scenario framing regardless of context;
    // generationContext() above now says explicitly what a case/option means
    // per questionType instead.
    const val caseFormatInstruction = """
        For each case, provide exactly 8 different response options.
        For each option, rate: health, wealth, relationships, happiness, knowledge, karma, time_management, environmental_impact, personal_growth, and social_responsibility.
        Indicate which option number is best ('optimal').

        Return ONLY valid JSON, starting with [ and ending with ].
        JSON structure:
        [
          {
            "case": "...",
            "options": [
              {
                "number": 1,
                "option": "...",
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

private class AllProvidersFailedException(mode: String, val failures: List<Pair<String, Exception>>) :
    Exception("All providers failed during $mode: " + failures.joinToString { (name, e) -> "$name (${shortReason(e)})" })

/**
 * Reduces a raw SDK/network exception from any of the three providers to one
 * short, readable phrase -- each provider's failures have a different raw
 * shape (Gemini's is a JSON error body, Groq/GigaChat's own use cases throw
 * plain IllegalStateException/HTTP-status exceptions), so this checks a few
 * known patterns before falling back to a trimmed version of the raw message.
 */
private fun shortReason(e: Exception): String {
    val raw = e.message ?: e.toString()
    val status = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
    val apiMessage = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)

    return when {
        status == "FAILED_PRECONDITION" && apiMessage?.contains("location", ignoreCase = true) == true ->
            "not available in your region"
        apiMessage != null -> apiMessage
        raw.contains("API key configured", ignoreCase = true) -> "not configured"
        e is java.net.SocketTimeoutException -> "timed out"
        raw.contains("Unable to resolve host", ignoreCase = true) -> "no internet"
        else -> raw.take(100)
    }
}

/**
 * Despite the name (kept to avoid a churny rename across every call site),
 * this now falls back across all three AI providers this shell already has
 * working infrastructure for in the shared module -- Gemini, then Groq, then
 * GigaChat -- the same providers (under different names) as the original
 * Python backend's own get_response() fallback chain
 * (Gemini -> Grok/XAI -> Mistral). Real-device testing hit Gemini's API
 * refusing the request outright with a geographic restriction
 * (FAILED_PRECONDITION "User location is not supported"), which used to mean
 * the whole game failed to start; with nothing else to retry, Gemini alone
 * was a single point of failure. See completeWithFallback().
 */
class GeminiRepository(context: Context) {

    private val gson = Gson()
    private val appContext = context.applicationContext

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

    // GroqUseCase/GigaChatUseCase are normally Hilt-injected (see the shared
    // module's KeysModule) -- MatterOfChoice doesn't use Hilt at all, so
    // these are built by hand, mirroring exactly what KeysModule's
    // rotatorFor() does for each provider. @Inject/@Named/@ApplicationContext
    // on their constructors are just annotations; nothing stops calling them
    // directly like any other Kotlin constructor.
    private val prefsKeyStore by lazy { PrefsApiKeyStore(appContext) }
    private val bundledKeyStore by lazy { BundledApiKeyStore(appContext) }

    private fun keyRotatorFor(providerId: String): ApiKeyRotator {
        BundledApiKeys.sync(bundledKeyStore, providerId)
        return ApiKeyRotator(store = prefsKeyStore, providerId = providerId, bundledStore = bundledKeyStore)
    }

    private val groqUseCase by lazy { GroqUseCase(keyRotatorFor(ApiProviderIds.GROQ)) }
    private val gigaChatUseCase by lazy {
        GigaChatUseCase(keyRotatorFor(ApiProviderIds.GIGACHAT), GigaChatTokenProvider())
    }

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

        Timber.i("GeminiRepository: $mode prompt (with provider fallback):\n${truncateForLog(prompt)}")

        val responseText = try {
            completeWithFallback(prompt, mode)
        } catch (e: AllProvidersFailedException) {
            Timber.e(e, "GeminiRepository: all providers failed during $mode")
            throw IOException(friendlyErrorMessage(e), e)
        }

        Timber.i("GeminiRepository: $mode raw response (${responseText.length} chars):\n${truncateForLog(responseText)}")

        try {
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
            throw IOException("The AI's response wasn't in the expected format. Try again.", e)
        }
    }

    /**
     * Tries Gemini, then Groq, then GigaChat in order, returning the first
     * one that actually answers. Same text-in/text-out prompt for all three
     * -- none of this app's calls use images, so there's no per-provider
     * request-shape difference to handle (unlike this shell's other
     * sub-apps, where GigaChat specifically can't take the image payload the
     * others do).
     *
     * Sequential, not parallel: simpler to reason about for a chain whose
     * whole point is "keep going until one works," and this app has no
     * multi-provider comparison UI (unlike BaseResultViewModel's tabbed
     * results) to make racing them worthwhile. Trade-off: a run where every
     * provider times out takes roughly the sum of all three timeouts, not
     * the max -- acceptable here since a provider being down or misconfigured
     * (the common case: no key configured, or a geographic block) fails fast,
     * not slow.
     */
    private suspend fun completeWithFallback(prompt: String, mode: String): String {
        val providers = listOf<Pair<String, suspend () -> String>>(
            "Gemini" to { model.generateContent(prompt).text ?: throw IOException("Empty response from Gemini") },
            "Groq" to { withContext(Dispatchers.IO) { groqUseCase.generateGroqSolution(emptyList(), prompt).getOrThrow() } },
            "GigaChat" to { gigaChatUseCase.generateGigaChatSolution(prompt).getOrThrow() }
        )

        val failures = mutableListOf<Pair<String, Exception>>()
        for ((name, call) in providers) {
            try {
                Timber.i("GeminiRepository: trying $name for $mode")
                return call()
            } catch (e: Exception) {
                if (name == "Gemini") logGeminiError(mode, e) else Timber.w(e, "GeminiRepository: $name failed for $mode")
                failures += name to e
            }
        }
        throw AllProvidersFailedException(mode, failures)
    }

    /**
     * Reduces a raw SDK/network exception to one short, human-readable sentence
     * for on-screen display. Full technical detail already went to the log via
     * completeWithFallback()/logGeminiError() before this is called -- this is
     * display-only.
     */
    private fun friendlyErrorMessage(e: AllProvidersFailedException): String =
        "Couldn't get a response from any AI provider (" +
        e.failures.joinToString("; ") { (name, err) -> "$name: ${shortReason(err)}" } +
        "). Try again later."

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
        val sb = StringBuilder(Prompts.personaInstruction(questionType))
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
