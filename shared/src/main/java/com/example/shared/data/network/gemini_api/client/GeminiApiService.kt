package com.example.shared.data.network.gemini_api.client

import com.example.shared.UnableToAssistException
import com.example.shared.data.keys.ApiKeyRotator
import com.example.shared.data.keys.ApiProviderIds
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Named

/**
 * Gemini via its plain REST API (not through langchain4j the way
 * GPT/Groq are, since streaming image upload has no equivalent there).
 *
 * Now goes through [apiKeyRotator] instead of a single BuildConfig key
 * directly -- until this, a single 429 (Gemini's free tier is
 * quota-limited per key/day) failed every request for the rest of that
 * quota window with nothing else to fall back to, which is almost
 * certainly why Gemini -- otherwise the one provider actually answering
 * during this app's real-device testing -- still intermittently produced
 * no answer at all. [KeysModule] already provisioned a
 * `@Named(ApiProviderIds.GEMINI)` rotator for exactly this; this class
 * just wasn't consuming it yet.
 */
class GeminiApiService @Inject constructor(
    private val client: HttpClient,
    @Named(ApiProviderIds.GEMINI) private val apiKeyRotator: ApiKeyRotator,
) {

    // Gemini Http routes
    private object HttpRoutes{
        const val GENERATIVE_LANGUAGE_BASE_URL = "https://generativelanguage.googleapis.com"
        const val UPLOAD_URL = "$GENERATIVE_LANGUAGE_BASE_URL/upload"
        const val MODELS_URL = "$GENERATIVE_LANGUAGE_BASE_URL/v1beta/models"
    }

    // Gemini Models
    object GeminiModel {
        const val GEMINI_2_5_FLASH = "gemini-2.5-flash"
        //const val GEMINI_2_5_FLASH_LITE = "gemini-2.5-flash-lite"
    }

    suspend fun uploadFileWithProgress(
        fileByteArray: ByteArray,
        fileName: String,
    ): Result<UploadModel> {
        val keyEntry = apiKeyRotator.activeKey()
            ?: return Result.failure(
                IllegalStateException(apiKeyRotator.exhaustionMessage() ?: "No Gemini API key configured")
            )
        return try {
            val response: HttpResponse = client.post(
                "${HttpRoutes.UPLOAD_URL}/v1beta/files?key=${keyEntry.key}"
            ) {
                header("X-Goog-Upload-Protocol", "resumable")
                header("X-Goog-Upload-Command", "start")
                header("X-Goog-Upload-Header-Content-Length", fileByteArray.size)
                header("X-Goog-Upload-Header-Content-Type", "image/jpeg")
                contentType(ContentType.Application.Json)
                setBody("{'file': {'display_name': '$fileName'}}")
            }

            val uploadUrl = response.headers["X-Goog-Upload-URL"]

            return if (uploadUrl != null) {
                Timber.d("Upload url is $uploadUrl")
                Result.success(UploadModel(uploadUrl))
            } else {
                Timber.d("Upload url is null")
                Result.failure(ServerResponseException(response, "Upload url is null"))
            }

        } catch (e: RedirectResponseException) {
            //3xx - responses
            Timber.e(e, e.message)
            Result.failure(e)
        } catch (e: ClientRequestException) {
            //4xx - response
            if (e.response.status == HttpStatusCode.TooManyRequests) {
                apiKeyRotator.markExhausted(keyEntry.id)
            }
            Timber.e(e, e.message)
            Result.failure(e)
        } catch (e: ServerResponseException) {
            //5xx - response
            Timber.e(e, e.message)
            Result.failure(e)
        } catch (e: UnknownHostException) {
            Timber.e(e, e.message)
            Result.failure(e)
        } catch (e: IOException) {
            Timber.e(e, e.message)
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, e.message)
            Result.failure(e)
        }
    }

    suspend fun uploadFileBytes(
        uploadUrl: String,
        fileByteArray: ByteArray,
    ): Result<String> {
        return try {
            val response: HttpResponse = client.post(uploadUrl) {
                header("Content-Length", fileByteArray.size)
                header("X-Goog-Upload-Offset", 0)
                header("X-Goog-Upload-Command", "upload, finalize")
                setBody(ByteReadChannel(fileByteArray))
            }

            val actualFileUri = Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["file"]?.jsonObject?.get("uri")?.jsonPrimitive?.content
            if (!actualFileUri.isNullOrEmpty())
                Result.success(actualFileUri)
            else
                Result.failure(NullPointerException())

        } catch (e: RedirectResponseException) {
            //3xx - responses
            Timber.e(e, e.message)
            Result.failure(e)
        } catch (e: ClientRequestException) {
            //4xx - response
            Timber.e(e, e.message)
            Result.failure(e)
        } catch (e: ServerResponseException) {
            //5xx - response
            Timber.e(e, e.message)
            Result.failure(e)
        } catch (e: UnknownHostException) {
            Timber.e(e, e.message)
            Result.failure(e)
        } catch (e: IOException) {
            Timber.e(e, e.message)
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, e.message)
            Result.failure(e)
        }
    }

    suspend fun generateContent(requestBody: String, modelName: String): Result<String> {
        val keyEntry = apiKeyRotator.activeKey()
            ?: return Result.failure(
                IllegalStateException(apiKeyRotator.exhaustionMessage() ?: "No Gemini API key configured")
            )

        var networkRetriesLeft = NETWORK_RETRY_ATTEMPTS
        while (true) {
            try {
                val response: HttpResponse = client.post(
                    "${HttpRoutes.MODELS_URL}/${modelName}:generateContent?key=${keyEntry.key}"

                ) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

                val plainTextResponse = jsonResponseToString(response.bodyAsText())
                return if (plainTextResponse.isNullOrEmpty()) {
                    Result.failure(UnableToAssistException)
                } else {
                    Result.success(plainTextResponse)
                }

            } catch (e: RedirectResponseException) {
                //3xx - responses
                Timber.e(e, e.message)
                return Result.failure(e)
            } catch (e: ClientRequestException) {
                //4xx - response
                if (e.response.status == HttpStatusCode.TooManyRequests) {
                    // Gemini's free tier is quota-limited per key/day -- without
                    // this, one exhausted key failed every request for the rest
                    // of the quota window with nothing else to fall back to.
                    apiKeyRotator.markExhausted(keyEntry.id)
                }
                Timber.e(e, e.message)
                return Result.failure(e)
            } catch (e: ServerResponseException) {
                //5xx - response
                Timber.e(e, e.message)
                return Result.failure(e)
            } catch (e: UnknownHostException) {
                // A DNS lookup failure says nothing about Gemini or this key
                // -- a real device log caught this being treated as a hard
                // failure on the very first attempt, from a plain transient
                // connectivity blip. One retry before giving up on it.
                if (networkRetriesLeft > 0) {
                    networkRetriesLeft--
                    Timber.w("Gemini call hit a transient DNS failure, retrying ($networkRetriesLeft attempt(s) left)")
                    delay(NETWORK_RETRY_DELAY_MS)
                    continue
                }
                Timber.e(e, e.message)
                return Result.failure(e)
            } catch (e: Exception) {
                // HttpRequestTimeoutException and SocketTimeoutException are
                // both IOException subtypes with no shared marker interface
                // of their own, so they're matched here rather than as their
                // own catch clauses -- same transient-retry treatment as an
                // UnknownHostException above, and everything else keeps its
                // existing behavior.
                if ((e is HttpRequestTimeoutException || e is SocketTimeoutException) && networkRetriesLeft > 0) {
                    networkRetriesLeft--
                    Timber.w("Gemini call timed out, retrying ($networkRetriesLeft attempt(s) left)")
                    delay(NETWORK_RETRY_DELAY_MS)
                    continue
                }
                Timber.e(e, e.message)
                return Result.failure(e)
            }
        }
    }

    private fun jsonResponseToString(jsonResponse: String): String? {
        val json = Json {
            ignoreUnknownKeys = true
        }
        val geminiJsonResponse = json.decodeFromString<GeminiJsonResponse>(jsonResponse)
        // candidate.content.parts[2].text -> get second result,
        // but multiple candidates aren't supported yet (?)
        val text = geminiJsonResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (text.isNullOrEmpty()) {
            // A blank/missing text used to look identical whether the
            // model said nothing, refused on a safety filter, or ran out
            // of its token budget (a real risk for a thinking model, which
            // spends part of it on reasoning before ever writing an
            // answer) -- surfaced here at WARN so it actually reaches the
            // Log screen instead of silently becoming "no answer" with no
            // way to tell which of those it was.
            val blockReason = geminiJsonResponse.promptFeedback?.blockReason
            val finishReason = geminiJsonResponse.candidates?.firstOrNull()?.finishReason
            Timber.w(
                "Gemini returned no text -- blockReason=$blockReason, finishReason=$finishReason"
            )
        }
        return text
    }

    private companion object {
        // One retry for a transient network failure (DNS, socket/request
        // timeout) before giving up -- these are momentary by definition,
        // not evidence Gemini or this key is actually broken.
        const val NETWORK_RETRY_ATTEMPTS = 1
        const val NETWORK_RETRY_DELAY_MS = 500L
    }
}

@Serializable
data class UploadModel(
    val uploadUrl: String,
    val errorCode: Int? = null
)