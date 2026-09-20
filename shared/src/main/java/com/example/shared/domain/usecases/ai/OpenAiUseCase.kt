package com.example.shared.domain.usecases.ai

import android.os.Build
import com.google.ai.client.generativeai.common.ServerException
import dev.ai4j.openai4j.OpenAiHttpException
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.Content
import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiChatModelName
import dev.langchain4j.model.output.Response
import timber.log.Timber
import java.time.Duration
import javax.inject.Inject

/**
 * GPT via langchain4j's "demo" API key -- not a real OpenAI key, but a
 * string langchain4j itself special-cases to route through its own free,
 * shared proxy (gpt-4o-mini only), meant for trying the library rather than
 * production use. No key rotation here because there's no real key to
 * rotate: this quota is shared across every developer using this same
 * string, worldwide, so it 429s more the more popular the library gets --
 * confirmed via a real device log, and not something retrying or waiting
 * fixes reliably. A real, paid OpenAI key (wired through [ApiKeyRotator]
 * the same way [GroqUseCase] is) is the only actual fix; until then this
 * stays in the parallel provider list since it costs nothing to keep
 * trying and an occasional free response is a bonus.
 */
class OpenAiUseCase @Inject constructor() {

    private val duration: Duration? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Duration.ofSeconds(90L)
    } else {
        null
    }

    private val model: OpenAiChatModel = OpenAiChatModel.builder()
        .apiKey("demo")
        .modelName(OpenAiChatModelName.GPT_4_O_MINI).apply {
            if (duration != null) {
                timeout(duration) // Only set timeout if Duration is available
            }
        }.build()

    private fun generateOpenAiSolution(
        userMessage: UserMessage,
        systemInstruction: String
    ): Result<String> {

        try {
            val response: Response<AiMessage> = if (systemInstruction.isBlank()) {
                model.generate(
                    userMessage,
                )
            } else {
                model.generate(
                    SystemMessage.from(systemInstruction),
                    userMessage
                )
            }
            val contentText = response.content().text()
            return Result.success(cleanOpenAiResult(contentText))
        } catch (e: ServerException) {
            Timber.e(e)
            return Result.failure(e)
        } catch (e: OpenAiHttpException) {
            if (e.code() == 429) {
                // The "demo" key above is langchain4j's own free, shared
                // proxy quota (see its own doc comment) -- with no real
                // OpenAI key configured, this is its permanent, expected
                // state, not a one-off problem worth a full stack trace on
                // every single call. Left in the parallel provider list
                // anyway: it costs nothing to keep trying, and an occasional
                // free response is a bonus if that shared quota ever eases.
                Timber.w("GPT (demo key) rate-limited -- see OpenAiUseCase's doc comment")
            } else {
                Timber.e(e)
            }
            return Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
            return Result.failure(e)
        } catch (e: RuntimeException) {
            Timber.e(e)
            return Result.failure(e)
        }
    }

    private fun cleanOpenAiResult(response: String): String {
        return response.replace(
            Regex("""\\\[(.*?)\\]""", RegexOption.DOT_MATCHES_ALL)
        ) { matchResult -> "$$${matchResult.groupValues[1]}$$" }
    }

    /** Generate GPT solution using text and optionally one or more images,
     * passed as base64-encoded JPEG data (no external hosting required). */
    fun generateOpenAiSolution(
        imagesBase64: List<String>,
        prompt: String,
        systemInstruction: String = ""
    ): Result<String> {

        val userMessage = if (imagesBase64.isNotEmpty()) {
            val contents = mutableListOf<Content>()

            // Add image contents
            imagesBase64.mapTo(contents) { base64Data ->
                ImageContent.from(base64Data, "image/jpeg", ImageContent.DetailLevel.HIGH)
            }

            // Add the text prompt after the images (or before, depending on your use case)
            contents.add(TextContent.from(prompt))
            UserMessage.from(contents)
        } else {
            UserMessage.from(
                TextContent.from(prompt)
            )
        }

        return generateOpenAiSolution(userMessage, systemInstruction)
    }


}