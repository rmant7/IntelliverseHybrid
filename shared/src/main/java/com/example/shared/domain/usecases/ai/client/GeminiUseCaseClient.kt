package com.example.shared.domain.usecases.ai.client

import com.example.shared.data.network.gemini_api.client.GeminiRequestRejected
import com.example.shared.domain.ai.EnforcementLadder
import com.example.shared.domain.ai.FormatSupportErrors
import com.example.shared.domain.ai.JsonEnforcement
import com.example.shared.domain.ai.ResponseFormat
import timber.log.Timber

import com.example.shared.data.network.gemini_api.client.GeminiApiService
import com.example.shared.data.network.gemini_api.client.GeminiRequest
import javax.inject.Inject

class GeminiUseCaseClient @Inject constructor(
    private val geminiApiService: GeminiApiService
) {

    /** Generate Gemini solution using the image */
    /**
     * [responseFormat] [ResponseFormat.Json] asks Gemini for native structured
     * output (JSON + the response model's schema). Only if Gemini answers that
     * this model does not support that does the same request go out again
     * with less enforcement (JSON syntax only, then prompt only) -- see
     * [EnforcementLadder]; every other failure is returned as is.
     */
    suspend fun generateGeminiSolution(
        generativeLanguageUrls: List<String> = emptyList(),
        prompt: String,
        systemInstruction: String = "",
        modelName: String,
        responseFormat: ResponseFormat = ResponseFormat.Text,
    ): Result<String> = ladder.run(
        model = modelName,
        format = responseFormat,
        isFormatUnsupported = { it is GeminiRequestRejected && FormatSupportErrors.isGeminiFormatUnsupported(it.status, it.body) },
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