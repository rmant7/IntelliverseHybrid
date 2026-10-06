package com.intelliverse.localai

import ai.localstudio.app.llama.Admission
import ai.localstudio.sdk.CheckResult
import ai.localstudio.sdk.LocalCapability
import com.intelliverse.models.LocalModelSeed
import com.intelliverse.models.ModelPurpose

/** What a request needs from a model: the capability, how much context, and whether a cloud model may answer instead. */
data class AiRequest(
    val capability: LocalCapability,
    val contextTokens: Int? = null,
    /** The caller's own mini-app allows a cloud answer when no on-device model can take it. */
    val allowCloud: Boolean = false,
    /** Models already tried for this request (refused at the load after all): not offered again. */
    val exclude: Set<String> = emptySet(),
)

/** One model the router passed over, and why -- said to the person, not only logged. */
data class Skipped(
    val modelId: String,
    val title: String,
    val reason: String,
    /** Set when it was memory: what it needs and what there is. */
    val requiredBytes: Long? = null,
    val availableBytes: Long? = null,
)

sealed interface RouteResult {
    val skipped: List<Skipped>

    /** An installed model that takes this request and fits in memory now. */
    data class Local(val seed: LocalModelSeed, override val skipped: List<Skipped>) : RouteResult

    /** No on-device model can take it now, and the caller allows the cloud. */
    data class Cloud(override val skipped: List<Skipped>) : RouteResult

    /** Nothing can take it; [reason] says why, for the person asking. */
    data class NoModel(val reason: String, override val skipped: List<Skipped>) : RouteResult
}

/**
 * Picks the model a request goes to -- a layer above the engine, not part
 * of the SDK. Every installed model that can do the request is a
 * candidate; the one chosen for the purpose on the Models screen first, then
 * those that passed their check here, then the rest, bigger first (a
 * stronger model while it fits). A candidate is skipped when its current
 * check FAILed that capability, or when the engine says it would not be
 * admitted now ([admission]: the same rule the load applies) -- and the
 * next one is tried. Only when no on-device model can take it does a cloud
 * model, if the caller allows one. It never counts memory itself.
 */
class ModelRouter(
    private val installed: () -> List<LocalModelSeed>,
    private val capabilitiesOf: (LocalModelSeed) -> Set<LocalCapability>,
    /** This phone's current check result for one capability; null when never checked. */
    private val checkOf: (LocalModelSeed, LocalCapability) -> CheckResult?,
    private val chosenFor: (ModelPurpose) -> String?,
    private val admission: (LocalModelSeed, contextTokens: Int) -> Admission,
    private val log: (String) -> Unit = {},
) {
    fun route(request: AiRequest): RouteResult {
        val skipped = mutableListOf<Skipped>()
        val purpose = if (request.capability == LocalCapability.TRANSLATION) ModelPurpose.TRANSLATION else ModelPurpose.CHAT
        val chosen = chosenFor(purpose)
        val candidates = installed()
            .filter { request.capability in capabilitiesOf(it) && it.id !in request.exclude }
            .sortedWith(
                compareByDescending<LocalModelSeed> { it.id == chosen }
                    // Translation: dedicated translators before chat models asked to translate.
                    .thenByDescending { purpose == ModelPurpose.TRANSLATION && ModelPurpose.CHAT !in it.purposes }
                    .thenByDescending { checkOf(it, request.capability) == CheckResult.PASS }
                    .thenByDescending { it.approxSizeBytes },
            )
        for (seed in candidates) {
            if (checkOf(seed, request.capability) == CheckResult.FAIL) {
                skipped += Skipped(seed.id, seed.title, "failed its ${request.capability.name.lowercase()} check on this phone")
                continue
            }
            when (val verdict = admission(seed, request.contextTokens ?: seed.contextTokens)) {
                is Admission.Admitted -> {
                    log("${request.capability}: ${seed.id}" + (if (verdict.resident) " (already loaded)" else "") + skippedNote(skipped))
                    return RouteResult.Local(seed, skipped)
                }
                is Admission.NotAdmitted -> skipped += Skipped(
                    seed.id,
                    seed.title,
                    "needs ~${verdict.requiredBytes / MB} MB, ~${verdict.availableBytes / MB} MB available now",
                    verdict.requiredBytes,
                    verdict.availableBytes,
                )
            }
        }
        if (request.allowCloud) {
            log("${request.capability}: cloud" + skippedNote(skipped))
            return RouteResult.Cloud(skipped)
        }
        val reason = when {
            candidates.isEmpty() -> "no installed on-device model can do ${request.capability.name.lowercase()}"
            else -> "no on-device model fits now: " + skipped.joinToString("; ") { "${it.title} ${it.reason}" }
        }
        log("${request.capability}: none -- $reason")
        return RouteResult.NoModel(reason, skipped)
    }

    private fun skippedNote(skipped: List<Skipped>) =
        if (skipped.isEmpty()) "" else " -- skipped " + skipped.joinToString("; ") { "${it.modelId}: ${it.reason}" }

    private companion object {
        const val MB = 1_000_000L
    }
}
