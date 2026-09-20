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
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named

/**
 * Groq (api.groq.com) via its OpenAI-compatible endpoint. Groq accepts the
 * exact same request/response shape OpenAI's does at a different base URL,
 * so this is [OpenAiUseCase] with [OpenAiChatModel.Builder.baseUrl] pointed
 * at Groq instead of OpenAI, plus key rotation across [apiKeyRotator]'s pool.
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
        return try {
            val connection = URL("$BASE_URL/models").openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body).getJSONArray("data")
            List(data.length()) { i -> data.getJSONObject(i).getString("id") }
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch Groq's model list for this key; falling back to the hardcoded list")
            null
        }
    }

    private fun isModelUnavailable(e: OpenAiHttpException): Boolean =
        SKIPPABLE_MODEL_ERROR_CODES.any { code -> e.message?.contains(code) == true }

    // A DNS lookup or socket timeout says nothing about this model or key --
    // a real device log caught "Unable to resolve host api.groq.com" (a
    // plain transient connectivity blip, confirmed via the user's own
    // device at the time) being treated as a hard failure that skipped
    // trying every other candidate, even though nothing about the account
    // or model was actually wrong. Walks the cause chain since it's usually
    // wrapped (see httpExceptionOf's own comment on why RetryUtils wraps
    // everything in a plain RuntimeException).
    private fun isTransientNetworkError(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is java.net.UnknownHostException || cause is java.net.SocketTimeoutException) return true
            cause = cause.cause
        }
        return false
    }

    // langchain4j's RetryUtils.withRetry() (which OpenAiChatModel.generate()
    // goes through) never lets the original OpenAiHttpException escape
    // directly: after exhausting its retries it always rethrows
    // `new RuntimeException(originalException)`, discarding the specific
    // type and keeping it only as .cause. A `catch (e: OpenAiHttpException)`
    // clause here previously looked correct but could never actually match
    // -- confirmed by checking RetryUtils' own source, since a real device
    // log kept showing the model-skip logic below never taking effect even
    // after fixing isModelUnavailable() itself.
    private fun httpExceptionOf(e: Throwable): OpenAiHttpException? =
        e as? OpenAiHttpException ?: e.cause as? OpenAiHttpException

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

        val modelCandidates = resolveModelCandidates(apiKey)
        var lastFailure: Throwable? = null
        for (modelName in modelCandidates) {
            var networkRetriesLeft = NETWORK_RETRY_ATTEMPTS
            while (true) {
                val model = OpenAiChatModel.builder()
                    .baseUrl(BASE_URL)
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .timeout(Duration.ofSeconds(90L))
                    // Without this, langchain4j sends no max_tokens field at
                    // all and Groq applies its own per-model server-side
                    // default -- confirmed too small via a real device log: a
                    // genuine, successful response (a multi-day trip
                    // itinerary, for OneClickTrip) got cut off mid-JSON,
                    // failing to decode with "Expected end of the object
                    // '}', but had 'EOF' instead". 8192 comfortably covers
                    // this app's largest structured response shape with room
                    // to spare.
                    .maxTokens(8192)
                    .build()

                try {
                    val response: Response<AiMessage> = if (systemInstruction.isBlank()) {
                        model.generate(userMessage)
                    } else {
                        model.generate(SystemMessage.from(systemInstruction), userMessage)
                    }
                    return Result.success(cleanResult(response.content().text()))
                } catch (e: RuntimeException) {
                    val httpException = httpExceptionOf(e)
                    if (httpException != null && isModelUnavailable(httpException)) {
                        // Expected/handled, not a real error -- the next
                        // candidate is tried immediately. A one-line note,
                        // not the full stack trace every OTHER failure here
                        // gets, keeps this from flooding the Log screen
                        // every time Groq's catalogue drifts under an
                        // already-broken model.
                        Timber.w("Groq model $modelName not accessible with this key, trying next candidate")
                        lastFailure = e
                        break
                    }
                    if (httpException == null && isTransientNetworkError(e) && networkRetriesLeft > 0) {
                        // Says nothing about this model or key -- worth one
                        // more try on the exact same candidate before
                        // treating it as a real failure or moving on.
                        networkRetriesLeft--
                        Timber.w(
                            "Groq model $modelName hit a transient network error, retrying " +
                                "($networkRetriesLeft attempt(s) left)"
                        )
                        continue
                    }
                    Timber.e(e, "Groq model $modelName failed")
                    if (httpException?.code() == 429) {
                        apiKeyRotator.markExhausted(keyEntry.id)
                    }
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
