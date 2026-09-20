package com.diettracker.presentation.screens.output.ocr

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diettracker.BuildConfig
import com.diettracker.R
import com.example.shared.presentation.common.ApplicationScaffold
import com.example.shared.presentation.screens.output.SharedViewModel
import com.example.shared.presentation.screens.output.ocr.OcrViewModel
import com.example.shared.presentation.screens.output.ocr.OcrContentSection


@Composable
fun OcrScreenContent(
    sharedViewModel: SharedViewModel,
    onNavigateToResultScreen: (String?) -> Unit
) {
    val viewModel: OcrViewModel = hiltViewModel<OcrViewModel>().apply {
        initSharedViewModel(
            sharedViewModel
        )
    }
    val selectedOcrService = viewModel.sharedViewModel.selectedOcrService.collectAsState()
    val ocrResults = viewModel.sharedViewModel.ocrResults.collectAsStateWithLifecycle()
    val isWebViewReload = remember { mutableStateOf(false) }
    // Get the height of the IME (keyboard) in pixels
    val imeHeightPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val imeVisible = imeHeightPx > 0

    val focusManager = LocalFocusManager.current
    val webView: MutableState<WebView?> = remember { mutableStateOf(null) }
    val isOcrEdited = viewModel.isOcrEdited.collectAsState()

    val ocrError = viewModel.error.collectAsState()
    val shouldShowErrorMessage = remember { mutableStateOf(true) }

    val textDirection = viewModel.ocrTextDirection.collectAsState()
    val recognizedTextLabel = stringResource(R.string.ingredients)

    val isEditButtonVisible = remember { mutableStateOf(true) }

    BackHandler {}

    ApplicationScaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    focusManager.clearFocus() // Clear focus only when tapping outside
                }
            },
        isShowed = true,
        content = {

            OcrContentSection(
                viewModel = viewModel,
                isDebugMode = BuildConfig.DEBUG,
                selectedOcrService = selectedOcrService.value,
                ocrResults = ocrResults.value,
                textDirection = textDirection.value,
                isEditButtonVisible = isEditButtonVisible,
                isOcrEdited = isOcrEdited.value,
                isWebViewReload = isWebViewReload,
                webView = webView,
                recognizedTextLabel = recognizedTextLabel,
                imeVisible = imeVisible,
                ocrError = ocrError.value,
                shouldShowErrorMessage = shouldShowErrorMessage,
                onNavigateToResultScreen = onNavigateToResultScreen
            )

        })
}