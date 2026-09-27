package com.example.shared.domain.usecases.ai

import com.example.shared.data.keys.ApiKeyRotator
import com.example.shared.data.keys.ApiProviderIds
import org.json.JSONArray
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
 * ...); when it works, [resolveModelCandidates] ranks the account's own
 * models by [VISION_HINT_REGEX] and tries all of them in that order,
 * skipping one that comes back unavailable (see
 * [SKIPPABLE_MODEL_ERROR_CODES]) in favor of the next, rather than treating
 * one bad guess as a permanent failure for the whole provider. Vision-hinted
 * models are ranked first since this app's core flows are photo-driven
 * (diet photos, homework photos, ...) -- Groq's own docs
 * (console.groq.com/docs/vision) confirm its gpt-oss reasoning models do not
 * accept image input at all.
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
     * The account's own model catalog, ranked vision-hinted-first, or
     * [FALLBACK_MODEL_CANDIDATES] if the discovery call itself fails.
     * Cached per API key for the process lifetime -- a model catalog does
     * not change within one app session, and this avoids paying an extra
     * HTTP round trip on every single Groq call.
     */
    private fun resolveModelCandidates(apiKey: String): List<String> =
        modelCacheByKey.getOrPut(apiKey) {
            val discovered = fetchAccountModelIds(apiKey)
            if (discovered.isNullOrEmpty()) {
                FALLBACK_MODEL_CANDIDATES
            } else {
                discovered.sortedByDescending { VISION_HINT_REGEX.containsMatchIn(it) }
            }
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

    /** Groq's `/chat/completions` request body -- images (if any) as inline `data:` URIs, no external hosting required. */
    private fun buildRequestBody(
        modelName: String,
        systemInstruction: String,
        prompt: String,
        imagesBase64: List<String>,
    ): String {
        val messages = JSONArray()
        if (systemInstruction.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
        }
        val userContent: Any = if (imagesBase64.isEmpty()) {
            prompt
        } else {
            val parts = JSONArray()
            imagesBase64.forEach { base64Data ->
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64Data"))
                )
            }
            parts.put(JSONObject().put("type", "text").put("text", prompt))
            parts
        }
        messages.put(JSONObject().put("role", "user").put("content", userContent))

        return JSONObject()
            .put("model", modelName)
            // Without this, Groq applies its own per-model server-side
            // default -- confirmed too small via a real device log: a
            // genuine, successful response (a multi-day trip itinerary, for
            // OneClickTrip) got cut off mid-JSON, failing to decode with
            // "Expected end of the object '}', but had 'EOF' instead". 8192
            // comfortably covers this app's largest structured response
            // shape with room to spare.
            .put("max_tokens", 8192)
            .put("messages", messages)
            .toString()
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

        val modelCandidates = resolveModelCandidates(apiKey)
        var lastFailure: Throwable? = null
        for (modelName in modelCandidates) {
            var networkRetriesLeft = NETWORK_RETRY_ATTEMPTS
            while (true) {
                val requestBody = buildRequestBody(modelName, systemInstruction, prompt, imagesBase64)
                try {
                    val response = postChatCompletion(apiKey, requestBody)

                    if (response.status !in 200..299) {
                        if (isModelUnavailable(response.body)) {
                            // Expected/handled, not a real error -- the next
                            // candidate is tried immediately. A one-line
                            // note, not a full stack trace, keeps this from
                            // flooding the Log screen every time Groq's
                            // catalogue drifts under an already-broken model.
                            Timber.w("Groq model $modelName not accessible with this key, trying next candidate")
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
                        Timber.w("Groq model $modelName failed: HTTP ${response.status} -- ${response.body.take(300)}")
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
                        Timber.w("Groq model $modelName returned no parseable content: ${response.body.take(300)}")
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
                    Timber.w(e, "Groq model $modelName failed")
                    return Result.failure(e)
                }
            }
        }
        // Every candidate came back unavailable (not found, decommissioned,
        // ...) -- this API key's account has access to none of them, not a
        // transient issue a retry would fix. Surfaced as its own message
        // (rather than just the last model's raw error) so the Log screen
        // shows this is a full-list exhaustion, not one model's ordinary
        // hiccup.
        return Result.failure(
            IllegalStateException(
                "None of Groq's candidate models (${modelCandidates.joinToString()}) " +
                    "are accessible with this API key",
                lastFailure,
            )
        )
    }

    private companion object {
        const val BASE_URL = "https://api.groq.com/openai/v1"

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

        // Heuristic only: Groq's /models response has no explicit
        // vision-capability field, so this ranks by naming convention
        // (current and past vision-capable Groq models all contain one of
        // these -- "qwen" per rmant7/AI's own confirmed-working
        // qwen/qwen3.6-27b). A model that matches nothing is still tried --
        // just later in the order -- rather than excluded outright, since a
        // wrong guess here should degrade to "tried later," never "never
        // tried".
        val VISION_HINT_REGEX = Regex("vision|llama-4|scout|maverick|qwen|-vl-|vl$", RegexOption.IGNORE_CASE)

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
