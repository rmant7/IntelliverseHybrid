package com.intelliverse.llama

/**
 * The JNI surface of llama.cpp -- ported from rmant7/AI's own LlamaBridge.kt,
 * trimmed to only the subset a translation-only feature needs (no embedding
 * or vision entry points; those still exist on the native side in
 * llama_jni.cpp but are simply never called from here). One instance owns
 * one loaded model.
 */
class LlamaBridge {

    /** Receives generated text as it is produced. Called on the generating thread. */
    interface TokenSink {
        fun onToken(text: String)
    }

    /** Returns a handle, or 0 when the model could not be loaded. */
    external fun nativeLoad(modelPath: String, contextTokens: Int, threads: Int): Long

    external fun nativeFree(handle: Long)

    /** Asks generation to stop; takes effect at the next token, not instantly. */
    external fun nativeCancel(handle: Long)

    /** Returns the number of tokens produced, or a negative code on failure. */
    external fun nativeGenerate(
        handle: Long,
        systemPrompt: String?,
        userPrompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: TokenSink,
    ): Int

    /**
     * True for an encoder-decoder (T5-family) GGUF -- MADLAD-400 is the one
     * this catalog knows about (see TranslationModels.kt) -- false for every
     * ordinary decoder-only chat GGUF. Read once, right after [nativeLoad],
     * to decide whether a turn goes through [nativeGenerate]'s chat-template
     * path or [nativeGenerateT5]'s.
     */
    external fun nativeHasEncoder(handle: Long): Boolean

    /**
     * Generation for an encoder-decoder model loaded via [nativeLoad] when
     * [nativeHasEncoder] is true -- [sourceText] is MADLAD-400's own expected
     * format, `<2xx> source text`, built by the caller.
     */
    external fun nativeGenerateT5(
        handle: Long,
        sourceText: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: TokenSink,
    ): Int

    companion object {
        /**
         * Which CPU-feature build was loaded (llama_jni, llama_jni_dotprod,
         * llama_jni_i8mm) -- the best this CPU supports, see [CpuVariant].
         * Null when none could be loaded (e.g. an ABI this build doesn't
         * cover).
         */
        val loadedLibrary: String? by lazy { CpuVariant.loadBest("llama_jni") }

        /**
         * Whether the native library is present and loadable on this device.
         * False rather than a crash: an ABI this build does not cover must
         * degrade to "local models unavailable" instead of killing the app.
         */
        val isAvailable: Boolean by lazy { loadedLibrary != null }

        const val DEFAULT_CONTEXT_TOKENS = 4096

        /**
         * Phone SoCs are big.LITTLE and ggml splits each matmul evenly across
         * its threads, so handing work to the efficiency cores makes every
         * other thread wait on them. Four is a good proxy for the
         * performance cluster.
         */
        fun defaultThreads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}
