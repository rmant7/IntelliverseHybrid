package com.example.shared.data.network.gigachat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GigaChatTokenResponse(val access_token: String)

/** See the doc comment where this is thrown, in [GigaChatTokenProvider.fetchToken]. */
class GigaChatOAuthException(val status: Int, val body: String) : Exception(
    "GigaChat OAuth token request failed (HTTP $status): ${body.take(500)}",
)

/**
 * GigaChat does not take a static bearer key directly — what a user (or a
 * bundled `local.properties` key) actually has is an OAuth client-credentials
 * "authorization key" (base64 `client_id:client_secret`), which has to be
 * exchanged for a short-lived access token on a completely different host
 * than the chat API itself before every real request.
 *
 * Not verified end to end against GigaChat's live API — this repo's own
 * build/dev environment cannot reach `devices.sberbank.ru` at all (blocked
 * at the network level), so this is implemented straight from GigaChat's
 * published API reference, not from a working test run. Two things worth
 * confirming on a real device: the exact token lifetime (documented as
 * "valid ~30 minutes," which [TOKEN_LIFETIME_MS] deliberately undercuts),
 * and — separately, in [GigaChatUseCase] — that GigaChat's servers are
 * reachable at all without installing Russia's national "Минцифры" root CA,
 * which is not present in Android's default trust store.
 */
@Singleton
class GigaChatTokenProvider @Inject constructor() {

    private val scope: String = "GIGACHAT_API_PERS"
    private data class CachedToken(val accessToken: String, val fetchedAtMs: Long)

    // Keyed by the raw authorization key, not a single slot: a bundled key
    // pool can hold more than one GigaChat authorization key, each needing
    // its own independently-cached token.
    private val cache = ConcurrentHashMap<String, CachedToken>()

    suspend fun token(authorizationKey: String): String {
        val key = authorizationKey.trim()
        val now = System.currentTimeMillis()
        cache[key]?.let { cached ->
            if (now - cached.fetchedAtMs < TOKEN_LIFETIME_MS) return cached.accessToken
        }
        val fetched = fetchToken(key)
        cache[key] = CachedToken(fetched, now)
        return fetched
    }

    private suspend fun fetchToken(authorizationKey: String): String = withContext(Dispatchers.IO) {
        require(runCatching { java.util.Base64.getDecoder().decode(authorizationKey) }.isSuccess) {
            "This doesn't look like a GigaChat Authorization key (not valid Base64). " +
                "Re-copy it from \"Получить ключ\" / Get Key in the Sber developer console — " +
                "not the Client ID or Client Secret shown next to it."
        }
        val connection = (URI.create(OAUTH_URL).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "IntelliverseHybrid-Android")
            // Required by GigaChat's own API — a fresh uuid4 per request.
            setRequestProperty("RqUID", UUID.randomUUID().toString())
            setRequestProperty("Authorization", "Basic $authorizationKey")
            // The actual fix for this connection's stale-pooled-connection
            // SocketException is IntelliverseApplication.onCreate() setting
            // "http.keepAlive" to false JVM-wide -- see its own comment.
            // This header alone was confirmed on a real device NOT to be
            // enough (it only stops pooling this connection afterward, not
            // reusing an already-stale one when opening it), but it's kept
            // as it's still the semantically correct thing to tell the
            // server for a connection this class never intends to reuse.
            setRequestProperty("Connection", "close")
        }
        connection.outputStream.use { it.write("scope=$scope".toByteArray(Charsets.UTF_8)) }

        val status = connection.responseCode
        val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
        connection.disconnect()

        if (status !in 200..299) throw GigaChatOAuthException(status, body)
        Json { ignoreUnknownKeys = true }.decodeFromString(GigaChatTokenResponse.serializer(), body).access_token
    }

    private companion object {
        const val OAUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"

        // GigaChat documents the token as valid for roughly 30 minutes.
        // Refreshing at 20 rather than trusting that number to the minute
        // costs one extra token fetch per half hour of active use at worst,
        // against risking a request failing on an already-expired token.
        const val TOKEN_LIFETIME_MS = 20 * 60 * 1000L
    }
}
