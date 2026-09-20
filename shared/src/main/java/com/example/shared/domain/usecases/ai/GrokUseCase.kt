package com.example.shared.domain.usecases.ai

import com.example.shared.data.keys.ApiKeyRotator
import com.example.shared.data.keys.ApiProviderIds
import dev.ai4j.openai4j.OpenAiHttpException
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.Content
import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.output.Response
import timber.log.Timber
import java.time.Duration
import javax.inject.Inject
import javax.inject.Named

/**
 * Grok (xAI) via its OpenAI-compatible endpoint. xAI's API accepts the exact
 * same request/response shape OpenAI's does at a different base URL, so this
 * is [OpenAiUseCase] with [OpenAiChatModel.Builder.baseUrl] pointed at xAI
 * instead of OpenAI, plus key rotation across [apiKeyRotator]'s pool.
 */
class GrokUseCase @Inject constructor(
    @Named(ApiProviderIds.GROK) private val apiKeyRotator: ApiKeyRotator,
) {
    private fun cleanResult(response: String): String {
        return response.replace(
            Regex("""\\\[(.*?)\\]""", RegexOption.DOT_MATCHES_ALL)
        ) { matchResult -> "$$${matchResult.groupValues[1]}$$" }
    }

    /** Generate a Grok solution using text and optionally one or more base64-encoded JPEG images. */
    fun generateGrokSolution(
        imagesBase64: List<String>,
        prompt: String,
        systemInstruction: String = "",
    ): Result<String> {
        val keyEntry = apiKeyRotator.activeKey()
            ?: return Result.failure(
                IllegalStateException(apiKeyRotator.exhaustionMessage() ?: "No Grok API key configured")
            )
        val apiKey = keyEntry.key

        val model = OpenAiChatModel.builder()
            .baseUrl(BASE_URL)
            .apiKey(apiKey)
            .modelName(DEFAULT_MODEL)
            .timeout(Duration.ofSeconds(90L))
            .build()

        val userMessage = if (imagesBase64.isNotEmpty()) {
            val contents = mutableListOf<Content>()
            imagesBase64.mapTo(contents) { base64Data ->
                ImageContent.from(base64Data, "image/jpeg", ImageContent.DetailLevel.HIGH)
            }
            contents.add(TextContent.from(prompt))
            UserMessage.from(contents)
        } else {
            UserMessage.from(TextContent.from(prompt))
        }

        return try {
            val response: Response<AiMessage> = if (systemInstruction.isBlank()) {
                model.generate(userMessage)
            } else {
                model.generate(SystemMessage.from(systemInstruction), userMessage)
            }
            Result.success(cleanResult(response.content().text()))
        } catch (e: OpenAiHttpException) {
            Timber.d(e)
            if (e.code() == 429) {
                apiKeyRotator.markExhausted(keyEntry.id)
            }
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Timber.d(e)
            Result.failure(e)
        } catch (e: RuntimeException) {
            Timber.d(e)
            Result.failure(e)
        }
    }

    private companion object {
        const val BASE_URL = "https://api.x.ai/v1"
        const val DEFAULT_MODEL = "grok-4-fast"
    }
}
