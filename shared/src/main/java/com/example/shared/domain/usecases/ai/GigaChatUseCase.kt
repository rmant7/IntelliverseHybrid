package com.example.shared.domain.usecases.ai

import com.example.shared.data.keys.ApiKeyRotator
import com.example.shared.data.keys.ApiProviderIds
import com.example.shared.data.network.gigachat.GigaChatTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Named

@Serializable
private data class GigaChatMessage(val role: String, val content: String)

@Serializable
private data class GigaChatRequest(val model: String, val messages: List<GigaChatMessage>)

@Serializable
private data class GigaChatChoice(val message: GigaChatMessage)

@Serializable
private data class GigaChatResponse(val choices: List<GigaChatChoice> = emptyList())

class GigaChatHttpException(val status: Int, val body: String) : Exception(
    "GigaChat returned HTTP $status: ${body.take(300)}",
)

/**
 * GigaChat (Sber) via its own (mostly-)OpenAI-compatible `/chat/completions`
 * endpoint. Not routed through [OpenAiUseCase]'s langchain4j client like
 * [GroqUseCase] is: GigaChat's key is not a bearer token by itself — it has
 * to be exchanged for one through [GigaChatTokenProvider] first — and its
 * vision support does not match the OpenAI inline-image-url shape (see
 * `CloudProviders.kt`'s notes upstream), so this sends text only for now.
 *
 * Unverified against a live GigaChat account from this environment for two
 * separate reasons: this app's build/dev network cannot reach
 * `gigachat.devices.sberbank.ru` at all, and even a device that can reach it
 * needs Russia's national "Минцифры" root CA installed — GigaChat's TLS
 * certificate chain is not trusted by Android's default trust store
 * otherwise, and this class does nothing to work around that.
 */
class GigaChatUseCase @Inject constructor(
    @Named(ApiProviderIds.GIGACHAT) private val apiKeyRotator: ApiKeyRotator,
    private val tokenProvider: GigaChatTokenProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Generate a GigaChat solution using text only — see class doc comment on why images are not sent. */
    suspend fun generateGigaChatSolution(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val keyEntry = apiKeyRotator.activeKey()
            ?: return@withContext Result.failure(
                IllegalStateException(apiKeyRotator.exhaustionMessage() ?: "No GigaChat API key configured")
            )
        val authorizationKey = keyEntry.key

        try {
            val accessToken = tokenProvider.token(authorizationKey)
            val requestBody = json.encodeToString(
                GigaChatRequest.serializer(),
                GigaChatRequest(model = DEFAULT_MODEL, messages = listOf(GigaChatMessage("user", prompt))),
            )

            val connection = (URI.create("$BASE_URL/chat/completions").toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            connection.disconnect()

            if (status !in 200..299) {
                if (status == 429) apiKeyRotator.markExhausted(keyEntry.id)
                return@withContext Result.failure(GigaChatHttpException(status, body))
            }

            val content = json.decodeFromString(GigaChatResponse.serializer(), body)
                .choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(IllegalStateException("GigaChat returned no choices"))

            Result.success(content)
        } catch (e: Exception) {
            Timber.e(e)
            Result.failure(e)
        }
    }

    private companion object {
        const val BASE_URL = "https://gigachat.devices.sberbank.ru/api/v1"
        const val DEFAULT_MODEL = "GigaChat-2"
    }
}
