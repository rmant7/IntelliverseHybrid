package com.example.shared.domain.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class CooldownEntry(
    val provider: String,
    val model: String,
    val untilEpochMs: Long,
    val consecutiveFailures: Int = 1,
    val lastFailureAtMs: Long = untilEpochMs,
)

@Serializable
private data class CooldownList(val entries: List<CooldownEntry> = emptyList())

/**
 * Which (provider, model) pairs are temporarily not worth trying -- ported
 * from rmant7/AI's ModelCooldownStore, where it was proven on devices.
 * "HTTP 503: this model is currently experiencing high demand" says nothing
 * about the key, so key rotation cannot route around it, and retrying the
 * same overloaded model on every request costs seconds each time.
 *
 * Escalating: a first failure costs a short [INITIAL_COOLDOWN_MS], each
 * consecutive one doubles it up to [MAX_COOLDOWN_MS]; a failure long after
 * the previous one starts over at the short tier (a fresh problem, not the
 * same outage). Kept in a file, so it survives process death.
 */
class ModelCooldownStore(private val file: File, private val clock: () -> Long = System::currentTimeMillis) {

    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun isOnCooldown(provider: String, model: String): Boolean =
        (readAll().firstOrNull { it.provider == provider && it.model == model }?.untilEpochMs ?: 0L) > clock()

    @Synchronized
    fun markFailed(provider: String, model: String): Long {
        val now = clock()
        val existing = readAll().firstOrNull { it.provider == provider && it.model == model }
        val fresh = existing == null || now - existing.lastFailureAtMs > STALE_FAILURE_RESET_MS
        val failures = if (fresh) 1 else existing!!.consecutiveFailures + 1
        val duration = minOf(INITIAL_COOLDOWN_MS shl (failures - 1).coerceAtMost(30), MAX_COOLDOWN_MS)
        writeAll(readAll().filterNot { it.provider == provider && it.model == model } + CooldownEntry(provider, model, now + duration, failures, now))
        return duration
    }

    private fun readAll(): List<CooldownEntry> = runCatching {
        if (!file.isFile) return emptyList()
        json.decodeFromString(CooldownList.serializer(), file.readText()).entries
    }.getOrElse { emptyList() }

    private fun writeAll(entries: List<CooldownEntry>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(CooldownList.serializer(), CooldownList(entries)))
        }
    }

    companion object {
        const val INITIAL_COOLDOWN_MS: Long = 3 * 60 * 1000L
        const val MAX_COOLDOWN_MS: Long = 2 * 60 * 60 * 1000L
        private const val STALE_FAILURE_RESET_MS: Long = MAX_COOLDOWN_MS
    }
}

/**
 * Trying a provider's models in order: the primary first, then its siblings,
 * skipping any on cooldown. A failure that is about *this model* -- [isModelProblem]
 * (overloaded, rate-limited, retired) -- cools it down and moves to the next
 * one; any other failure (a bad request, a decoding problem, no key) is the
 * answer, returned as is: another model would fail the same way.
 *
 * When every model is on cooldown the primary is still tried, so a provider
 * never silently stops answering.
 */
class ModelRotation(
    private val provider: String,
    private val models: List<String>,
    private val cooldowns: ModelCooldownStore,
) {
    fun candidates(): List<String> {
        val available = models.distinct().filterNot { cooldowns.isOnCooldown(provider, it) }
        return available.ifEmpty { models.take(1) }
    }

    inline fun <T> run(
        isModelProblem: (Throwable) -> Boolean,
        onModelProblem: (model: String, failure: Throwable, cooldownMs: Long) -> Unit = { _, _, _ -> },
        attempt: (model: String) -> Result<T>,
    ): Pair<String, Result<T>> {
        var last: Pair<String, Result<T>>? = null
        for (model in candidates()) {
            val result = attempt(model)
            val failure = result.exceptionOrNull() ?: return model to result
            last = model to result
            if (!isModelProblem(failure)) return last
            onModelProblem(model, failure, markFailed(model))
        }
        return last!!
    }

    fun markFailed(model: String): Long = cooldowns.markFailed(provider, model)
}
