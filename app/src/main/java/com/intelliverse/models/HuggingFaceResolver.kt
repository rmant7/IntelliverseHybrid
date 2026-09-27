package com.intelliverse.models

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

/** One resolvable file on a HuggingFace repo: its direct download URL and size. */
data class RemoteArtifact(val downloadUrl: String, val sizeBytes: Long, val fileName: String)

/**
 * Resolves a HuggingFace repoId into a concrete downloadable GGUF file --
 * ported from rmant7/AI's own HuggingFaceResolver.kt plus its :core
 * ArtifactResolver.pickBest() quant-priority matching, collapsed into one
 * class since this app has no separate :core registry module.
 */
class HuggingFaceResolver {

    private val defaultQuantPriority =
        listOf("Q4_K_M", "Q4_K_S", "Q4_0", "IQ4_XS", "Q3_K_M", "Q5_K_M", "Q8_0")

    /** Tries each repoId in order, returns the first that resolves; aggregates failure messages otherwise. */
    fun resolveAny(repoIds: List<String>, quantPriority: List<String>?): Result<RemoteArtifact> {
        val failures = mutableListOf<String>()
        for (repoId in repoIds) {
            resolveRepo(repoId, quantPriority)
                .onSuccess { return Result.success(it) }
                .onFailure { failures += "$repoId: ${it.message}" }
        }
        return Result.failure(IllegalStateException("No repo resolved: ${failures.joinToString("; ")}"))
    }

    private fun resolveRepo(repoId: String, quantPriority: List<String>?): Result<RemoteArtifact> {
        var attempt = 0
        while (true) {
            attempt++
            try {
                val url = URL("https://huggingface.co/api/models/$repoId/tree/main?recursive=false")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val tree = JSONArray(body)

                val candidates = mutableListOf<RemoteArtifact>()
                for (i in 0 until tree.length()) {
                    val entry = tree.getJSONObject(i)
                    val path = entry.optString("path")
                    if (!path.endsWith(".gguf", ignoreCase = true)) continue
                    // Exclude multi-part splits (-00001-of-00005.gguf): this
                    // resolver only handles a single downloadable file.
                    if (Regex("-\\d{5}-of-\\d{5}\\.").containsMatchIn(path)) continue
                    val lfs = entry.optJSONObject("lfs")
                    val size = lfs?.optLong("size") ?: entry.optLong("size")
                    candidates += RemoteArtifact(
                        downloadUrl = "https://huggingface.co/$repoId/resolve/main/$path",
                        sizeBytes = size,
                        fileName = path,
                    )
                }
                if (candidates.isEmpty()) return Result.failure(IllegalStateException("No .gguf file found in $repoId"))

                val priority = quantPriority ?: defaultQuantPriority
                val best = priority.firstNotNullOfOrNull { quant ->
                    candidates.firstOrNull { it.fileName.contains(quant, ignoreCase = true) }
                } ?: candidates.minByOrNull { it.sizeBytes }!!
                return Result.success(best)
            } catch (e: UnknownHostException) {
                if (attempt >= 3) return Result.failure(e)
                Thread.sleep(2_000)
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }
    }
}
