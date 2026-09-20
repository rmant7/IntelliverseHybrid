package com.example.shared.data.keys

import com.example.shared.BuildConfig

/**
 * Provider ids used consistently across [BundledApiKeys], every
 * `ApiKeyRotator`, and each provider's own client (`GeminiApiService`,
 * `GrokUseCase`, `GigaChatUseCase`) — one id per model family, so a new
 * model added under an existing provider (e.g. another Gemini or Grok model)
 * never needs a new id or a new `local.properties` key of its own.
 */
object ApiProviderIds {
    const val GEMINI = "gemini"
    const val GROK = "grok"
    const val GIGACHAT = "gigachat"
}

/**
 * Keys baked into this build from `local.properties` (`<provider>_api_key`,
 * e.g. `gemini_api_key`) via [BuildConfig] — see each module's
 * `build.gradle.kts`.
 *
 * Each property accepts either a single key or a comma-separated list —
 * one name whether a provider is run with one key today or rotated across
 * several later, so switching to rotation for a provider that already has a
 * single key configured never means renaming its `local.properties` entry.
 */
object BundledApiKeys {

    private fun rawFor(providerId: String): String = when (providerId) {
        ApiProviderIds.GEMINI -> BuildConfig.gemini_api_key
        ApiProviderIds.GROK -> BuildConfig.grok_api_key
        ApiProviderIds.GIGACHAT -> BuildConfig.gigachat_api_key
        else -> ""
    }

    fun forProvider(providerId: String): List<String> =
        rawFor(providerId).split(",").map { it.trim() }.filter { it.isNotBlank() && it != "null" }

    /** Reconciles [store]'s pool for [providerId] against the compiled-in key list. See [BundledApiKeyStore.sync]. */
    fun sync(store: BundledApiKeyStore, providerId: String) {
        store.sync(providerId, forProvider(providerId))
    }
}
