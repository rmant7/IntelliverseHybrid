package com.example.shared.domain.usecases.ai.client

import com.example.shared.data.network.gemini_api.client.GeminiApiService
import com.example.shared.data.network.gemini_api.client.GeminiRequest
import javax.inject.Inject

class GeminiUseCaseClient @Inject constructor(
    private val geminiApiService: GeminiApiService
) {

    /** Generate Gemini solution using the image */
    suspend fun generateGeminiSolution(
        generativeLanguageUrls: List<String> = emptyList(),
        prompt: String,
        systemInstruction: String = "",
        modelName: String
    ): Result<String> {

        val requestBody = GeminiRequest.buildGeminiRequest(
            fileUris = generativeLanguageUrls,
            prompt = prompt,
            systemInstruction = systemInstruction
        )

        return fetchGeminiResponse(requestBody, modelName)
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