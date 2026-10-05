package com.intelliverse.localai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Which installed model answers chat and which translates -- chosen on the Models screen; null means "the best installed one". */
data class ModelSelection(val chatModelId: String?, val translationModelId: String?)

@Singleton
class LocalAiSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("local_ai", Context.MODE_PRIVATE)

    private val _selection = MutableStateFlow(read())
    val selection: StateFlow<ModelSelection> = _selection

    var chatModelId: String?
        get() = _selection.value.chatModelId
        set(value) = write(_selection.value.copy(chatModelId = value))

    var translationModelId: String?
        get() = _selection.value.translationModelId
        set(value) = write(_selection.value.copy(translationModelId = value))

    private fun read() = ModelSelection(prefs.getString(KEY_CHAT, null), prefs.getString(KEY_TRANSLATION, null))

    private fun write(selection: ModelSelection) {
        prefs.edit().putString(KEY_CHAT, selection.chatModelId).putString(KEY_TRANSLATION, selection.translationModelId).apply()
        _selection.value = selection
    }

    private companion object {
        const val KEY_CHAT = "chat_model"
        const val KEY_TRANSLATION = "translation_model"
    }
}
