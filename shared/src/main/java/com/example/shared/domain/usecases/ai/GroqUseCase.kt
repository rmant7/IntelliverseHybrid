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
 * Model rotation, not one hardcoded name: two different Llama 4 checkpoints
 * hardcoded here in turn ("meta-llama/llama-4-scout-17b-16e-instruct", then
 * its sibling maverick) each came back HTTP 400 model_not_found on a real
 * account, confirmed via this app's own Log screen -- Groq's free-tier
 * catalogue drifts faster than this code can be verified against a live
 * account from this environment (no network access to api.groq.com here).
 * [MODEL_CANDIDATES] is tried in order per call; a candidate that comes back
 * model_not_found is skipped in favor of the next one in the SAME call,
 * rather than being a permanent, unrecoverable failure for the whole
 * provider. Vision-capable models are listed first since this app's core
 * flows are photo-driven (diet photos, homework photos, ...) -- Groq's own
 * docs (console.groq.com/docs/vision) confirm its gpt-oss reasoning models
 * do not accept image input at all, so those are deliberately not candidates
 * here.
 */
class GroqUseCase @Inject constructor(
    @Named(ApiProviderIds.GROQ) private val apiKeyRotator: ApiKeyRotator,
) {
    private fun cleanResult(response: String): String {
        return response.replace(
            Regex("""\\\[(.*?)\\]""", RegexOption.DOT_MATCHES_ALL)
        ) { matchResult -> "$$${matchResult.groupValues[1]}$$" }
    }

    private fun isModelNotFound(e: OpenAiHttpException): Boolean =
        e.code() == 400 && e.message?.contains("model_not_found") == true

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

        var lastFailure: Throwable? = null
        for (modelName in MODEL_CANDIDATES) {
            val model = OpenAiChatModel.builder()
                .baseUrl(BASE_URL)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(90L))
                .build()

            try {
                val response: Response<AiMessage> = if (systemInstruction.isBlank()) {
                    model.generate(userMessage)
                } else {
                    model.generate(SystemMessage.from(systemInstruction), userMessage)
                }
                return Result.success(cleanResult(response.content().text()))
            } catch (e: OpenAiHttpException) {
                Timber.e(e, "Groq model $modelName failed")
                if (isModelNotFound(e)) {
                    lastFailure = e
                    continue
                }
                if (e.code() == 429) {
                    apiKeyRotator.markExhausted(keyEntry.id)
                }
                return Result.failure(e)
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Groq model $modelName failed")
                return Result.failure(e)
            } catch (e: RuntimeException) {
                Timber.e(e, "Groq model $modelName failed")
                return Result.failure(e)
            }
        }
        // Every candidate came back model_not_found -- this API key's
        // account has access to none of them, not a transient issue a retry
        // would fix. Surfaced as its own message (rather than just the last
        // model's raw error) so the Log screen shows this is a full-list
        // exhaustion, not one model's ordinary hiccup.
        return Result.failure(
            IllegalStateException(
                "None of Groq's candidate models (${MODEL_CANDIDATES.joinToString()}) " +
                    "are accessible with this API key",
                lastFailure,
            )
        )
    }

    private companion object {
        const val BASE_URL = "https://api.groq.com/openai/v1"
        val MODEL_CANDIDATES = listOf(
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "meta-llama/llama-4-maverick-17b-128e-instruct",
            "llama-3.2-90b-vision-preview",
            "llama-3.2-11b-vision-preview",
        )
    }
}
