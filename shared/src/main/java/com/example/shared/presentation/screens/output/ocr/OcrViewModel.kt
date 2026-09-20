package com.example.shared.presentation.screens.output.ocr

import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import com.example.shared.domain.usecases.TextUtils
import com.example.shared.presentation.screens.output.SharedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class OcrViewModel @Inject constructor() : ViewModel(){

    private var _sharedViewModel: SharedViewModel? = null
    val sharedViewModel: SharedViewModel
        get() = _sharedViewModel ?: throw IllegalStateException("SharedViewModel is not initialized")

    fun initSharedViewModel(sharedViewModel: SharedViewModel) {
        _sharedViewModel = sharedViewModel
        updateOcrTextDirection(TextUtils.getTextDirection(sharedViewModel.ocrResults.value.values.firstOrNull()))
    }

    /** A pair of Error title and Error message */
    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error

    fun updateError(error: Throwable?) {
        _error.update { error }
    }

    /** Ocr text direction */
    private val _ocrTextDirection = MutableStateFlow<LayoutDirection?>(null)
    val ocrTextDirection: StateFlow<LayoutDirection?> = _ocrTextDirection

    fun updateOcrTextDirection(ocrTextDirection: LayoutDirection?) {
        _ocrTextDirection.update { ocrTextDirection }
    }

    private val _isOcrEdited = MutableStateFlow(false)
    val isOcrEdited: StateFlow<Boolean> = _isOcrEdited

    fun updateIsOcrEdited(isOcrEdited: Boolean) {
        _isOcrEdited.update { isOcrEdited }
    }

}