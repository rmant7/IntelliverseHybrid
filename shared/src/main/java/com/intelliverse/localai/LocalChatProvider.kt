package com.intelliverse.localai

import ai.localstudio.sdk.GenerationOptions
import ai.localstudio.sdk.LocalAi
import ai.localstudio.sdk.LocalAiInput
import ai.localstudio.sdk.LocalImage
import com.intelliverse.models.ModelPurpose
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-device chat model as one more answer in every mini-app, next to
 * the cloud models -- the way rmant7/AI offers "Local" among its providers:
 * on when Settings says so ([LocalAiSettings.useInApps]) and a chat model is
 * installed; the model is the one chosen for chat on the Models screen. A
 * run with a photo goes to a model that can see (its vision part installed):
 * the chat model when it can, else the first that can.
 */
@Singleton
class LocalChatProvider @Inject constructor(
    private val localAi: LocalAi,
    private val models: IntelliverseLocalAi,
    private val settings: LocalAiSettings,
) {
    /** Whether a mini-app's run gets a local answer: switched on, and a model installed that can take it (one that sees, for a photo). */
    fun available(withImages: Boolean = false): Boolean = settings.useInApps && answering(withImages) != null

    /** The model that answers, for the answer's footer. */
    fun modelTitle(withImages: Boolean = false): String = answering(withImages)?.title ?: "on-device"

    private fun answering(withImages: Boolean) = if (withImages) models.defaultSeeing() else models.defaultFor(ModelPurpose.CHAT)

    /** The model's whole answer to [prompt] (about [images], when there are any), reasoning removed. */
    suspend fun answer(
        prompt: String,
        images: List<LocalImage> = emptyList(),
        systemPrompt: String? = null,
        maxTokens: Int = MAX_TOKENS,
        timeoutMs: Long = GenerationOptions().timeoutMs,
    ): String {
        val reply = StringBuilder()
        localAi.generate(LocalAiInput(prompt, images = images, systemPrompt = systemPrompt), GenerationOptions(maxTokens = maxTokens, temperature = 0.3, timeoutMs = timeoutMs)).collect { reply.append(it) }
        return IntelliverseLocalAi.finalAnswer(reply.toString())?.trim().orEmpty()
            .ifEmpty { throw IllegalStateException("${modelTitle(images.isNotEmpty())} gave no answer (still reasoning when it stopped)") }
    }

    companion object {
        /** Mini-app answers are structured and longer than a chat reply. */
        const val MAX_TOKENS = 1536

        /**
         * The JSON object inside [reply] when a model wrapped it in a code
         * fence or a sentence ("```json { ... } ```"); [reply] itself otherwise.
         */
        fun jsonIn(reply: String): String {
            val start = reply.indexOf('{')
            val end = reply.lastIndexOf('}')
            return if (start in 0 until end) reply.substring(start, end + 1) else reply
        }
    }
}
