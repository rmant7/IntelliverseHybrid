package com.example.shared.presentation.screens.output.ocr

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.shared.R
import com.example.shared.domain.usecases.TextUtils
import com.example.shared.presentation.common.button.RadioIndexButton
import com.example.shared.presentation.common.button.RoundIconButton
import com.example.shared.presentation.common.button.TextAlignmentButton
import com.example.shared.presentation.common.button.UniversalButton
import com.example.shared.presentation.common.dialog.ErrorAlertDialog
import com.example.shared.presentation.common.web_view.HtmlTextView
import com.example.shared.presentation.common.web_view.cleanHtmlStr
import com.example.shared.presentation.screens.AIService

@Composable
fun OcrContentSection(
    viewModel: OcrViewModel,
    selectedOcrService: AIService?,
    ocrResults: Map<AIService, String>,
    textDirection: LayoutDirection?,
    isEditButtonVisible: MutableState<Boolean>,
    isOcrEdited: Boolean,
    isWebViewReload: MutableState<Boolean>,
    webView: MutableState<WebView?>,
    recognizedTextLabel: String,
    imeVisible: Boolean,
    ocrError: Throwable?,
    shouldShowErrorMessage: MutableState<Boolean>,
    isDebugMode: Boolean,
    onNavigateToResultScreen: (String?) -> Unit
) {
    if (ocrError != null && shouldShowErrorMessage.value) {
        ErrorAlertDialog(
            onDismissRequest = {
                shouldShowErrorMessage.value = false
            },
            onConfirmation = {
                shouldShowErrorMessage.value = false
                viewModel.updateError(null)
                onNavigateToResultScreen(null)
            },
            errors = listOf(ocrError),
            icon = Icons.Default.Info
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(if (imeVisible) 0.65f else 0.7f)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        val content = ocrResults[selectedOcrService] ?: ""
        if (content.isNotBlank()) {
            HtmlTextView(
                modifier = Modifier.fillMaxSize().clip(RectangleShape),
                title = recognizedTextLabel + if (isDebugMode) " ($selectedOcrService)" else "",
                htmlContent = content,
                isEditable = !isEditButtonVisible.value,
                isReload = isWebViewReload.value,
                textDirection = textDirection,
                onWebViewCreated = { createdWebView ->
                    webView.value = createdWebView
                }
            )
            isWebViewReload.value = false
        } else {
            CircularProgressIndicator(modifier = Modifier.size(80.dp))
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(ocrResults.filter { (_, value) -> value.isNotBlank() }
            .keys.toList()
        ) { aiService ->
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RadioIndexButton(
                    isSelected = aiService == selectedOcrService,
                    isEnabled = true,
                    onRadioButtonClick = {
                        if (aiService != selectedOcrService) {
                            webView.value?.evaluateJavascript("getContent()") { latestValue ->
                                viewModel.sharedViewModel.updateOcrResults(
                                    selectedOcrService!!,
                                    cleanHtmlStr(latestValue),
                                    override = true
                                )
                                viewModel.sharedViewModel.updateSelectedOcrService(aiService)
                                isWebViewReload.value = true
                            }
                        }
                    }
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextAlignmentButton(
            layoutDirection = textDirection!!,
            onUpdate = {
                viewModel.updateOcrTextDirection(it)
            }
        )

        Button(
            modifier = Modifier.wrapContentWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            onClick = {
                webView.value?.evaluateJavascript("getContent()") { latestValue ->
                    val newOcrResult = TextUtils.htmlToJsonString(cleanHtmlStr(latestValue))
                    viewModel.sharedViewModel.solveByOcrResult(selectedOcrService!!, newOcrResult)
                    onNavigateToResultScreen(newOcrResult)
                }
            },
            enabled = isEditButtonVisible.value && isOcrEdited
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Sending"
            )
        }

        if (isEditButtonVisible.value) {
            RoundIconButton(
                iconModifier = Modifier.size(30.dp),
                icon = R.drawable.edit_svg,
                onButtonClick = {
                    isEditButtonVisible.value = false
                }
            )
        } else {
            UniversalButton(label = R.string.Ok) {
                isEditButtonVisible.value = true
                viewModel.updateIsOcrEdited(true)
            }
        }
    }
}
