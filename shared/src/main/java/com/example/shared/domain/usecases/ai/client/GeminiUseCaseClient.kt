package com.example.shared.domain.usecases.ai.client

import com.example.shared.data.network.gemini_api.client.GeminiHttpException
import com.example.shared.domain.ai.EnforcementLadder
import com.example.shared.domain.ai.FormatSupportErrors
import com.example.shared.domain.ai.JsonEnforcement
import com.example.shared.domain.ai.ResponseFormat
import com.example.shared.domain.ai.ModelCooldownStore
import com.example.shared.domain.ai.ModelRotation
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import timber.log.Timber

import com.example.shared.data.network.gemini_api.client.GeminiApiService
import com.example.shared.data.network.gemini_api.client.GeminiRequest
import javax.inject.Inject

class GeminiUseCaseClient @Inject constructor(
    private val geminiApiService: GeminiApiService,
    @ApplicationContext context: Context,
) {
    private val cooldowns = ModelCooldownStore(File(context.filesDir, "gemini-model-cooldowns.json"))

    /** The model that actually produced the last successful answer -- for the attribution footer. */
    @Volatile
    var lastUsedModel: String? = null
        private set

    /**
     * Tries [modelName] first, then its sibling models
     * ([GeminiApiService.GeminiModel.ROTATION]) -- ported from rmant7/AI. A
     * failure about the model itself (503 overloaded, 404 retired) cools
     * that model down ([ModelCooldownStore]) and moves on to
     * the next; any other failure is the answer. Within each model,
     * [responseFormat] [ResponseFormat.Json] asks for native structured
     * output, stepping down only when Gemini says explicitly that this model
     * doesn't support the format ([EnforcementLadder]).
     */
    suspend fun generateGeminiSolution(
        generativeLanguageUrls: List<String> = emptyList(),
        prompt: String,
        systemInstruction: String = "",
        modelName: String,
        responseFormat: ResponseFormat = ResponseFormat.Text,
    ): Result<String> {
        val rotation = ModelRotation(PROVIDER, listOf(modelName) + GeminiApiService.GeminiModel.ROTATION, cooldowns)
        val (model, result) = rotation.run(
            isModelProblem = { it is GeminiHttpException && it.status in MODEL_PROBLEM_STATUSES },
            onModelProblem = { failed, failure, cooldownMs ->
                Timber.w("Gemini $failed unavailable (${(failure as GeminiHttpException).status}); cooling it down ${cooldownMs / 1000}s, trying the next model")
            },
        ) { candidate -> withFormat(candidate, generativeLanguageUrls, prompt, systemInstruction, responseFormat) }
        if (result.isSuccess) lastUsedModel = model
        return result
    }

    private suspend fun withFormat(
        modelName: String,
        generativeLanguageUrls: List<String>,
        prompt: String,
        systemInstruction: String,
        responseFormat: ResponseFormat,
    ): Result<String> = ladder.run(
        model = modelName,
        format = responseFormat,
        isFormatUnsupported = { it is GeminiHttpException && FormatSupportErrors.isGeminiFormatUnsupported(it.status, it.body) },
    ) { enforcement ->
        val requestBody = GeminiRequest.buildGeminiRequest(
            fileUris = generativeLanguageUrls,
            prompt = prompt,
            systemInstruction = systemInstruction,
            responseFormat = responseFormat,
            enforcement = enforcement,
        )
        fetchGeminiResponse(requestBody, modelName).also { result ->
            if (enforcement != JsonEnforcement.SCHEMA && responseFormat is ResponseFormat.Json && result.isSuccess) {
                Timber.w("Gemini $modelName answered ${responseFormat.name} at reduced enforcement $enforcement")
            }
        }
    }

    private companion object {
        const val PROVIDER = "gemini"

        /**
         * About the model, not the request or the key: overloaded (503) or
         * retired/unknown (404) -- as in rmant7/AI. A 429 is the key's quota:
         * GeminiApiService marks the key exhausted for the key rotator, and
         * cooling a healthy model down for it would wrongly take it out.
         */
        val MODEL_PROBLEM_STATUSES = setOf(404, 503)

        /** Per model, for the process lifetime: a model that rejected a format isn't asked for it again. */
        val ladder = EnforcementLadder()
    }


    /** Fetch single response using Gemini Api and
     * convert response to html to show in web view */
    private suspend fun fetchGeminiResponse(request: String, modelName: String): Result<String> {

        val response = geminiApiService.generateContent(request, modelName)
        var result: Result<String> = Result.success("")
        response.onSuccess {
            result = Result.success(cleanGeminiResponse(it))
        }
        response.onFailure {
            result = Result.failure(it)
        }
        return result
    }

    private fun cleanGeminiResponse(text: String): String {
        val imageRegex = Regex("""!\[.*?]\(.*?\)""", RegexOption.MULTILINE)
        val emptyLines = Regex("""^\s+""", RegexOption.MULTILINE)
        return text
            .replace(imageRegex, "") // remove all image markdown references
            .trim() // remove leading and trailing spaces
            .replace(emptyLines, "") // remove empty lines
    }

    /** Upload local image and get request for Gemini with actual image url */
    suspend fun getUploadedImageUrl(
        fileByteArray: ByteArray,
        fileName: String,
    ): Result<String> {

        var result = Result.success("")

        val uploadResult = geminiApiService.uploadFileWithProgress(
            fileByteArray,
            fileName
        )

        uploadResult.onSuccess { uploadModel ->
            val fileUriResult = geminiApiService.uploadFileBytes(
                uploadModel.uploadUrl,
                fileByteArray
            )

            fileUriResult.onSuccess {
                result = Result.success(it)
            }

            fileUriResult.onFailure {
                result = Result.failure(it)
            }
        }
        uploadResult.onFailure {
            result = Result.failure(it)
        }
        return result
    }

}