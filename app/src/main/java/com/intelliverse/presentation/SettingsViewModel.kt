package com.intelliverse.presentation

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.intelliverse.localai.IntelliverseLocalAi
import com.intelliverse.localai.LocalAiSettings
import com.intelliverse.models.ModelDownloads
import com.intelliverse.models.ModelPurpose
import com.intelliverse.models.ModelStore
import com.intelliverse.models.LocalModelCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class InstalledModelInfo(val title: String, val sizeBytes: Long)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val downloads: ModelDownloads,
    private val localAiSettings: LocalAiSettings,
    private val localAi: IntelliverseLocalAi,
    @ApplicationContext context: Context,
) : ViewModel() {
    val selection = localAiSettings.selection

    fun setUseInApps(on: Boolean) {
        localAiSettings.useInApps = on
    }

    fun chatModelTitle(): String? = localAi.defaultFor(ModelPurpose.CHAT)?.title

    fun translationModelTitle(): String? = localAi.defaultFor(ModelPurpose.TRANSLATION)?.title

    private val store = ModelStore(context)

    var installedModels by mutableStateOf(loadInstalled())
        private set

    val totalBytes: Long get() = installedModels.sumOf { it.sizeBytes }

    /** Re-reads disk state -- a model may have been downloaded/deleted from
     * the separate Models screen since this ViewModel was created. */
    fun refresh() {
        installedModels = loadInstalled()
    }

    fun deleteAllModels() {
        LocalModelCatalog.ALL.forEach { seed ->
            if (store.isInstalled(seed)) downloads.delete(seed)
        }
        refresh()
    }

    private fun loadInstalled(): List<InstalledModelInfo> =
        LocalModelCatalog.ALL
            .filter { store.isInstalled(it) }
            .map { InstalledModelInfo(it.title, store.finalFile(it).length()) }
}
