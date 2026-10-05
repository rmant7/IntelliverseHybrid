package com.intelliverse.localai

import ai.localstudio.sdk.GenerationOptions
import ai.localstudio.sdk.LocalAi
import ai.localstudio.sdk.LocalAiInput
import com.intelliverse.models.ModelPurpose
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-device chat model as one more answer in every mini-app, next to
 * the cloud models -- the way rmant7/AI offers "Local" among its providers:
 * on when Settings says so ([LocalAiSettings.useInApps]) and a chat model is
 * installed; the model is the one chosen for chat on the Models screen.
 * Text-only questions: this app's local models do not see images yet.
 */
@Singleton
class LocalChatProvider @Inject constructor(
    private val localAi: LocalAi,
    private val models: IntelliverseLocalAi,
    private val settings: LocalAiSettings,
) {
    /** Whether a mini-app's run gets a local answer: switched on, and a chat model installed. */
    fun available(): Boolean = settings.useInApps && models.defaultFor(ModelPurpose.CHAT) != null

    /** The model that answers, for the answer's footer. */
    fun modelTitle(): String = models.defaultFor(ModelPurpose.CHAT)?.title ?: "on-device"

    /** The chat model's whole answer to [prompt], reasoning removed. */
    suspend fun answer(prompt: String): String {
        val reply = StringBuilder()
        localAi.generate(LocalAiInput(prompt), GenerationOptions(maxTokens = MAX_TOKENS, temperature = 0.3)).collect { reply.append(it) }
        return IntelliverseLocalAi.finalAnswer(reply.toString())?.trim().orEmpty()
            .ifEmpty { throw IllegalStateException("${modelTitle()} gave no answer (still reasoning when it stopped)") }
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
