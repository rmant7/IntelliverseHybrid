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

    /** Path + context size of the model [handle] currently holds, or null when nothing is loaded. */
    @Volatile var loadedModelPath: String? = null
        private set
    @Volatile private var loadedContextTokens: Int = 0

    val isLoaded: Boolean get() = handle != 0L

    /** Loads [modelPath], freeing any previously loaded model on this session first. */
    suspend fun load(modelPath: String, contextTokens: Int = LlamaBridge.DEFAULT_CONTEXT_TOKENS): Boolean =
        loadMutex.withLock {
            // isAvailable's own lazy initializer is what actually calls
            // System.loadLibrary -- must run at least once before any
            // external fun below, or every native call throws
            // UnsatisfiedLinkError instead of failing cleanly.
            if (!LlamaBridge.isAvailable) return@withLock false
            // Same model already resident -- reuse it. A multi-GB nativeLoad
            // is by far the slowest part of a local translation (tens of
            // seconds on a phone, far longer than any cloud round trip), and
            // re-reading the exact same file every single run was most of
            // why local results only ever showed up after the cloud ones.
            if (handle != 0L && loadedModelPath == modelPath && loadedContextTokens == contextTokens) {
                return@withLock true
            }
            unloadLocked()
            val newHandle = bridge.nativeLoad(modelPath, contextTokens, LlamaBridge.defaultThreads())
            if (newHandle == 0L) return@withLock false
            handle = newHandle
            loadedModelPath = modelPath
            loadedContextTokens = contextTokens
            hasEncoder = bridge.nativeHasEncoder(newHandle)
            true
        }

    suspend fun unload() = loadMutex.withLock { unloadLocked() }

    private fun unloadLocked() {
        if (handle != 0L) {
            bridge.nativeFree(handle)
            handle = 0
            loadedModelPath = null
            loadedContextTokens = 0
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
        /** A system turn ahead of [prompt], through the model's own chat template; ignored by a T5 model. */
        systemPrompt: String? = null,
    ): Flow<String> = callbackFlow {
        val callback = object : LlamaBridge.TokenSink {
            override fun onToken(text: String) {
                trySend(text)
            }
        }
        val job = launch(Dispatchers.IO) {
            // Held for the whole native call, not just load()/unload() --
            // this session is a Hilt @Singleton shared across every screen
            // that translates, and without this a concurrent load() (e.g.
            // the user backs out mid-generation, changes settings, and
            // starts a new translation before this coroutine's own
            // cancellation has actually unwound) frees the native context
            // this coroutine is still actively generating from underneath
            // it. Confirmed on a real device: crash with nothing in the
            // app's own log at all -- a native use-after-free/SIGSEGV,
            // not a caught Kotlin exception, exactly this sequence
            // (previous local-model generation still in flight when the
            // next run's load() ran).
            loadMutex.withLock {
                val activeHandle = handle
                if (activeHandle == 0L) {
                    close(IllegalStateException("No model loaded"))
                    return@withLock
                }
                val result = if (hasEncoder) {
                    bridge.nativeGenerateT5(
                        activeHandle, prompt, maxTokens, temperature, topP, topK, repeatPenalty, callback,
                    )
                } else {
                    bridge.nativeGenerate(
                        activeHandle, systemPrompt, prompt, maxTokens, temperature, topP, topK, repeatPenalty, callback,
                    )
                }
                if (result < 0) close(IllegalStateException("Generation failed (code $result)")) else close()
            }
        }
        awaitClose {
            // Signals the in-progress native loop to stop -- read live off
            // [handle] rather than a captured value, since with the mutex
            // above this can only be the handle generate() is (or was
            // about to be) locked on; nativeCancel itself doesn't need the
            // lock, it just flips a flag the generation loop checks
            // between tokens so the withLock block above can return and
            // release the mutex for whoever's waiting on it next (a
            // concurrent load(), most commonly).
            bridge.nativeCancel(handle)
            job.cancel()
        }
    }
}
