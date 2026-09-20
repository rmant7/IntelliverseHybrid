package com.example.shared.data.keys

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class StoredKey(val id: String, val key: String, val cooldownUntilEpochMs: Long = 0L)

@Serializable
private data class KeyPools(val pools: Map<String, List<StoredKey>> = emptyMap())

/**
 * Persists each provider's user-added key pool to a flat JSON file — a
 * provider's pool is an ordered list, which plain SharedPreferences has no
 * native way to store.
 *
 * Empty until a future "API keys" settings screen lets the user add keys of
 * their own; until then, [ApiKeyRotator] falls straight through to
 * [BundledApiKeyStore] for every provider.
 */
@Singleton
class PrefsApiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : ApiKeyStore {

    private val file = File(context.filesDir, "api-key-pools.json")
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(providerId: String): List<ApiKeyEntry> =
        readPools()[providerId]?.map { ApiKeyEntry(it.id, it.key, it.cooldownUntilEpochMs) }.orEmpty()

    override fun save(providerId: String, entries: List<ApiKeyEntry>) {
        val pools = readPools().toMutableMap()
        pools[providerId] = entries.map { StoredKey(it.id, it.key, it.cooldownUntilEpochMs) }
        writePools(pools)
    }

    private fun readPools(): Map<String, List<StoredKey>> = runCatching {
        if (!file.isFile) return emptyMap()
        json.decodeFromString(KeyPools.serializer(), file.readText()).pools
    }.getOrElse { emptyMap() }

    private fun writePools(pools: Map<String, List<StoredKey>>) {
        file.writeText(json.encodeToString(KeyPools.serializer(), KeyPools(pools)))
    }
}
