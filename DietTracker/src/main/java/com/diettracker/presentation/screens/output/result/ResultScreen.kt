package com.diettracker.presentation.screens.output.result

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diettracker.BuildConfig
import com.diettracker.R.string
import com.example.shared.domain.usecases.TextUtils
import com.example.shared.presentation.screens.output.SharedViewModel
import com.example.shared.presentation.screens.output.result.ResultScreenContent
import com.example.shared.presentation.screens.output.result.handleSolutionRegeneration
import com.example.shared.presentation.screens.output.result.maybeShowAd


@Composable
fun ResultScreen(
    sharedViewModel: SharedViewModel,
    secretShowAd: Boolean,
    onNavigateToOcrScreen: () -> Unit,
) {
    val viewModel: ResultViewModel = hiltViewModel<ResultViewModel>().apply {
        initSharedViewModel(
            sharedViewModel
        )
    }
    val solutionResults = viewModel.solutionResults.collectAsStateWithLifecycle()
    val ocrResults = viewModel.sharedViewModel.ocrResults.collectAsStateWithLifecycle()
    val requestSolutionResponse = viewModel.requestSolutionResponse.collectAsState()

    val solutionTextDirection = viewModel.solutionTextDirection.collectAsState()

    val selectedSolutionService = viewModel.selectedSolutionService.collectAsState()
    val selectedOcrService = viewModel.sharedViewModel.selectedOcrService.collectAsState()
    val aiSolution by remember {
        derivedStateOf {
            solutionResults.value.getOrDefault(
                selectedSolutionService.value, ""
            ) ?: ""
        }
    }
    val ocrResult by remember {
        derivedStateOf {
            TextUtils.htmlToJsonString(ocrResults.value.getOrDefault(selectedOcrService.value, ""))
        }
    }
    val solutionProgress = viewModel.solutionProgress.collectAsState()

    val context = LocalContext.current
    val isWebViewReload = remember { mutableStateOf(false) }
    val webView: MutableState<WebView?> = remember { mutableStateOf(null) }

    val shouldShowErrorDialog = viewModel.shouldShowErrorDialog.collectAsState()
    val errors = viewModel.errors.collectAsState()

    // ad views count, ad plays every 2 clicks or on first try
    val shouldShowAd = viewModel.shouldShowAd.collectAsState()
    val interstitialAdViewCount = viewModel.adViewCount.collectAsState()

    val hasFlickered = viewModel.sharedViewModel.hasFlickered.collectAsState()

    val textToSpeechAudioFiles = viewModel.fileIds.collectAsState()

    val showShareDialog = remember { mutableStateOf(false) }
    val showReportDialog = remember { mutableStateOf(false) }
    val shareSolution = remember { mutableStateOf(true) }
    val shareImage = remember { mutableStateOf(true) }
    val shareOcrResult = remember { mutableStateOf(true) }
    val shareUserTask = remember { mutableStateOf(true) }


    handleSolutionRegeneration(
        requestSolutionResponse.value,
        viewModel,
        isWebViewReload
    )

    maybeShowAd(
        context = context,
        secretShowAd = secretShowAd,
        solutionResults = solutionResults.value,
        shouldShowAd = shouldShowAd.value,
        adViewCount = interstitialAdViewCount.value,
        viewModel = viewModel
    )

    BackHandler {}

    // ResultScreenContent itself provides the whole-screen Column and calls
    // SolutionAudioPlayer internally -- not wrapped in ApplicationScaffold
    // here, since this screen is already composed inside the
    // navigation-level one, and a second nested Scaffold duplicated its
    // system-bar inset reservation.
    ResultScreenContent(
        viewModel = viewModel,
        solutionResults = solutionResults.value,
        selectedSolutionService = selectedSolutionService.value,
        solutionProgress = solutionProgress.value,
        solutionTextDirection = solutionTextDirection.value,
        webView = webView,
        isWebViewReload = isWebViewReload,
        ocrResult = ocrResult,
        aiSolution = aiSolution,
        hasFlickered = hasFlickered.value,
        updateHasFlickered = { viewModel.sharedViewModel.updateHasFlickered(it) },
        showShareDialog = showShareDialog, // or use a regular reference
        showReportDialog = showReportDialog,
        shareSolution = shareSolution,
        shareImage = shareImage,
        shareOcrResult = shareOcrResult,
        shareUserTask = shareUserTask,
        onNavigateToOcrScreen = onNavigateToOcrScreen,
        context = context,
        taskTextLabel = stringResource(string.user_task_value),
        recognizedTextLabel = stringResource(string.ingredients),
        solutionTextLabel = stringResource(string.solution_text_value),
        solvedByStyleTranslator = stringResource(string.solved_by_diet_tracker),
        invalidSolutionText = stringResource(string.error_gemini_solution_result_extraction),
        solutionsGenerationProgress = stringResource(string.progress_bar_hint_text_value),
        errors = errors.value,
        shouldShowErrorDialog = shouldShowErrorDialog.value,
        isDebugMode = BuildConfig.DEBUG,
        chooseSharing = stringResource(string.choose_sharing),
        share = stringResource(string.share),
        shareOcrResultLabel = stringResource(string.share_recognized_ingredients),
        shareSolutionLabel = stringResource(string.share_solution),
        shareUserText = stringResource(string.share_user_ingredients),
        shareImageLabel = stringResource(string.share_image),
        textToSpeechAudioFiles = textToSpeechAudioFiles.value,
        appName = "diettracker"
    )
}