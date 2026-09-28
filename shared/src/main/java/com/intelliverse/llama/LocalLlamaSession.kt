package com.intelliverse.llama

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns exactly one loaded local model at a time -- loading a second model
 * frees whichever one was loaded before. Deliberately much simpler than
 * rmant7/AI's own LlamaCppRuntime: that class implements :core's
 * ModelRuntime/TextModelHandle interfaces so a shared RuntimeManager can pick
 * between many registered models/providers under one RAM budget; this app
 * has no such router -- one screen, one model, loaded on demand -- so this
 * is a direct wrapper with no multi-model admission/eviction logic.
 */
class LocalLlamaSession {
    private val bridge = LlamaBridge()
    private val loadMutex = Mutex()

    @Volatile private var handle: Long = 0

    @Volatile private var hasEncoder: Boolean = false

    val isLoaded: Boolean get() = handle != 0L

    /** Loads [modelPath], freeing any previously loaded model on this session first. */
    suspend fun load(modelPath: String, contextTokens: Int = LlamaBridge.DEFAULT_CONTEXT_TOKENS): Boolean =
        loadMutex.withLock {
            // isAvailable's own lazy initializer is what actually calls
            // System.loadLibrary -- must run at least once before any
            // external fun below, or every native call throws
            // UnsatisfiedLinkError instead of failing cleanly.
            if (!LlamaBridge.isAvailable) return@withLock false
            unloadLocked()
            val newHandle = bridge.nativeLoad(modelPath, contextTokens, LlamaBridge.defaultThreads())
            if (newHandle == 0L) return@withLock false
            handle = newHandle
            hasEncoder = bridge.nativeHasEncoder(newHandle)
            true
        }

    suspend fun unload() = loadMutex.withLock { unloadLocked() }

    private fun unloadLocked() {
        if (handle != 0L) {
            bridge.nativeFree(handle)
            handle = 0
        }
    }

    /**
     * Streams generated text token by token. [prompt] is used as-is: the
     * caller is responsible for MADLAD-400's own `<2xx> source text` format
     * when this session's loaded model is a T5 encoder-decoder ([isLoaded]
     * true and the catalog entry's own isT5EncoderDecoder is true), or an
     * ordinary instruction otherwise -- this class doesn't know which model
     * is loaded beyond what [LlamaBridge.nativeHasEncoder] reports.
     */
    fun generate(
        prompt: String,
        maxTokens: Int = 512,
        // Low, near-greedy temperature -- literal translation is a
        // deterministic task, not creative writing, and this session is
        // only ever used for translation (unlike rmant7/AI's own
        // general-purpose LlamaCppRuntime, which reuses one chat-style
        // 0.7 default across every use case). A real device test: MADLAD-
        // 400 asked to translate the single word "Hi" at temperature 0.7
        // produced a fluent but entirely unrelated sentence -- exactly the
        // hallucination risk high-temperature sampling adds on a source
        // text with almost no context for the model to anchor on.
        temperature: Float = 0.2f,
        topP: Float = 0.9f,
        topK: Int = 40,
        // 1.1 wasn't enough headroom for these small quantized models --
        // they degenerate into repeating a word/phrase far more readily
        // than larger models (same fix, same reasoning, as rmant7/AI's own
        // Settings.DEFAULT_REPEAT_PENALTY).
        repeatPenalty: Float = 1.2f,
    ): Flow<String> = callbackFlow {
        val activeHandle = handle
        if (activeHandle == 0L) {
            close(IllegalStateException("No model loaded"))
            return@callbackFlow
        }
        val callback = object : LlamaBridge.TokenSink {
            override fun onToken(text: String) {
                trySend(text)
            }
        }
        val job = launch(Dispatchers.IO) {
            val result = if (hasEncoder) {
                bridge.nativeGenerateT5(
                    activeHandle, prompt, maxTokens, temperature, topP, topK, repeatPenalty, callback,
                )
            } else {
                bridge.nativeGenerate(
                    activeHandle, null, prompt, maxTokens, temperature, topP, topK, repeatPenalty, callback,
                )
            }
            if (result < 0) close(IllegalStateException("Generation failed (code $result)")) else close()
        }
        awaitClose {
            bridge.nativeCancel(activeHandle)
            job.cancel()
        }
    }
}
