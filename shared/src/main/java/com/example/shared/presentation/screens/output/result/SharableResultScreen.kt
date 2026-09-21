package com.example.shared.presentation.screens.output.result

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.BidiFormatter
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.R
import com.example.shared.ReportDialog
import com.example.shared.googlePlayLink
import com.example.shared.presentation.common.MediaPlayer
import com.example.shared.presentation.common.button.FlickeringRoundIconButton
import com.example.shared.presentation.common.button.RadioIndexButton
import com.example.shared.presentation.common.button.RoundIconButton
import com.example.shared.presentation.common.button.TextAlignmentButton
import com.example.shared.presentation.common.dialog.ErrorAlertDialog
import com.example.shared.presentation.common.web_view.HtmlTextView
import com.example.shared.presentation.common.web_view.cleanHtmlStr
import com.example.shared.presentation.screens.AIService


fun textToShare(
    userTask: String?,
    ocrResult: String?,
    solution: String?,
    taskTextLabel: String,
    recognizedTextLabel: String,
    solutionTextLabel: String,
    solvedByText: String
): String {
    val stringBuilder = StringBuilder()
    val bidiFormatter = BidiFormatter.getInstance()
    val delimiter = bidiFormatter.unicodeWrap("=".repeat(15))

    if (!userTask.isNullOrBlank()) {
        stringBuilder.append("$taskTextLabel:\n ${userTask.trim()}")
    }

    if (!ocrResult.isNullOrBlank()) {
        if (stringBuilder.isNotEmpty()) stringBuilder.append("\n$delimiter\n")
        stringBuilder.append("$recognizedTextLabel:\n ${ocrResult.trim()}")
    }

    if (!solution.isNullOrBlank()) {
        if (stringBuilder.isNotEmpty()) stringBuilder.append("\n$delimiter\n")
        stringBuilder.append("$solutionTextLabel:\n $solution")
    }

    stringBuilder.append("\n$delimiter\n$solvedByText:\n $googlePlayLink")

    return stringBuilder.toString().trim()
}

fun evalTextAndShare(
    userTask: String?,
    ocrResult: String?,
    webView: WebView?,
    taskTextLabel: String,
    recognizedTextLabel: String,
    solutionTextLabel: String,
    solvedByText: String,
    cleanHtmlStr: (String) -> String,
    share: (String) -> Unit
) {
    if (webView == null) {
        share(textToShare(userTask, ocrResult, null, taskTextLabel, recognizedTextLabel, solutionTextLabel, solvedByText))
    } else {
        webView.evaluateJavascript("getPlainMathAndUrls()") { plainText ->
            val text = textToShare(
                userTask,
                ocrResult,
                cleanHtmlStr(plainText),
                taskTextLabel,
                recognizedTextLabel,
                solutionTextLabel,
                solvedByText
            )
            share(text)
        }
    }
}

fun shareContent(text: String, imageUri: Uri?, context: Context) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        if (imageUri != null) {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "Shared Image", imageUri)
        }
        if (text.isNotBlank()) {
            if (imageUri == null) {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }
    require(shareIntent.type != null) { "No Uri nor text is supplied for sharing" }
    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
}

fun handleSolutionRegeneration(
    requestSolutionResponse: Boolean,
    viewModel: BaseResultViewModel,
    isWebViewReload: MutableState<Boolean>
) {
    if (requestSolutionResponse) {
        val audioPlayer = viewModel.audioPlayer
        viewModel.stopUtterance()
        audioPlayer.pause()
        audioPlayer.setNewFile(null)
        audioPlayer.changeTimeStamp(0f)

        isWebViewReload.value = true
        viewModel.generateSolutions()
        viewModel.updateRequestSolutionResponse(false)
    }
}


/** Conditions to show ad:
 * - There are no solutions yet.
 * - It's user's first try or second "Try again" button's click. */
fun maybeShowAd(
    context: Context,
    secretShowAd: Boolean,
    solutionResults: Map<*, *>,
    shouldShowAd: Boolean,
    adViewCount: Int,
    viewModel: BaseResultViewModel
) {
    if (secretShowAd && solutionResults.isEmpty() && shouldShowAd && adViewCount == 0) {
        viewModel.showInterstitialAd(context)
    }
}

@Composable
fun ResultScreenContent(
    viewModel: BaseResultViewModel,
    solutionResults: Map<AIService, String?>,
    selectedSolutionService: AIService?,
    solutionProgress: Float,
    solutionTextDirection: LayoutDirection,
    webView: MutableState<WebView?>,
    isWebViewReload: MutableState<Boolean>,
    ocrResult: String,
    aiSolution: String,
    hasFlickered: Boolean,
    updateHasFlickered: (Boolean) -> Unit,
    showShareDialog: MutableState<Boolean>,
    showReportDialog: MutableState<Boolean>,
    shareSolution: MutableState<Boolean>,
    shareImage: MutableState<Boolean>,
    shareOcrResult: MutableState<Boolean>,
    shareUserTask: MutableState<Boolean>,
    onNavigateToOcrScreen: () -> Unit,
    context: Context,
    taskTextLabel: String,
    recognizedTextLabel: String,
    solutionTextLabel: String,
    solvedByStyleTranslator: String,
    invalidSolutionText: String,
    solutionsGenerationProgress: String,
    errors: List<Throwable>,
    shouldShowErrorDialog: Boolean,
    isDebugMode: Boolean,
    shareImageLabel: String,
    chooseSharing: String,
    share: String,
    shareOcrResultLabel: String,
    shareSolutionLabel: String,
    shareUserText: String
) {
    // Tab/index order for the results that actually came back -- by AIService's
    // own declared order (GEMINI, GEMINI_THINKING, GPT, GROQ, GIGACHAT), not
    // by whichever happened to finish first. GigaChat is declared last there
    // specifically so it always sorts last here too: it's the fallback,
    // only ever invoked once the primary services are already done, and
    // future local/on-device models are expected to land after it in that
    // same enum for the same reason (slower, only worth showing last).
    val orderedResults = solutionResults.filterValues { it != null }.keys.sortedBy { it.ordinal }

    if (shouldShowErrorDialog) {

        fun onErrorFound() {
            viewModel.updateShouldShowErrorDialog(false)
        }

        ErrorAlertDialog(
            onDismissRequest = {
                onErrorFound()
            },
            onConfirmation = {
                onErrorFound()
            },
            errors = errors.toList(),
            icon = Icons.Default.Info
        )
    }

    // Progress
    if (solutionProgress != 1f) {
        // Indicator how many AI responses were received.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = Modifier.padding(0.dp, 10.dp),
                text = solutionsGenerationProgress,
                fontSize = 20.sp
            )
            LinearProgressIndicator(
                progress = { solutionProgress },
            )
        }
    }

    // Solution Display
    Box(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.65f),
        contentAlignment = Alignment.Center
    ) {
        val content = solutionResults[selectedSolutionService]
        val isEnabled = !content.isNullOrBlank()

        if (isEnabled) {
            val index = orderedResults.indexOf(selectedSolutionService)
            HtmlTextView(
                modifier = Modifier.fillMaxSize().clip(RectangleShape),
                htmlContent = content ?: invalidSolutionText,
                isEditable = false,
                textDirection = solutionTextDirection,
                isReload = isWebViewReload.value,
                title = "$solutionTextLabel ${index + 1}:${if (isDebugMode) " ($selectedSolutionService)" else ""}",
                onWebViewCreated = { webView.value = it }
            )
            isWebViewReload.value = false
        } else {
            if (solutionProgress == 1f) {
                Text(invalidSolutionText)
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    // AI Service Buttons
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(orderedResults) { aiService ->
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RadioIndexButton(
                    isSelected = aiService == selectedSolutionService,
                    isEnabled = true,
                    onRadioButtonClick = {
                        if (aiService != selectedSolutionService) {
                            viewModel.updateSelectedSolutionService(aiService)
                            isWebViewReload.value = true
                        }
                    }
                )
            }
        }
    }

    // Action Buttons Row
    ActionButtonsRow(
        viewModel = viewModel,
        ocrResult = ocrResult,
        aiSolution = aiSolution,
        webView = webView,
        onNavigateToOcrScreen = onNavigateToOcrScreen,
        context = context,
        hasFlickered = hasFlickered,
        updateHasFlickered = updateHasFlickered,
        showShareDialog = showShareDialog,
        showReportDialog = showReportDialog,
        shareUserTask = shareUserTask,
        shareOcrResult = shareOcrResult,
        shareSolution = shareSolution,
        shareImage = shareImage,
        taskTextLabel = taskTextLabel,
        recognizedTextLabel = recognizedTextLabel,
        solutionTextLabel = solutionTextLabel,
        solvedByStyleTranslator = solvedByStyleTranslator,
        solutionTextDirection = solutionTextDirection,
        shareImageLabel = shareImageLabel,
        chooseSharing = chooseSharing,
        share = share,
        shareOcrResultLabel = shareOcrResultLabel,
        shareSolutionLabel = shareSolutionLabel,
        shareUserTranslation = shareUserText
    )
}

@Composable
fun ActionButtonsRow(
    viewModel: BaseResultViewModel,
    ocrResult: String,
    aiSolution: String,
    webView: MutableState<WebView?>,
    onNavigateToOcrScreen: () -> Unit,
    context: Context,
    hasFlickered: Boolean,
    updateHasFlickered: (Boolean) -> Unit,
    showShareDialog: MutableState<Boolean>,
    showReportDialog: MutableState<Boolean>,
    shareUserTask: MutableState<Boolean>,
    shareOcrResult: MutableState<Boolean>,
    shareSolution: MutableState<Boolean>,
    shareImage: MutableState<Boolean>,
    solutionTextDirection: LayoutDirection,
    taskTextLabel: String,
    recognizedTextLabel: String,
    solutionTextLabel: String,
    solvedByStyleTranslator: String,
    shareUserTranslation: String,
    shareOcrResultLabel: String,
    shareSolutionLabel: String,
    shareImageLabel: String,
    chooseSharing: String,
    share: String
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(
            icon = R.drawable.retry_svg,
            onButtonClick = {
                viewModel.updateRequestSolutionResponse(true)
                viewModel.updateShouldShowAd(true)
                viewModel.increaseInterstitialAdViewCount()
            }
        )

        TextAlignmentButton(
            layoutDirection = solutionTextDirection,
            onUpdate = {
                viewModel.updateSolutionTextDirection(it)
            }
        )

        FlickeringRoundIconButton(
            icon = R.drawable.ic_document,
            isEnabled = ocrResult.isNotBlank(),
            onButtonClick = onNavigateToOcrScreen,
            hasFlickered = hasFlickered,
            updateHasFlickered = updateHasFlickered
        )

        val itemsToShare = run {
            var count = 0
            if (viewModel.passedImageUris.isNotEmpty()) count++
            if (viewModel.userTask.isNotBlank()) count++
            if (aiSolution.isNotBlank()) count++
            if (ocrResult.isNotBlank()) count++
            count
        }

        RoundIconButton(
            icon = Icons.Default.Share,
            isEnabled = itemsToShare > 0,
            onButtonClick = {
                if (itemsToShare > 1) {
                    showShareDialog.value = true
                } else {
                    evalTextAndShare(
                        viewModel.userTask,
                        ocrResult,
                        webView.value,
                        taskTextLabel = taskTextLabel,
                        recognizedTextLabel = recognizedTextLabel,
                        solutionTextLabel = solutionTextLabel,
                        solvedByText = solvedByStyleTranslator,
                        cleanHtmlStr = ::cleanHtmlStr
                    ) {
                        shareContent(it, viewModel.passedImageUris.firstOrNull(), context)
                    }
                }
            }
        )

        if (showShareDialog.value) {
            ShareDialog(
                showDialog = showShareDialog,
                userTask = viewModel.userTask,
                ocrResult = ocrResult,
                aiSolution = aiSolution,
                imageUris = viewModel.passedImageUris,
                webView = webView.value,
                shareUserTask = shareUserTask,
                shareOcrResult = shareOcrResult,
                shareSolution = shareSolution,
                shareImage = shareImage,
                taskTextLabel = taskTextLabel,
                recognizedTextLabel = recognizedTextLabel,
                solutionTextLabel = solutionTextLabel,
                solvedByText = solvedByStyleTranslator,
                context = context,
                shareUserTranslation = shareUserTranslation,
                shareOcrResultLabel = shareOcrResultLabel,
                shareSolutionLabel = shareSolutionLabel,
                shareImageLabel = shareImageLabel,
                chooseSharing = chooseSharing,
                share = share
            )
        }

        RoundIconButton(
            icon = R.drawable.flag_report,
            onButtonClick = { showReportDialog.value = true },
            isEnabled = aiSolution.isNotBlank()
        )

        if (showReportDialog.value) {
            ReportDialog(
                onDismiss = { showReportDialog.value = false },
                onSubmit = {
                    //onSubmit(selectedReason) // not really used, just to obey AI-Generated Content policy
                    showReportDialog.value = false
                }
            )
        }
    }
}

@Composable
fun ShareDialog(
    showDialog: MutableState<Boolean>,
    userTask: String,
    ocrResult: String,
    aiSolution: String,
    imageUris: List<Uri>,
    webView: WebView?,
    shareUserTask: MutableState<Boolean>,
    shareOcrResult: MutableState<Boolean>,
    shareSolution: MutableState<Boolean>,
    shareImage: MutableState<Boolean>,
    taskTextLabel: String,
    recognizedTextLabel: String,
    solutionTextLabel: String,
    solvedByText: String,
    chooseSharing: String,
    share: String,
    context: Context,
    shareUserTranslation: String,
    shareOcrResultLabel: String,
    shareSolutionLabel: String,
    shareImageLabel: String
) {
    AlertDialog(
        onDismissRequest = { showDialog.value = false },
        title = { Text(chooseSharing) },
        text = {
            Column {
                if (userTask.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = shareUserTask.value, onCheckedChange = { shareUserTask.value = it })
                        Text(text = shareUserTranslation)
                    }
                }
                if (ocrResult.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = shareOcrResult.value, onCheckedChange = { shareOcrResult.value = it })
                        Text(text = shareOcrResultLabel)
                    }
                }
                if (aiSolution.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = shareSolution.value, onCheckedChange = { shareSolution.value = it })
                        Text(text = shareSolutionLabel)
                    }
                }
                if (imageUris.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = shareImage.value, onCheckedChange = { shareImage.value = it })
                        Text(text = shareImageLabel)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                showDialog.value = false
                val task = if (shareUserTask.value) userTask else null
                val ocr = if (shareOcrResult.value) ocrResult else null
                val imageUri = if (shareImage.value) imageUris.firstOrNull() else null

                evalTextAndShare(
                    task,
                    ocr,
                    if (shareSolution.value) webView else null,
                    taskTextLabel = taskTextLabel,
                    recognizedTextLabel = recognizedTextLabel,
                    solutionTextLabel = solutionTextLabel,
                    solvedByText = solvedByText,
                    cleanHtmlStr = ::cleanHtmlStr
                ) {
                    shareContent(it, imageUri, context)
                }
            }) {
                Text(share)
            }
        },
        dismissButton = {
            Button(onClick = { showDialog.value = false }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SolutionAudioPlayer(
    viewModel: BaseResultViewModel,
    selectedSolutionService: State<AIService?>,
    textToSpeechAudioFiles: State<MutableList<String>>,
    appName: String
) {
    val id = "${appName}_solution_${selectedSolutionService.value?.ordinal}.wav"
    val filePath = "$audioPath$id"
    val isEnabled = textToSpeechAudioFiles.value.contains(id)
    val audioPlayer = viewModel.audioPlayer

    MediaPlayer(
        viewModel = viewModel,
        filePath = if (isEnabled) filePath else null,
        audioPlayer = audioPlayer,
        isEnabled = isEnabled
    )
}
