package com.intelliverse.localai

import ai.localstudio.sdk.CheckResult
import ai.localstudio.sdk.GenerationOptions
import ai.localstudio.sdk.InstallProgress
import ai.localstudio.sdk.LocalAi
import ai.localstudio.sdk.LocalAiException
import ai.localstudio.sdk.LocalAiInput
import ai.localstudio.sdk.LocalCapability
import ai.localstudio.sdk.LocalModel
import ai.localstudio.sdk.LocalModelDiscovery
import ai.localstudio.sdk.ModelCandidate
import ai.localstudio.sdk.ModelSource
import ai.localstudio.sdk.TranslationRequest
import android.content.Context
import com.example.shared.log.AppLog
import com.intelliverse.llama.LocalLlamaSession
import com.intelliverse.llama.TranslationPrompts
import com.intelliverse.models.ChatModels
import com.intelliverse.models.LocalModelCatalog
import com.intelliverse.models.LocalModelSeed
import com.intelliverse.models.ModelPurpose
import com.intelliverse.models.ModelStore
import com.intelliverse.models.TranslationModels
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A check running right now: which model, and which question of how many. */
data class RunningCheck(val modelId: String, val question: Int, val questions: Int)

/**
 * [LocalAi] -- rmant7/AI's SDK contract -- backed by this app's own catalog
 * ([LocalModelCatalog]), downloads and its one-model llama.cpp session
 * ([LocalLlamaSession]). Everything in this app that runs a local model
 * goes through here, so one model is loaded at a time and a request never
 * finds another model swapped in under it ([operation]).
 *
 * Not here yet: images (no projector downloads in this app), discovery of
 * new models (the catalog is fixed), and a RAM manager -- a model that
 * does not fit fails to load and says so.
 */
@Singleton
class IntelliverseLocalAi @Inject constructor(
    @ApplicationContext context: Context,
    private val session: LocalLlamaSession,
    private val settings: LocalAiSettings,
    private val checks: LocalChecks,
    private val log: AppLog,
) : LocalAi {

    private val store = ModelStore(context)

    /** Load + answer as one step: nothing else loads a model in between. */
    private val operation = Mutex()

    private val _runningCheck = MutableStateFlow<RunningCheck?>(null)

    /** The device check in progress, for the Models screen. */
    val runningCheck: StateFlow<RunningCheck?> = _runningCheck

    // ── Models ───────────────────────────────────────────────────────────

    override suspend fun models(): List<LocalModel> = installed().map { seed ->
        LocalModel(
            id = seed.id,
            displayName = seed.title,
            capabilities = capabilitiesOf(seed),
            verified = checks.results(seed.id, store.finalFile(seed)),
            sizeBytes = store.finalFile(seed).length(),
            source = ModelSource.CATALOG,
        )
    }

    fun installed(): List<LocalModelSeed> = LocalModelCatalog.ALL.filter { store.isInstalled(it) }

    fun fileOf(seed: LocalModelSeed): File = store.finalFile(seed)

    /** What [seed] can be asked: chat models take text and translate; translation models only translate. */
    fun capabilitiesOf(seed: LocalModelSeed): Set<LocalCapability> = buildSet {
        if (ModelPurpose.CHAT in seed.purposes && !seed.isT5EncoderDecoder) add(LocalCapability.TEXT)
        if (ModelPurpose.TRANSLATION in seed.purposes) add(LocalCapability.TRANSLATION)
    }

    /**
     * The model a request without an id goes to: the one chosen for
     * [purpose] when it is still installed, else the first installed one
     * that can do it (dedicated translation models first for translation).
     */
    fun defaultFor(purpose: ModelPurpose): LocalModelSeed? {
        val chosen = when (purpose) {
            ModelPurpose.CHAT -> settings.chatModelId
            ModelPurpose.TRANSLATION -> settings.translationModelId
        }?.let(LocalModelCatalog::byId)
        if (chosen != null && purpose in chosen.purposes && store.isInstalled(chosen)) return chosen
        val order = if (purpose == ModelPurpose.TRANSLATION) TranslationModels.ALL + ChatModels.ALL else ChatModels.ALL
        return order.firstOrNull { purpose in it.purposes && store.isInstalled(it) }
    }

    private fun resolve(purpose: ModelPurpose, modelId: String?): LocalModelSeed {
        val capability = if (purpose == ModelPurpose.CHAT) LocalCapability.TEXT else LocalCapability.TRANSLATION
        if (modelId == null) return defaultFor(purpose) ?: throw LocalAiException.NoModel(capability)
        val seed = LocalModelCatalog.byId(modelId)?.takeIf { store.isInstalled(it) } ?: throw LocalAiException.UnknownModel(modelId)
        if (capability !in capabilitiesOf(seed)) throw LocalAiException.Failed("${seed.title} cannot be asked for ${capability.name.lowercase()}")
        return seed
    }

    // ── Asking ───────────────────────────────────────────────────────────

    override fun generate(input: LocalAiInput, options: GenerationOptions, modelId: String?): Flow<String> = flow {
        val seed = resolve(ModelPurpose.CHAT, modelId)
        if (input.images.isNotEmpty()) throw LocalAiException.ImageNotSeen("${seed.title} cannot see images in this app")
        operation.withLock {
            load(seed)
            try {
                withTimeout(options.timeoutMs) {
                    session.generate(
                        prompt = input.text,
                        maxTokens = options.maxTokens,
                        temperature = options.temperature.toFloat(),
                        repeatPenalty = CHAT_REPEAT_PENALTY,
                        systemPrompt = input.systemPrompt,
                    ).collect { emit(it) }
                }
            } catch (e: TimeoutCancellationException) {
                throw LocalAiException.Timeout(options.timeoutMs)
            }
        }
    }

    override suspend fun translate(request: TranslationRequest, modelId: String?): String {
        val seed = resolve(ModelPurpose.TRANSLATION, modelId)
        val prompt = TranslationPrompts.buildPrompt(seed, request.target.code, request.target.name, request.text)
        val reply = operation.withLock {
            load(seed)
            answer(prompt, TRANSLATION_TIMEOUT_MS)
        }
        return finalAnswer(reply)?.takeIf { it.isNotBlank() }
            ?: throw LocalAiException.Failed("${seed.title} gave no translation" + if (finalAnswer(reply) == null) " (still reasoning when the reply ended)" else "")
    }

    /** Loads [seed] unless it is the one loaded; must hold [operation]. */
    private suspend fun load(seed: LocalModelSeed) {
        val file = store.finalFile(seed)
        if (session.loadedModelPath == file.absolutePath) return
        val started = System.currentTimeMillis()
        val ok = withContext(Dispatchers.IO) { session.load(file.absolutePath, seed.contextTokens) }
        if (!ok) {
            log.record("LOCAL_AI", "${seed.id}: load FAILED (${file.length() / 1_000_000} MB)")
            throw LocalAiException.Failed("${seed.title} could not be loaded -- most likely not enough free memory")
        }
        log.record("LOCAL_AI", "${seed.id}: loaded in ${System.currentTimeMillis() - started} ms")
    }

    /** The whole reply to [prompt] at near-greedy settings; must hold [operation]. */
    private suspend fun answer(prompt: String, timeoutMs: Long, onFirstToken: () -> Unit = {}): String = try {
        withTimeout(timeoutMs) {
            val reply = StringBuilder()
            session.generate(prompt, maxTokens = ANSWER_MAX_TOKENS, temperature = 0f).collect {
                if (reply.isEmpty()) onFirstToken()
                reply.append(it)
            }
            reply.toString()
        }
    } catch (e: TimeoutCancellationException) {
        throw LocalAiException.Timeout(timeoutMs)
    }

    // ── Checking ─────────────────────────────────────────────────────────

    /**
     * Asks [modelId] the check's questions now -- chat (unless it only
     * translates) and translation -- and records what it answered, for its
     * file's exact bytes on this phone with this runtime.
     */
    override suspend fun verify(modelId: String): Map<LocalCapability, CheckResult> {
        val seed = LocalModelCatalog.byId(modelId)?.takeIf { store.isInstalled(it) } ?: throw LocalAiException.UnknownModel(modelId)
        val file = store.finalFile(seed)
        val suites = buildMap {
            if (LocalCapability.TEXT in capabilitiesOf(seed)) put(LocalCapability.TEXT, LocalChecks.TEXT)
            put(
                LocalCapability.TRANSLATION,
                LocalChecks.TRANSLATION_PAIRS.map { (sentence, expected) ->
                    Probe("EN→FR \"$sentence\"", TranslationPrompts.buildPrompt(seed, "fr", "French", sentence), listOf(expected), letters = true)
                },
            )
        }
        val total = suites.values.sumOf { it.size }
        operation.withLock {
            _runningCheck.value = RunningCheck(seed.id, 0, total)
            try {
                log.record("LOCAL_AI", "${seed.id}: check started ($total questions)")
                val sha = withContext(Dispatchers.IO) { checks.sha256(file) }
                var asked = 0
                var error: String? = null
                val results = linkedMapOf<String, CapabilityCheck>()
                try {
                    load(seed)
                } catch (e: LocalAiException) {
                    error = e.message
                }
                if (error == null) {
                    for ((capability, probes) in suites) {
                        val steps = mutableListOf<ProbeStep>()
                        var failure: String? = null
                        for (probe in probes) {
                            _runningCheck.value = RunningCheck(seed.id, ++asked, total)
                            val askedAt = System.currentTimeMillis()
                            var firstAt = 0L
                            val step = try {
                                val reply = answer(probe.prompt, CHECK_QUESTION_TIMEOUT_MS) { firstAt = System.currentTimeMillis() }
                                val final = finalAnswer(reply)
                                val passed = final != null && final.isNotBlank() && probe.passes(final)
                                ProbeStep(
                                    title = probe.title,
                                    passed = passed,
                                    answer = (final ?: reply).trim().replace('\n', ' ').take(120).ifBlank { null },
                                    error = if (final == null) "still reasoning when the reply ended" else null,
                                    firstTokenMs = if (firstAt > 0) firstAt - askedAt else null,
                                    totalMs = System.currentTimeMillis() - askedAt,
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                ProbeStep(probe.title, passed = false, error = e.message ?: e.javaClass.simpleName, totalMs = System.currentTimeMillis() - askedAt)
                            }
                            steps += step
                            if (!step.passed && failure == null) {
                                failure = "wrong answer to: ${probe.title} (expected ${probe.expectAnyOf.joinToString(" or ")})"
                            }
                            if (!step.passed) break
                        }
                        results[capability.name] = CapabilityCheck(failure == null, failure, steps)
                    }
                }
                val check = StoredCheck(
                    modelId = seed.id,
                    sha256 = sha,
                    device = checks.device,
                    runtime = checks.runtime,
                    checkVersion = LocalChecks.CHECK_VERSION,
                    checkedAtEpochMs = System.currentTimeMillis(),
                    results = results,
                    error = error,
                )
                checks.record(check)
                log.record(
                    "LOCAL_AI",
                    "${seed.id}: check done -- " + (error ?: results.entries.joinToString { (cap, r) -> "$cap ${if (r.passed) "PASS" else "FAIL"}" }),
                )
            } finally {
                _runningCheck.value = null
            }
        }
        return checks.results(seed.id, file)
    }

    // ── Discovery ────────────────────────────────────────────────────────

    /** This app's catalog is fixed: there is nothing to discover yet. */
    override val discovery: LocalModelDiscovery = object : LocalModelDiscovery {
        override suspend fun candidates(): List<ModelCandidate> = emptyList()

        override fun install(candidateId: String): Flow<InstallProgress> =
            flowOf(InstallProgress.Failed("discovering new models is not available in this app yet"))

        override suspend fun verify(candidateId: String): Map<LocalCapability, CheckResult> =
            throw LocalAiException.UnknownModel(candidateId)
    }

    companion object {
        /** Chat sampling's repetition penalty -- 1.2 (translation's) makes a chat model avoid words it needs. */
        const val CHAT_REPEAT_PENALTY = 1.1f
        const val ANSWER_MAX_TOKENS = 1024
        const val TRANSLATION_TIMEOUT_MS = 5 * 60_000L
        const val CHECK_QUESTION_TIMEOUT_MS = 5 * 60_000L

        /**
         * The answer in [reply]: after the last `</think>`, or all of it when
         * it never opened one; null when a `<think>` was opened and never
         * closed -- the model was still reasoning when the reply ended.
         */
        fun finalAnswer(reply: String): String? {
            val close = reply.lastIndexOf("</think>")
            return when {
                close >= 0 -> reply.substring(close + "</think>".length).trim()
                reply.contains("<think>") -> null
                else -> reply.trim()
            }
        }
    }
}
