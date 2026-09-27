package com.intelliverse.models

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(
    val downloads: ModelDownloads,
) : ViewModel() {
    val catalog = TranslationModels.ALL
}
