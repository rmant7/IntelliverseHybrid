package com.intelliverse.models

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intelliverse.llama.LlamaBridge
import com.intelliverse.llama.LocalLlamaSession
import com.intelliverse.llama.TranslationPrompts
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(
    val downloads: ModelDownloads,
    private val llama: LocalLlamaSession,
    @ApplicationContext context: Context,
) : ViewModel() {
    val catalog = TranslationModels.ALL
    private val store = ModelStore(context)

    /** Whether the native llama.cpp library itself is present on this
     * device/build -- checked once; a build without the native library
     * (unsupported ABI) should show "unavailable", not attempt a load that
     * can only fail. */
    val nativeAvailable: Boolean get() = LlamaBridge.isAvailable

    var testExpandedId by mutableStateOf<String?>(null)
        private set
    var loadedModelId by mutableStateOf<String?>(null)
        private set
    var isBusy by mutableStateOf(false)
        private set
    var translateOutput by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun toggleTest(seed: LocalModelSeed) {
        testExpandedId = if (testExpandedId == seed.id) null else seed.id
        errorMessage = null
    }

    fun translate(seed: LocalModelSeed, targetLang: String, text: String) {
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            errorMessage = null
            translateOutput = ""
            try {
                if (loadedModelId != seed.id) {
                    val ok = llama.load(store.finalFile(seed).absolutePath)
                    if (!ok) {
                        errorMessage = "Failed to load model -- check the on-device Log for the reason."
                        return@launch
                    }
                    loadedModelId = seed.id
                }
                // This quick-test field takes a bare code ("he"), not a
                // full name -- Locale resolves the English display name
                // from it for the chat-model prompt path (see
                // TranslationPrompts.buildPrompt's own doc comment on why a
                // bare code alone confused TranslateGemma on a real device).
                val targetLangName = java.util.Locale(targetLang).getDisplayLanguage(java.util.Locale.ENGLISH)
                    .ifBlank { targetLang }
                val prompt = TranslationPrompts.buildPrompt(seed, targetLang, targetLangName, text)
                // Accumulated raw, then stripped once complete -- a
                // reasoning model's <think> block can't be cleanly removed
                // from a live-streamed partial string, so this quick-test
                // screen shows the final answer only, not a live typing
                // effect.
                val raw = StringBuilder()
                llama.generate(prompt).collect { token -> raw.append(token) }
                translateOutput = TranslationPrompts.stripThinking(raw.toString())
            } catch (e: Exception) {
                errorMessage = e.message ?: "Translation failed"
            } finally {
                isBusy = false
            }
        }
    }
}
