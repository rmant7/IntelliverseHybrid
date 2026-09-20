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
 * Groq (api.groq.com) via its OpenAI-compatible endpoint. Groq accepts the
 * exact same request/response shape OpenAI's does at a different base URL,
 * so this is [OpenAiUseCase] with [OpenAiChatModel.Builder.baseUrl] pointed
 * at Groq instead of OpenAI, plus key rotation across [apiKeyRotator]'s pool.
 *
 * Model is a Llama 4 checkpoint rather than Groq's faster gpt-oss models:
 * Groq's own docs (console.groq.com/docs/vision) confirm gpt-oss does not
 * accept image input at all, while this app's core flows are photo-driven
 * (diet photos, homework photos, ...), so a vision-capable model is the
 * only sane default here.
 *
 * NOTE: a real device log confirmed "meta-llama/llama-4-scout-17b-16e-instruct"
 * returns HTTP 400 model_not_found on this account -- switched to its Llama 4
 * sibling below. This has NOT been verified against a live Groq account from
 * here (no network access to api.groq.com in this environment); check
 * console.groq.com/docs/models for the current model ID if this one also 404s.
 */
class GroqUseCase @Inject constructor(
    @Named(ApiProviderIds.GROQ) private val apiKeyRotator: ApiKeyRotator,
) {
    private fun cleanResult(response: String): String {
        return response.replace(
            Regex("""\\\[(.*?)\\]""", RegexOption.DOT_MATCHES_ALL)
        ) { matchResult -> "$$${matchResult.groupValues[1]}$$" }
    }

    /** Generate a Groq solution using text and optionally one or more base64-encoded JPEG images. */
    fun generateGroqSolution(
        imagesBase64: List<String>,
        prompt: String,
        systemInstruction: String = "",
    ): Result<String> {
        val keyEntry = apiKeyRotator.activeKey()
            ?: return Result.failure(
                IllegalStateException(apiKeyRotator.exhaustionMessage() ?: "No Groq API key configured")
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
            Timber.e(e)
            if (e.code() == 429) {
                apiKeyRotator.markExhausted(keyEntry.id)
            }
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
            Result.failure(e)
        } catch (e: RuntimeException) {
            Timber.e(e)
            Result.failure(e)
        }
    }

    private companion object {
        const val BASE_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_MODEL = "meta-llama/llama-4-maverick-17b-128e-instruct"
    }
}
