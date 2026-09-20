package com.example.shared.presentation.screens.output.result

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shared.UnableToAssistException
import com.example.shared.ads.InterstitialAdUseCase
import com.example.shared.data.network.gemini_api.client.GeminiApiService
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.domain.usecases.AudioPlayer
import com.example.shared.domain.usecases.AudioPlayer.Companion.playbackSpeeds
import com.example.shared.domain.usecases.ImageUtils
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.domain.usecases.TextUtils
import com.example.shared.domain.usecases.ai.GigaChatUseCase
import com.example.shared.domain.usecases.ai.GrokUseCase
import com.example.shared.domain.usecases.ai.OpenAiUseCase
import com.example.shared.domain.usecases.ai.client.GeminiUseCaseClient
import com.example.shared.presentation.screens.AIService
import com.example.shared.presentation.screens.output.SharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

abstract class BaseResultViewModel(
    private val imageUtils: ImageUtils,
    private val geminiUseCaseClient: GeminiUseCaseClient,
    private val openAiUseCase: OpenAiUseCase,
    private val grokUseCase: GrokUseCase,
    private val gigaChatUseCase: GigaChatUseCase,
    private val interstitialAdUseCase: InterstitialAdUseCase,
    protected val speechConverter: SpeechConverter,
    val audioPlayer: AudioPlayer,
    savedStateHandle: SavedStateHandle
): ViewModel() {

    abstract val audioPrefixName: String
    var passedImageUris: List<Uri> = emptyList()
    var userTask: String = ""
    var prompt = ""
    protected var imageUsed = false
    private var generativeLanguageURLs: MutableList<String> = mutableListOf()
    protected var passedEditedResult: String = ""

    /** Solutions max capacity: GEMINI_THINKING, GPT, GROK, GIGACHAT */
    private val maxSolutionResultsCapacity = 4
    private val geminiAttempts: AtomicInteger = AtomicInteger(2)
    private val geminiThinkingAttempts: AtomicInteger = AtomicInteger(2)
    /** System instructions and OpenAI prompt*/
    protected var selectedLanguage: SolutionLanguageOption

    init {
        val selectedLanguageIndex = savedStateHandle.get<Int>("selectedLanguageIndex") ?: 0
        this.selectedLanguage = SolutionLanguageOption.getByIndex(selectedLanguageIndex)
        val locale = Locale.forLanguageTag(selectedLanguage.languageTag)
        speechConverter.setLanguage(locale)
        speechConverter.onUtteranceFinished = { addFile(it) }

        savedStateHandle.get<String>("userTask")?.let { userTask = it }
        savedStateHandle.get<String>("passedEditedResult")?.let { passedEditedResult = it }
        val encodedUris = savedStateHandle.get<String>("passedImageUris")
        passedImageUris = encodedUris
            ?.split(",")
            ?.mapNotNull { encoded -> Uri.decode(encoded).takeIf { it.isNotBlank() }?.let { Uri.parse(it) } }
            ?: emptyList()

        imageUsed = passedImageUris.isNotEmpty() && passedEditedResult.isBlank()
    }

    private var _sharedViewModel: SharedViewModel? = null
    val sharedViewModel: SharedViewModel
        get() = _sharedViewModel
            ?: throw IllegalStateException("SharedViewModel is not initialized")
    fun initSharedViewModel(sharedViewModel: SharedViewModel) {
        _sharedViewModel = sharedViewModel
    }

    private val _solutionResults = MutableStateFlow<Map<AIService, String?>>(emptyMap())
    val solutionResults: StateFlow<Map<AIService, String?>> =
        _solutionResults.asStateFlow()

    private fun clearSolutionResults() {
        _solutionResults.update { emptyMap() }
    }

    /** Text to speech converted audio files */
    // Ids for tracking synthesizing process of saved audio files.
     val fileIds = MutableStateFlow<MutableList<String>>(mutableListOf())

    private val _currentSpeedIndex = MutableStateFlow(1)
     val currentSpeedIndex: StateFlow<Int> = _currentSpeedIndex

     fun cycleSpeed(): Int {
        _currentSpeedIndex.value = (_currentSpeedIndex.value + 1) % playbackSpeeds.size
        return _currentSpeedIndex.value
    }

    private fun addFile(utteranceId: String?) {
        fileIds.update { files ->
            files.toMutableList().apply {
                utteranceId?.let { add(it) }
            }
        }
    }

    private fun removeFile(utteranceId: String?) {
        fileIds.update {
            fileIds.value.toMutableList().apply {
                this.remove(utteranceId)
            }
        }
    }

     fun stopUtterance() {
        speechConverter.stopUtterance()
    }

    /** Errors */
    private val _errors = MutableStateFlow<MutableList<Throwable>>(mutableListOf())
    val errors: StateFlow<MutableList<Throwable>> = _errors

    protected fun updateErrors(error: Throwable) {
        _errors.update {
            it.apply { it.add(error) }
        }
    }

    private fun clearErrors() {
        _errors.update { mutableListOf() }
    }

    /** Should show error dialog condition */
    private val _shouldShowErrorDialog = MutableStateFlow(false)
    val shouldShowErrorDialog: StateFlow<Boolean> = _shouldShowErrorDialog

    fun updateShouldShowErrorDialog(shouldShowErrorDialog: Boolean) {
        _shouldShowErrorDialog.update { shouldShowErrorDialog }
    }

    /** Interstitial ad count */
    private val _adViewCount = MutableStateFlow(0)
    val adViewCount: StateFlow<Int> = _adViewCount

    private fun updateInterstitialAdViewCount(adViewCount: Int) {
        _adViewCount.update { adViewCount }
    }

    fun increaseInterstitialAdViewCount() {
        var currentVal = adViewCount.value
        val increasedNumber = currentVal + 1
        currentVal = if (increasedNumber >= 2) 0 else increasedNumber
        updateInterstitialAdViewCount(currentVal)
    }

    /** Show ad while waiting for solution results */
    fun showInterstitialAd(context: Context) {
        val isAdShown = interstitialAdUseCase.show(context)
        if (isAdShown) updateShouldShowAd(false)
    }

    private val _shouldShowAd = MutableStateFlow(true)
    val shouldShowAd: StateFlow<Boolean> = _shouldShowAd

    fun updateShouldShowAd(shouldShowAd: Boolean) {
        _shouldShowAd.update { shouldShowAd }
    }

    /** Update solution condition */
    private val _requestSolutionResponse = MutableStateFlow(true)
    val requestSolutionResponse: StateFlow<Boolean> = _requestSolutionResponse

     fun updateRequestSolutionResponse(requestSolutionResponse: Boolean) {
        _requestSolutionResponse.update { requestSolutionResponse }
    }

    /** Selected solution service */
    private val _selectedSolutionService = MutableStateFlow<AIService?>(null)
    val selectedSolutionService: StateFlow<AIService?> = _selectedSolutionService

    fun updateSelectedSolutionService(selectedSolutionService: AIService?) {
        _selectedSolutionService.update { selectedSolutionService }
    }

    private fun updateSolutionResults(
        aiService: AIService,
        resultText: String?
    ) {
        if (_selectedSolutionService.value == null && resultText != null) {
            updateSelectedSolutionService(aiService)
        }
        _solutionResults.update { oldMap ->
            oldMap + (aiService to resultText)
        }
    }

    /** Solution progress */
    private val _solutionProgress = MutableStateFlow(0f)
    val solutionProgress: StateFlow<Float> = _solutionProgress

    private fun updateSolutionProgress(solutionProgress: Float) {
        _solutionProgress.update { solutionProgress }
    }

    /** Solution text direction */
    private val _solutionTextDirection =
        MutableStateFlow(TextUtils.getTextDirection(selectedLanguage))
    val solutionTextDirection: StateFlow<LayoutDirection> = _solutionTextDirection

    fun updateSolutionTextDirection(solutionTextDirection: LayoutDirection) {
        _solutionTextDirection.update { solutionTextDirection }
    }

    // When AI solution result fetched
    private fun onSolutionResult(
        result: Result<String>,
        aiService: AIService
    ) {

        if (_solutionResults.value.containsKey(aiService)) {
            return
        }

        result.onSuccess {

            if (it.isBlank()) {
                updateSolutionResults(aiService, null)
                return
            }

            // convert markdown to html
            val htmlString = toHtml(it)
            updateSolutionResults(aiService, htmlString)

            // Replace existing or create new audio file.
            val fileName = "${audioPrefixName}_solution_${aiService.ordinal}.wav"
            removeFile(fileName)
            speechConverter.synthesizeToFile(it, fileName)
        }

        result.onFailure {
            when (aiService) {
                AIService.GEMINI -> {
                    val remainedAttempts = geminiAttempts.decrementAndGet()
                    if (remainedAttempts == 0) {
                        // all gemini attempts have failed
                        updateSolutionResults(aiService, null)
                    }
                }
                AIService.GEMINI_THINKING -> {
                    val remainedAttempts = geminiThinkingAttempts.decrementAndGet()
                    if (remainedAttempts == 0) {
                        // all gemini attempts have failed
                        updateSolutionResults(aiService, null)
                    }
                }
                AIService.GPT, AIService.GROK, AIService.GIGACHAT -> {
                    updateSolutionResults(aiService, null)
                }
            }
            /*
            // maxSolutionResultsCapacity is not maintained
            updateErrors(it)
            if (errors.value.size >= maxSolutionResultsCapacity) {
                updateShouldShowErrorDialog(true)
            }*/
        }

        val progress = solutionResults.value.size.toFloat() / maxSolutionResultsCapacity
        updateSolutionProgress(progress)
    }

    private suspend fun setGenerativeLangUrls() {
        if (imageUsed && generativeLanguageURLs.isEmpty()) {
            passedImageUris.forEach { passedImageUri ->
                val fileByteArray = imageUtils.convertUriToByteArray(passedImageUri)
                if (fileByteArray != null) {
                    val uploadResult = geminiUseCaseClient.getUploadedImageUrl(
                        fileByteArray = fileByteArray,
                        fileName = passedImageUri.toString()
                    )
                    uploadResult.onSuccess { generativeLanguageURLs.add(it) }
                }
            }
        }
    }

    fun generateSolutions() = viewModelScope.launch(Dispatchers.IO) {
        clearSolutionResults()
        clearErrors()
        updateSelectedSolutionService(null)
        updateSolutionProgress(0.0f)
        geminiAttempts.set(2)
        geminiThinkingAttempts.set(2)
        setGenerativeLangUrlsAndSolveGeminiWithinApp()

        val imagesBase64 = if (imageUsed) {
            passedImageUris.mapNotNull { imageUtils.convertUriToByteArray(it) }
                .map { Base64.encodeToString(it, Base64.NO_WRAP) }
        } else {
            emptyList()
        }

        if (imageUsed && imagesBase64.isEmpty()) {
            onSolutionResult(Result.failure(UnableToAssistException), AIService.GPT)
            onSolutionResult(Result.failure(UnableToAssistException), AIService.GROK)
        } else {
            gpt(imagesBase64)
            grok(imagesBase64)
        }
        gigaChat()
    }

    private fun gpt(imagesBase64: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        val result = openAiUseCase.generateOpenAiSolution(
            imagesBase64 = imagesBase64,
            prompt = prompt
        )
        result.onSuccess {
            try {
                val decodedResponse = decodeSolutionResponse(it)
                onSolutionResult(Result.success(decodedResponse.first), AIService.GPT)
                if (imageUsed && sharedViewModel.ocrResults.value[AIService.GPT].isNullOrBlank()) {
                    sharedViewModel.updateOcrResults(
                        AIService.GPT,
                        decodedResponse.second,
                        override = false
                    )
                }
            } catch (e: SerializationException) {
                Timber.d("Failed to serialize response for GPT: ${e.message}")
                onSolutionResult(Result.failure(e), AIService.GPT)
            }
        }
        result.onFailure {
            onSolutionResult(Result.failure(it), AIService.GPT)
        }
    }

    private fun grok(imagesBase64: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        val result = grokUseCase.generateGrokSolution(
            imagesBase64 = imagesBase64,
            prompt = prompt
        )
        result.onSuccess {
            try {
                val decodedResponse = decodeSolutionResponse(it)
                onSolutionResult(Result.success(decodedResponse.first), AIService.GROK)
                if (imageUsed && sharedViewModel.ocrResults.value[AIService.GROK].isNullOrBlank()) {
                    sharedViewModel.updateOcrResults(
                        AIService.GROK,
                        decodedResponse.second,
                        override = false
                    )
                }
            } catch (e: SerializationException) {
                Timber.d("Failed to serialize response for Grok: ${e.message}")
                onSolutionResult(Result.failure(e), AIService.GROK)
            }
        }
        result.onFailure {
            onSolutionResult(Result.failure(it), AIService.GROK)
        }
    }

    /** GigaChat gets no image: its endpoint does not accept this app's inline-image request shape (see GigaChatUseCase). */
    private fun gigaChat() = viewModelScope.launch(Dispatchers.IO) {
        val result = gigaChatUseCase.generateGigaChatSolution(prompt = prompt)
        result.onSuccess {
            try {
                val decodedResponse = decodeSolutionResponse(it)
                onSolutionResult(Result.success(decodedResponse.first), AIService.GIGACHAT)
                if (imageUsed && sharedViewModel.ocrResults.value[AIService.GIGACHAT].isNullOrBlank()) {
                    sharedViewModel.updateOcrResults(
                        AIService.GIGACHAT,
                        decodedResponse.second,
                        override = false
                    )
                }
            } catch (e: SerializationException) {
                Timber.d("Failed to serialize response for GigaChat: ${e.message}")
                onSolutionResult(Result.failure(e), AIService.GIGACHAT)
            }
        }
        result.onFailure {
            onSolutionResult(Result.failure(it), AIService.GIGACHAT)
        }
    }

    private fun setGenerativeLangUrlsAndSolveGeminiWithinApp() = viewModelScope.launch(Dispatchers.IO) {
        setGenerativeLangUrls()
        //geminiWithinApp(GeminiApiService.GeminiModel.GEMINI_2_5_FLASH_LITE, AIService.GEMINI)
        geminiWithinApp(
            GeminiApiService.GeminiModel.GEMINI_2_5_FLASH,
            AIService.GEMINI_THINKING
        )
    }

    private fun geminiWithinApp(modelName: String, aiService: AIService) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = geminiUseCaseClient.generateGeminiSolution(
                generativeLanguageUrls = if (imageUsed) {
                    generativeLanguageURLs
                } else {
                    emptyList()
                },
                prompt = prompt,
                modelName = modelName
            )
            result.onSuccess {
                try {
                    val decodedResponse = decodeSolutionResponse(it)
                    onSolutionResult(Result.success(decodedResponse.first), aiService)
                    if (imageUsed && sharedViewModel.ocrResults.value[aiService].isNullOrBlank()) {
                        sharedViewModel.updateOcrResults(
                            aiService,
                            decodedResponse.second,
                            override = false
                        )
                    }
                } catch (e: SerializationException) {
                    onSolutionResult(Result.failure(e), aiService)
                    Timber.d("Failed to serialize response for $aiService: ${e.message}")
                }
            }
            result.onFailure {
                onSolutionResult(Result.failure(it), aiService)
            }
        }
    }

    protected open fun toHtml(content: String): String {
        val urlRegex = """(https?://\S+)""".toRegex()

        return urlRegex.replace(content) { matchResult ->
            val url = matchResult.value
            """<a href="$url" target="_blank">$url</a>"""
        }
    }

    /**
     * Returns the solution text which will be displayed on the ResultScreen
     * and the recognized-properties text which will be displayed on the OCRScreen
     */
    abstract fun decodeSolutionResponse(response: String): Pair<String, String>
}