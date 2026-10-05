package com.intelliverse.localai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which installed model answers chat and which translates -- chosen on the
 * Models screen; null means "the best installed one". [useInApps]: the
 * chat model also answers in the mini-apps, next to the cloud models.
 */
data class ModelSelection(val chatModelId: String?, val translationModelId: String?, val useInApps: Boolean = true)

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

    var useInApps: Boolean
        get() = _selection.value.useInApps
        set(value) = write(_selection.value.copy(useInApps = value))

    private fun read() = ModelSelection(prefs.getString(KEY_CHAT, null), prefs.getString(KEY_TRANSLATION, null), prefs.getBoolean(KEY_IN_APPS, true))

    private fun write(selection: ModelSelection) {
        prefs.edit()
            .putString(KEY_CHAT, selection.chatModelId)
            .putString(KEY_TRANSLATION, selection.translationModelId)
            .putBoolean(KEY_IN_APPS, selection.useInApps)
            .apply()
        _selection.value = selection
    }

    private companion object {
        const val KEY_CHAT = "chat_model"
        const val KEY_TRANSLATION = "translation_model"
        const val KEY_IN_APPS = "use_in_apps"
    }
}
