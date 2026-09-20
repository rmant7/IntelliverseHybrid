package com.example.shared.data.keys

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class StoredBundledKey(val id: String, val key: String, val cooldownUntilEpochMs: Long = 0L)

@Serializable
private data class BundledKeyPools(val pools: Map<String, List<StoredBundledKey>> = emptyMap())

/**
 * Cooldown state for keys baked into the build itself via `local.properties`
 * (see [BundledApiKeys]), in a file of its own rather than
 * [PrefsApiKeyStore]'s: these keys were never typed in by the user and
 * should never appear in a future "your API keys" settings list, which reads
 * and writes that other file — only [ApiKeyRotator]'s own bundled-fallback
 * path ever touches this one.
 */
@Singleton
class BundledApiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : ApiKeyStore {

    private val file = File(context.filesDir, "bundled-api-key-pools.json")
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(providerId: String): List<ApiKeyEntry> =
        readPools()[providerId]?.map { ApiKeyEntry(it.id, it.key, it.cooldownUntilEpochMs) }.orEmpty()

    override fun save(providerId: String, entries: List<ApiKeyEntry>) {
        val pools = readPools().toMutableMap()
        pools[providerId] = entries.map { StoredBundledKey(it.id, it.key, it.cooldownUntilEpochMs) }
        writePools(pools)
    }

    /**
     * Reconciles this store's pool for [providerId] against [bundledKeys]
     * (the keys currently baked into the build via `local.properties`):
     * adds newly-bundled keys, drops ones no longer bundled, and — this is
     * the part that matters — leaves an unchanged key's cooldown exactly as
     * it was, so a key already serving a rate-limit pause does not get
     * retried immediately just because the app restarted.
     */
    fun sync(providerId: String, bundledKeys: List<String>) {
        val existing = load(providerId)
        if (bundledKeys.isEmpty()) {
            if (existing.isNotEmpty()) save(providerId, emptyList())
            return
        }
        val reconciled = bundledKeys.map { key ->
            existing.firstOrNull { it.key == key } ?: ApiKeyEntry(id = UUID.randomUUID().toString(), key = key)
        }
        if (reconciled != existing) save(providerId, reconciled)
    }

    private fun readPools(): Map<String, List<StoredBundledKey>> = runCatching {
        if (!file.isFile) return emptyMap()
        json.decodeFromString(BundledKeyPools.serializer(), file.readText()).pools
    }.getOrElse { emptyMap() }

    private fun writePools(pools: Map<String, List<StoredBundledKey>>) {
        file.writeText(json.encodeToString(BundledKeyPools.serializer(), BundledKeyPools(pools)))
    }
}
