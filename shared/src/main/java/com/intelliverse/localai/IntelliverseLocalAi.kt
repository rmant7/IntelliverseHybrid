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
import ai.localstudio.app.llama.EngineModel
import ai.localstudio.app.llama.LocalModelEngine
import ai.localstudio.core.model.ImageRef
import ai.localstudio.core.runtime.GenerationRequest
import ai.localstudio.core.runtime.ImageNotSeenException
import ai.localstudio.core.runtime.InsufficientMemoryException
import com.example.shared.log.AppLog
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
 * [LocalAi] -- rmant7/AI's SDK contract -- backed by this app's catalog
 * ([LocalModelCatalog]) and downloads, running models on rmant7/AI's own
 * engine ([LocalModelEngine]): every load admitted against this phone's free
 * RAM and what the model was measured to cost here, idle models evicted
 * first, weights mapped or read into memory per file (Auto), RAM measured on
 * every load. Everything in this app that runs a local model goes through
 * here; requests run one at a time ([operation]).
 *
 * Images go to a model whose vision projector is installed
 * ([ModelStore.hasProjector]); to any other, they fail as ImageNotSeen.
 * Not here yet: discovery of new models (the catalog is fixed).
 */
@Singleton
class IntelliverseLocalAi @Inject constructor(
    @ApplicationContext context: Context,
    private val engine: LocalModelEngine,
    private val settings: LocalAiSettings,
    private val checks: LocalChecks,
    private val log: AppLog,
) : LocalAi {

    private val store = ModelStore(context)

    /** One request at a time: a second model is never loaded next to one still answering. */
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
            verified = checks.results(seed.id, store.finalFile(seed), projectorOf(seed)),
            sizeBytes = store.finalFile(seed).length() + (projectorOf(seed)?.length() ?: 0L),
            source = ModelSource.CATALOG,
        )
    }

    fun installed(): List<LocalModelSeed> = LocalModelCatalog.ALL.filter { store.isInstalled(it) }

    fun fileOf(seed: LocalModelSeed): File = store.finalFile(seed)

    /** [seed]'s vision projector when it is installed; null for a model that cannot see here. */
    fun projectorOf(seed: LocalModelSeed): File? = store.projectorFile(seed).takeIf { store.hasProjector(seed) }

    /** What [seed] can be asked: chat models take text and translate, and see with their projector; translation models only translate. */
    fun capabilitiesOf(seed: LocalModelSeed): Set<LocalCapability> = buildSet {
        if (ModelPurpose.CHAT in seed.purposes && !seed.isT5EncoderDecoder) add(LocalCapability.TEXT)
        if (ModelPurpose.TRANSLATION in seed.purposes) add(LocalCapability.TRANSLATION)
        if (LocalCapability.TEXT in this && store.hasProjector(seed)) add(LocalCapability.VISION)
    }

    /**
     * The model a question with images goes to: the chat model when it can
     * see, else the first installed one that can (smallest first).
     */
    fun defaultSeeing(): LocalModelSeed? =
        defaultFor(ModelPurpose.CHAT)?.takeIf { LocalCapability.VISION in capabilitiesOf(it) }
            ?: ChatModels.ALL.firstOrNull { store.isInstalled(it) && LocalCapability.VISION in capabilitiesOf(it) }

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
        val seed = if (input.images.isNotEmpty() && modelId == null) {
            defaultSeeing() ?: throw LocalAiException.NoModel(LocalCapability.VISION)
        } else {
            resolve(ModelPurpose.CHAT, modelId)
        }
        if (input.images.isNotEmpty() && LocalCapability.VISION !in capabilitiesOf(seed)) {
            throw LocalAiException.ImageNotSeen("${seed.title} has no vision part installed" + if (seed.vision) " -- download it on the Models screen" else "")
        }
        val request = GenerationRequest(
            prompt = input.text,
            systemPrompt = input.systemPrompt,
            images = input.images.map { imageRef(it.bytes, it.mimeType) },
            maxTokens = options.maxTokens,
            temperature = options.temperature,
            repeatPenalty = CHAT_REPEAT_PENALTY,
        )
        operation.withLock {
            try {
                withTimeout(options.timeoutMs) {
                    withModel(seed) { handle -> handle.generate(request).collect { emit(it) } }
                }
            } catch (e: TimeoutCancellationException) {
                throw LocalAiException.Timeout(options.timeoutMs)
            } catch (e: ImageNotSeenException) {
                // No room for the vision part now, or a picture that would not decode: said, never answered as text.
                log.record("LOCAL_AI", "${seed.id}: picture not seen -- ${e.reason}")
                throw LocalAiException.ImageNotSeen(e.reason)
            }
        }
    }

    override suspend fun translate(request: TranslationRequest, modelId: String?): String {
        val seed = resolve(ModelPurpose.TRANSLATION, modelId)
        val prompt = TranslationPrompts.buildPrompt(seed, request.target.code, request.target.name, request.text)
        val reply = operation.withLock { answer(seed, prompt, TRANSLATION_TIMEOUT_MS) }
        return finalAnswer(reply)?.takeIf { it.isNotBlank() }
            ?: throw LocalAiException.Failed("${seed.title} gave no translation" + if (finalAnswer(reply) == null) " (still reasoning when the reply ended)" else "")
    }

    /**
     * Runs [block] with [seed] loaded on the engine -- reused when it is the
     * resident one, else admitted (other idle models evicted first). A model
     * that does not fit here fails as NotEnoughMemory, with the figures.
     */
    private suspend fun <T> withModel(seed: LocalModelSeed, block: suspend (ai.localstudio.core.runtime.TextModelHandle) -> T): T {
        val file = store.finalFile(seed)
        val projector = projectorOf(seed)
        // Its own id with a projector: a copy loaded before the projector arrived is never handed out for an image.
        val id = if (projector != null) "${seed.id}+vision" else seed.id
        return try {
            engine.withModel(EngineModel(id, file, projector = projector, contextLength = seed.contextTokens), seed.contextTokens, block)
        } catch (e: InsufficientMemoryException) {
            log.record("LOCAL_AI", "${seed.id}: not admitted -- ${e.message}")
            throw LocalAiException.NotEnoughMemory(e.requestedBytes, e.budgetBytes)
        } catch (e: ai.localstudio.core.runtime.ModelLoadException) {
            log.record("LOCAL_AI", "${seed.id}: load FAILED -- ${e.message}")
            throw LocalAiException.Failed("${seed.title} could not be loaded: ${e.message}", e)
        }
    }

    /** The whole reply to [prompt] at near-greedy settings, translation's repetition penalty; must hold [operation]. */
    private suspend fun answer(
        seed: LocalModelSeed,
        prompt: String,
        timeoutMs: Long,
        images: List<ImageRef> = emptyList(),
        onFirstToken: () -> Unit = {},
    ): String = try {
        withTimeout(timeoutMs) {
            withModel(seed) { handle ->
                val reply = StringBuilder()
                handle.generate(GenerationRequest(prompt = prompt, images = images, maxTokens = ANSWER_MAX_TOKENS, temperature = 0.0)).collect {
                    if (reply.isEmpty()) onFirstToken()
                    reply.append(it)
                }
                reply.toString()
            }
        }
    } catch (e: TimeoutCancellationException) {
        throw LocalAiException.Timeout(timeoutMs)
    }

    // ── Checking ─────────────────────────────────────────────────────────

    /**
     * Asks [modelId] the check's questions now -- chat (unless it only
     * translates), translation, and images when its projector is installed
     * -- and records what it answered, for its files' exact bytes on this
     * phone with this runtime.
     */
    override suspend fun verify(modelId: String): Map<LocalCapability, CheckResult> {
        val seed = LocalModelCatalog.byId(modelId)?.takeIf { store.isInstalled(it) } ?: throw LocalAiException.UnknownModel(modelId)
        val file = store.finalFile(seed)
        val projector = projectorOf(seed)
        val suites = buildMap {
            if (LocalCapability.TEXT in capabilitiesOf(seed)) put(LocalCapability.TEXT, LocalChecks.TEXT)
            put(
                LocalCapability.TRANSLATION,
                LocalChecks.TRANSLATION_PAIRS.map { (sentence, expected) ->
                    Probe("EN→FR \"$sentence\"", TranslationPrompts.buildPrompt(seed, "fr", "French", sentence), listOf(expected), letters = true)
                },
            )
            if (LocalCapability.VISION in capabilitiesOf(seed)) put(LocalCapability.VISION, LocalChecks.VISION)
        }
        val total = suites.values.sumOf { it.size }
        operation.withLock {
            _runningCheck.value = RunningCheck(seed.id, 0, total)
            try {
                log.record("LOCAL_AI", "${seed.id}: check started ($total questions)")
                val sha = withContext(Dispatchers.IO) { checks.sha256(file) }
                val projectorSha = projector?.let { withContext(Dispatchers.IO) { checks.sha256(it) } }
                var asked = 0
                var error: String? = null
                val results = linkedMapOf<String, CapabilityCheck>()
                run {
                    for ((capability, probes) in suites) {
                        val steps = mutableListOf<ProbeStep>()
                        var failure: String? = null
                        for (probe in probes) {
                            _runningCheck.value = RunningCheck(seed.id, ++asked, total)
                            val askedAt = System.currentTimeMillis()
                            var firstAt = 0L
                            val step = try {
                                if (probe.reloadBefore) {
                                    log.record("LOCAL_AI", "${seed.id}: unloading to check a reload")
                                    engine.evictIdle()
                                }
                                val images = probe.images.map { imageRef(ProbeImage.png(it), "image/png") }
                                val reply = answer(seed, probe.prompt, CHECK_QUESTION_TIMEOUT_MS, images) { firstAt = System.currentTimeMillis() }
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
                            } catch (e: LocalAiException.NotEnoughMemory) {
                                if (capability == LocalCapability.VISION) {
                                    // The weights fit (chat and translation were answered); weights and projector together do not, now.
                                    // Images FAIL for this phone's memory, said as such; what was answered stays.
                                    steps += ProbeStep(probe.title, passed = false, error = "not enough free memory for the vision part: ${e.message}", totalMs = System.currentTimeMillis() - askedAt)
                                    failure = "not enough free memory for weights and vision part together"
                                    break
                                }
                                // Not the model's answer: this phone could not hold it now. Nothing is checked.
                                error = e.message
                                results.clear()
                                return@run
                            } catch (e: Exception) {
                                ProbeStep(probe.title, passed = false, error = e.message ?: e.javaClass.simpleName, totalMs = System.currentTimeMillis() - askedAt)
                            }
                            steps += step
                            if (!step.passed && failure == null) {
                                failure = "wrong answer to: ${probe.title} (expected ${probe.expectAnyOf.joinToString(" or ")})"
                            }
                            // Every image step is asked: which part of seeing fails is the finding.
                            if (!step.passed && capability != LocalCapability.VISION) break
                        }
                        results[capability.name] = CapabilityCheck(failure == null, failure, steps)
                    }
                }
                val check = StoredCheck(
                    modelId = seed.id,
                    sha256 = sha,
                    projectorSha256 = projectorSha,
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
        return checks.results(seed.id, file, projector)
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
        const val CHAT_REPEAT_PENALTY = 1.1
        const val ANSWER_MAX_TOKENS = 1024
        const val TRANSLATION_TIMEOUT_MS = 5 * 60_000L
        const val CHECK_QUESTION_TIMEOUT_MS = 5 * 60_000L

        /** An image for the runtime: a data URI, the form llama-runtime decodes. */
        fun imageRef(bytes: ByteArray, mimeType: String): ImageRef =
            ImageRef("data:$mimeType;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))

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
