package com.example.shared.domain.usecases.ai

import com.example.shared.data.keys.ApiKeyRotator
import com.example.shared.data.keys.ApiProviderIds
import com.example.shared.domain.ai.EnforcementLadder
import com.example.shared.domain.ai.FormatSupportErrors
import com.example.shared.domain.ai.GroqModelPolicy
import com.example.shared.domain.ai.ResponseFormat
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named

/** Groq's own `{"error": {...}}` JSON body preserved verbatim for diagnostics. */
class GroqHttpException(val status: Int, val body: String) : Exception(
    "Groq returned HTTP $status: ${body.take(300)}"
)

/**
 * Groq (api.groq.com) via its OpenAI-compatible REST endpoint, called
 * directly over [HttpURLConnection] -- not through langchain4j. Dropped
 * entirely from this app: its only other consumer was the old GPT "demo"
 * key integration, which never produced a usable answer (permanently
 * rate-limited -- a shared, worldwide, non-configurable quota), and
 * langchain4j's own RetryUtils.withRetry() silently discarded the real
 * OpenAiHttpException type on every failure, requiring an
 * unwrap-from-.cause workaround here that a direct HTTP call has no need
 * for.
 *
 * Model list is discovered at runtime via Groq's own `GET /models` endpoint
 * for the actual account behind [apiKeyRotator]'s active key, not a hardcoded
 * name. Four different hardcoded guesses in a row -- llama-4-scout,
 * llama-4-maverick, llama-3.2-90b-vision-preview, llama-3.2-11b-vision-preview
 * -- each came back model_not_found or model_decommissioned on a real
 * account, confirmed via this app's own Log screen: Groq's free-tier
 * catalogue drifts faster than this code can be kept in sync from an
 * environment with no network access to api.groq.com to verify against.
 * [FALLBACK_MODEL_CANDIDATES] (the old hardcoded guesses) is only a
 * last-resort if the discovery call itself fails (network error, no key,
 * ...); when it works, [GroqModelPolicy] ranks the account's own models
 * (known vision models first) and they are tried in that order, skipping
 * one that comes back unavailable (see [SKIPPABLE_MODEL_ERROR_CODES]) or,
 * for a request with images, one that rejects image input -- remembered as
 * text-only, not offered images again. Groq's own docs
 * (console.groq.com/docs/vision) confirm most of its models (gpt-oss, ...)
 * do not accept image input at all; a text-only Qwen ranked by name before
 * the vision one used to end the whole Groq attempt with HTTP 400.
 */
class GroqUseCase @Inject constructor(
    @Named(ApiProviderIds.GROQ) private val apiKeyRotator: ApiKeyRotator,
) {
    // TEMPORARY diagnostic: which candidate actually answered, since the
    // model list is now discovered per-account rather than fixed -- read
    // by BaseResultViewModel right after a successful call to append a
    // "provider (model)" footer to the answer. Remove both sides once
    // multi-provider testing no longer needs this visible in the answer
    // itself (the Log screen already records it either way).
    var lastUsedModel: String? = null
        private set

    private fun cleanResult(response: String): String {
        return response.replace(
            Regex("""\\\[(.*?)\\]""", RegexOption.DOT_MATCHES_ALL)
        ) { matchResult -> "$$${matchResult.groupValues[1]}$$" }
    }

    /**
     * The account's own model catalog (ranked per request by
     * [GroqModelPolicy]), or [FALLBACK_MODEL_CANDIDATES] if the discovery
     * call itself fails.
     * Cached per API key for the process lifetime -- a model catalog does
     * not change within one app session, and this avoids paying an extra
     * HTTP round trip on every single Groq call.
     */
    private fun resolveModelCandidates(apiKey: String): List<String> =
        modelCacheByKey.getOrPut(apiKey) {
            val discovered = fetchAccountModelIds(apiKey)
            if (discovered.isNullOrEmpty()) FALLBACK_MODEL_CANDIDATES else discovered
        }

    /** `null` on any failure (network, auth, parse, ...) -- distinguished from "account has zero models". */
    private fun fetchAccountModelIds(apiKey: String): List<String>? {
        val connection = URL("$BASE_URL/models").openConnection() as HttpURLConnection
        return try {
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body).getJSONArray("data")
            List(data.length()) { i -> data.getJSONObject(i).getString("id") }
        } catch (e: Exception) {
            // HttpURLConnection throws a plain FileNotFoundException from
            // .inputStream on ANY non-2xx status, with the real status only
            // reachable via .responseCode/.errorStream -- read it here so
            // "the account's key is rejected outright" (401/403) doesn't
            // look identical to an ordinary network hiccup.
            val status = runCatching { connection.responseCode }.getOrDefault(-1)
            Timber.w(
                e,
                "Failed to fetch Groq's model list for this key (HTTP $status); falling back to the hardcoded list"
            )
            null
        }
    }

    private class HttpResult(val status: Int, val body: String)

    private fun postChatCompletion(apiKey: String, requestBody: String): HttpResult {
        val connection = URL("$BASE_URL/chat/completions").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 90_000
        connection.readTimeout = 90_000
        connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return HttpResult(status, body)
    }

    private fun extractContent(body: String): String? =
        runCatching {
            JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }.getOrNull()

    private fun extractErrorField(body: String, field: String): String? =
        runCatching { JSONObject(body).getJSONObject("error").optString(field).takeIf { it.isNotBlank() } }
            .getOrNull()

    private fun isModelUnavailable(body: String): Boolean =
        extractErrorField(body, "code") in SKIPPABLE_MODEL_ERROR_CODES

    // Confirmed on a real device: "rate_limit_exceeded" on output tokens per
    // minute (OTPM) -- a genuine, short-lived per-minute cap shared across
    // this app's own concurrent calls on the same key, not evidence the
    // model or key is actually broken. Groq's own error message names
    // exactly how long to wait ("Please try again in 5.04s"); parsed here
    // rather than guessing a fixed delay, with a safe fallback if the
    // message format ever changes.
    private fun isRateLimited(status: Int, body: String): Boolean =
        status == 429 || extractErrorField(body, "code") == "rate_limit_exceeded"

    private fun rateLimitRetryDelayMs(body: String): Long {
        val message = extractErrorField(body, "message").orEmpty()
        val seconds = Regex("""try again in ([\d.]+)s""")
            .find(message)
            ?.groupValues?.get(1)?.toDoubleOrNull()
            ?: 5.0
        return ((seconds + 0.5) * 1000).toLong().coerceAtMost(10_000L)
    }

    /**
     * Generate a Groq solution using text and optionally one or more
     * base64-encoded JPEG images. [responseFormat] [ResponseFormat.Json] asks
     * for `json_schema` first; a model that explicitly answers it doesn't
     * support that is asked again on the same request with `json_object`,
     * then without `response_format` (remembered per model) -- any other
     * error is handled exactly as before.
     */
    fun generateGroqSolution(
        imagesBase64: List<String>,
        prompt: String,
        systemInstruction: String = "",
        responseFormat: ResponseFormat = ResponseFormat.Text,
    ): Result<String> {
        val keyEntry = apiKeyRotator.activeKey()
            ?: return Result.failure(
                IllegalStateException(apiKeyRotator.exhaustionMessage() ?: "No Groq API key configured")
            )
        val apiKey = keyEntry.key

        val hasImages = imagesBase64.isNotEmpty()
        val modelCandidates = modelPolicy.candidates(resolveModelCandidates(apiKey), hasImages)
        if (modelCandidates.isEmpty()) {
            return Result.failure(IllegalStateException("No Groq model on this account accepts image input"))
        }
        // Every model tried and why it was passed over -- in the failure
        // itself, so the Log screen shows whether the vision model was reached.
        val attempts = mutableListOf<String>()
        var lastFailure: Throwable? = null
        for (modelName in modelCandidates) {
            var networkRetriesLeft = NETWORK_RETRY_ATTEMPTS
            var enforcement = formatLadder.start(modelName, responseFormat)
            while (true) {
                val requestBody = GroqRequest.body(modelName, systemInstruction, prompt, imagesBase64, responseFormat, enforcement)
                try {
                    val response = postChatCompletion(apiKey, requestBody)

                    if (response.status !in 200..299) {
                        if (FormatSupportErrors.isGroqFormatUnsupported(response.status, response.body)) {
                            val weaker = formatLadder.stepDown(modelName, enforcement)
                            if (weaker != null) {
                                Timber.w("Groq model $modelName does not support $enforcement output, retrying with $weaker")
                                enforcement = weaker
                                continue
                            }
                        }
                        if (hasImages && GroqModelPolicy.isImageInputRejected(response.status, response.body)) {
                            Timber.w("Groq model $modelName does not accept images, trying next candidate")
                            modelPolicy.markTextOnly(modelName)
                            attempts += "$modelName: no image input"
                            lastFailure = GroqHttpException(response.status, response.body)
                            break
                        }
                        if (isModelUnavailable(response.body)) {
                            // Expected/handled, not a real error -- the next
                            // candidate is tried immediately. A one-line
                            // note, not a full stack trace, keeps this from
                            // flooding the Log screen every time Groq's
                            // catalogue drifts under an already-broken model.
                            Timber.w("Groq model $modelName not accessible with this key, trying next candidate")
                            attempts += "$modelName: ${extractErrorField(response.body, "code")}"
                            lastFailure = GroqHttpException(response.status, response.body)
                            break
                        }
                        if (isRateLimited(response.status, response.body) && networkRetriesLeft > 0) {
                            networkRetriesLeft--
                            val delayMs = rateLimitRetryDelayMs(response.body)
                            Timber.w(
                                "Groq model $modelName hit its per-minute rate limit, retrying in " +
                                    "${delayMs}ms ($networkRetriesLeft attempt(s) left)"
                            )
                            Thread.sleep(delayMs)
                            continue
                        }
                        Timber.w("Groq model $modelName failed: HTTP ${response.status} -- ${response.body.take(300)}${triedBefore(attempts)}")
                        // 401/403 alongside 429: consistent with a
                        // rate-limited or otherwise rejected key, not a
                        // per-model problem.
                        if (response.status in setOf(401, 403, 429)) {
                            apiKeyRotator.markExhausted(keyEntry.id)
                        }
                        return Result.failure(GroqHttpException(response.status, response.body))
                    }

                    val content = extractContent(response.body)
                    if (content == null) {
                        Timber.w("Groq model $modelName returned no parseable content: ${response.body.take(300)}${triedBefore(attempts)}")
                        return Result.failure(IllegalStateException("Groq returned no parseable content"))
                    }
                    lastUsedModel = modelName
                    return Result.success(cleanResult(content))
                } catch (e: UnknownHostException) {
                    // A DNS lookup failure says nothing about this model or
                    // key -- a real device log caught "Unable to resolve
                    // host api.groq.com" (a plain transient connectivity
                    // blip) being treated as a hard failure that skipped
                    // trying every other candidate, even though nothing
                    // about the account or model was actually wrong.
                    if (networkRetriesLeft > 0) {
                        networkRetriesLeft--
                        Timber.w("Groq model $modelName hit a transient network error, retrying ($networkRetriesLeft attempt(s) left)")
                        continue
                    }
                    lastFailure = e
                    return Result.failure(e)
                } catch (e: SocketTimeoutException) {
                    if (networkRetriesLeft > 0) {
                        networkRetriesLeft--
                        Timber.w("Groq model $modelName timed out, retrying ($networkRetriesLeft attempt(s) left)")
                        continue
                    }
                    lastFailure = e
                    return Result.failure(e)
                } catch (e: Exception) {
                    Timber.w(e, "Groq model $modelName failed${triedBefore(attempts)}")
                    return Result.failure(e)
                }
            }
        }
        // Every candidate was passed over (not found, decommissioned, no
        // image input) -- not a transient issue a retry would fix. Surfaced
        // as its own message, each model with its reason, so the Log screen
        // shows a full-list exhaustion rather than one model's hiccup.
        return Result.failure(
            IllegalStateException(
                "None of Groq's candidate models could take this request: ${attempts.joinToString("; ")}",
                lastFailure,
            )
        )
    }

    private fun triedBefore(attempts: List<String>): String =
        if (attempts.isEmpty()) "" else " (passed over before it: ${attempts.joinToString("; ")})"

    private companion object {
        const val BASE_URL = "https://api.groq.com/openai/v1"

        /** Per model, for the process lifetime -- see [EnforcementLadder]. */
        val formatLadder = EnforcementLadder()

        /** Per model, for the process lifetime -- which ones rejected images. */
        val modelPolicy = GroqModelPolicy()

        // Last resort only, when GET /models itself fails. qwen/qwen3.6-27b
        // is listed first: it's not a guess, it's the model this same Groq
        // vision integration is confirmed currently working with in the
        // sibling rmant7/AI app's own CloudProviders.kt (which also notes
        // it, alongside the Llama 4 models, as one of the few Groq models
        // that actually accepts image input). The four Llama names after it
        // were each confirmed dead (model_not_found or model_decommissioned)
        // on this account specifically -- kept only so a discovery outage
        // still attempts something instead of failing immediately.
        val FALLBACK_MODEL_CANDIDATES = listOf(
            "qwen/qwen3.6-27b",
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "meta-llama/llama-4-maverick-17b-128e-instruct",
            "llama-3.2-90b-vision-preview",
            "llama-3.2-11b-vision-preview",
        )

        // Groq uses more than one distinct error code for "this model is not
        // usable, stop trying it" -- a real device log caught
        // model_decommissioned (llama-3.2-90b-vision-preview, since retired)
        // stopping the fallback loop the same way model_not_found once did,
        // because only the latter was checked for. Any new code found here
        // in the future almost certainly belongs in this same set rather
        // than being treated as a real failure.
        val SKIPPABLE_MODEL_ERROR_CODES = setOf("model_not_found", "model_decommissioned")

        val modelCacheByKey = ConcurrentHashMap<String, List<String>>()

        // One retry on the same model for a transient network error (DNS,
        // socket timeout) before giving up on it -- these are momentary by
        // definition, not evidence the model or key is bad.
        const val NETWORK_RETRY_ATTEMPTS = 1
    }
}
