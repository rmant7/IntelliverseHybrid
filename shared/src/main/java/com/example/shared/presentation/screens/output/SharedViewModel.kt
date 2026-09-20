package com.example.shared.presentation.screens.output

import androidx.lifecycle.ViewModel
import com.example.shared.domain.usecases.TextUtils
import com.example.shared.presentation.screens.AIService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

class SharedViewModel@Inject constructor(): ViewModel() {

    private val _hasFlickered = MutableStateFlow(false)
    val hasFlickered: StateFlow<Boolean> = _hasFlickered

    fun updateHasFlickered(hasFlickered: Boolean) {
        _hasFlickered.update { hasFlickered }
    }

    /** Selected solution service */
    private val _selectedOcrService = MutableStateFlow<AIService?>(null)
    val selectedOcrService: StateFlow<AIService?> = _selectedOcrService

    fun updateSelectedOcrService(selectedSolutionService: AIService?) {
        _selectedOcrService.update { selectedSolutionService }
    }

    /** OCR results */
    private val _ocrResults = MutableStateFlow<Map<AIService, String>>(emptyMap())
    val ocrResults: StateFlow<Map<AIService, String>> =
        _ocrResults.asStateFlow()

    fun updateOcrResults(
        aiService: AIService,
        resultText: String,
        override: Boolean
    ) {
        if (!override && _ocrResults.value.containsKey(aiService)) {
            return
        }
        if (resultText.isNotBlank()) {
            val htmlString = TextUtils.markdownToHtml(resultText)
            if (_selectedOcrService.value == null) {
                updateSelectedOcrService(aiService)
            }
            _ocrResults.update { oldMap ->
                oldMap + (aiService to htmlString)
            }
        }
    }

    fun solveByOcrResult(aiService: AIService, resultText: String) {
        _ocrResults.update { emptyMap() }
        val htmlString = TextUtils.markdownToHtml(resultText)
        _ocrResults.update { oldMap ->
            oldMap + (aiService to htmlString)
        }
    }

    fun reset() {
        _hasFlickered.update { false }
        _selectedOcrService.update { null }
        _ocrResults.update { emptyMap() }
    }

}