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
                val prompt = TranslationPrompts.buildPrompt(seed, targetLang, text)
                llama.generate(prompt).collect { token -> translateOutput += token }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Translation failed"
            } finally {
                isBusy = false
            }
        }
    }
}
