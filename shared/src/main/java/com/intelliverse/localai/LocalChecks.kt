package com.intelliverse.localai

import ai.localstudio.sdk.CheckResult
import ai.localstudio.sdk.LocalCapability
import android.content.Context
import android.os.Build
import ai.localstudio.app.llama.LlamaBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One question of a device check with a known right answer -- the same
 * questions rmant7/AI asks, so a model checked in both apps is judged the
 * same way. [letters]: compared on letters and digits only (a translation
 * "Bon jour" still carries "bonjour").
 */
data class Probe(
    val title: String,
    val prompt: String,
    val expectAnyOf: List<String>,
    val letters: Boolean = false,
    /** Shown with [prompt], in this order; empty for a text question. */
    val images: List<ProbeImage> = emptyList(),
    /** Each must also be in the answer -- a question about two pictures is answered for both. */
    val alsoExpect: List<String> = emptyList(),
    /** Unload the model (weights and projector) first: the answer comes from a fresh load. */
    val reloadBefore: Boolean = false,
) {
    fun passes(answer: String): Boolean =
        expectAnyOf.any { expected -> contains(answer, expected, letters) } && alsoExpect.all { contains(answer, it, letters = false) }

    private fun contains(answer: String, expected: String, letters: Boolean): Boolean =
        if (letters) lettersOnly(answer).contains(lettersOnly(expected))
        else Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(expected) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(answer)

    private fun lettersOnly(text: String) = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).filter { it.isLetterOrDigit() }
}

/** What one question observed: right or not, what the model said, how long to its first word and in all. */
@Serializable
data class ProbeStep(
    val title: String,
    val passed: Boolean,
    val answer: String? = null,
    val error: String? = null,
    val firstTokenMs: Long? = null,
    val totalMs: Long? = null,
)

/** One capability's check: PASS only when every question was answered right. */
@Serializable
data class CapabilityCheck(val passed: Boolean, val detail: String? = null, val steps: List<ProbeStep> = emptyList())

/**
 * A device check of one model's file: what it found per capability, and
 * what it is evidence for -- these bytes ([sha256]), this phone model, this
 * native runtime, this set of questions. Change any one and it reads STALE.
 */
@Serializable
data class StoredCheck(
    val modelId: String,
    val sha256: String,
    /** The vision projector's bytes when one was installed for the check; null when it ran without one. */
    val projectorSha256: String? = null,
    val device: String,
    val runtime: String,
    val checkVersion: Int,
    val checkedAtEpochMs: Long,
    val results: Map<String, CapabilityCheck>,
    val error: String? = null,
)

/** Where device checks live, and what one says now. */
@Singleton
class LocalChecks @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, "local_checks").apply { mkdirs() }
    private val hashes = context.getSharedPreferences("local_file_hashes", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    val device: String get() = "${Build.MANUFACTURER} ${Build.MODEL}"
    /** The native runtime: llama.cpp build and rmant7/AI's JNI revision -- the same words rmant7/AI uses -- and the CPU build loaded. */
    val runtime: String get() = "$RUNTIME_VERSION (${LlamaBridge.loadedLibrary ?: "unavailable"})"

    fun stored(modelId: String): StoredCheck? = runCatching {
        File(dir, "$modelId.json").takeIf { it.isFile }?.let { json.decodeFromString(StoredCheck.serializer(), it.readText()) }
    }.getOrNull()

    fun record(check: StoredCheck) {
        File(dir, "${check.modelId}.json").writeText(json.encodeToString(StoredCheck.serializer(), check))
    }

    fun forget(modelId: String) {
        File(dir, "$modelId.json").delete()
    }

    /**
     * Why [check] no longer applies to [file] here -- other bytes (or bytes
     * not identified since they changed), another phone, another runtime,
     * other questions; null while it holds.
     */
    fun staleReason(check: StoredCheck, file: File, projector: File? = null): String? = when {
        knownSha256(file) != check.sha256 -> "the model file changed since it was checked"
        (projector?.let(::knownSha256)) != check.projectorSha256 ->
            if (check.projectorSha256 == null) "its vision part was added since it was checked" else "its vision part changed since it was checked"
        check.device != device -> "checked on another phone: ${check.device}"
        check.runtime != runtime -> "the runtime changed: ${check.runtime} -> $runtime"
        check.checkVersion != CHECK_VERSION -> "the questions changed"
        else -> null
    }

    /** What a check of [modelId] says now for each capability it covers: PASS/FAIL while it holds, STALE once it does not. */
    fun results(modelId: String, file: File, projector: File? = null): Map<LocalCapability, CheckResult> {
        val check = stored(modelId) ?: return emptyMap()
        val stale = staleReason(check, file, projector) != null
        return check.results.mapNotNull { (cap, result) ->
            val capability = runCatching { LocalCapability.valueOf(cap) }.getOrNull() ?: return@mapNotNull null
            capability to when {
                stale -> CheckResult.STALE
                result.passed -> CheckResult.PASS
                else -> CheckResult.FAIL
            }
        }.toMap()
    }

    /** [file]'s sha256 when it was hashed as it is now (same size and modification time); null otherwise. */
    fun knownSha256(file: File): String? {
        val stored = hashes.getString(file.absolutePath, null) ?: return null
        val (size, modified, sha) = stored.split('|').takeIf { it.size == 3 } ?: return null
        return sha.takeIf { file.isFile && file.length().toString() == size && file.lastModified().toString() == modified }
    }

    /** [file]'s sha256, read through once unless already [knownSha256]. */
    fun sha256(file: File): String {
        knownSha256(file)?.let { return it }
        val size = file.length()
        val modified = file.lastModified()
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (file.length() == size && file.lastModified() == modified) hashes.edit().putString(file.absolutePath, "$size|$modified|$sha").apply()
        return sha
    }

    companion object {
        /** The llama.cpp release llama-runtime/src/main/cpp/CMakeLists.txt builds. */
        const val LLAMA_CPP_TAG = "b10448"

        /** What the engine's RAM figures and every check are tied to: another runtime is another figure, another check. */
        val RUNTIME_VERSION: String get() = "llama.cpp $LLAMA_CPP_TAG / jni ${LlamaBridge.JNI_REVISION}"

        /** Bumped whenever the questions change: older checks then read STALE. 2: run on rmant7/AI's engine. */
        const val CHECK_VERSION = 2

        val TEXT: List<Probe> = listOf(
            Probe("What is the capital of France?", "What is the capital of France? Answer with one word.", listOf("Paris")),
            Probe("What is 7 + 5?", "What is 7 + 5? Answer with the number only.", listOf("12", "twelve")),
        )

        /**
         * The life of a vision model on one phone, in order, each with an
         * answer that is not a matter of opinion -- rmant7/AI's own steps:
         * one picture, another, two in one turn, text after them, then
         * unload everything and one picture again from a fresh load. Every
         * step is asked even after a wrong one, so the report shows which
         * part of seeing fails.
         */
        val VISION: List<Probe> = listOf(
            Probe("image: the digit 7", "What digit is shown in this image? Answer with the digit only.", listOf("7", "seven"), images = listOf(ProbeImage.Digit(7))),
            Probe("image: a red circle", "What color is the circle in this image? Answer with one word.", listOf("red"), images = listOf(ProbeImage.Disc(ProbeImage.Disc.RED))),
            Probe(
                "two images in one turn: the digit 4, a blue circle",
                "There are two images. What digit is in the first image, and what color is the circle in the second? Answer briefly.",
                listOf("4", "four"),
                images = listOf(ProbeImage.Digit(4), ProbeImage.Disc(ProbeImage.Disc.BLUE)),
                alsoExpect = listOf("blue"),
            ),
            Probe("text after the images: 7 + 5", "What is 7 + 5? Answer with the number only.", listOf("12", "twelve")),
            Probe(
                "image after unloading and reloading the model: the digit 3",
                "What digit is shown in this image? Answer with the digit only.",
                listOf("3", "three"),
                images = listOf(ProbeImage.Digit(3)),
                reloadBefore = true,
            ),
        )

        /** English to French: (sentence, a word the French must contain). */
        val TRANSLATION_PAIRS: List<Pair<String, String>> = listOf(
            "Good morning, my friend." to "bonjour",
            "Thank you very much." to "merci",
            "Where is the train station?" to "gare",
        )
    }
}
