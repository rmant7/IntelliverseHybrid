package com.example.shared.presentation.screens.output.result

import com.example.shared.domain.ai.ResponseFormat
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shared.BuildConfig
import com.example.shared.PhotoUnreadableException
import com.example.shared.ads.InterstitialAdUseCase
import com.example.shared.data.network.gemini_api.client.GeminiApiService
import com.example.shared.domain.language.Language
import com.example.shared.domain.language.LanguageRegistry
import com.example.shared.domain.usecases.AudioPlayer
import com.example.shared.domain.usecases.AudioPlayer.Companion.playbackSpeeds
import com.example.shared.domain.usecases.ImageUtils
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.domain.usecases.TextUtils
import com.example.shared.domain.usecases.ai.GigaChatUseCase
import com.example.shared.domain.usecases.ai.GroqUseCase
import com.example.shared.domain.usecases.ai.client.GeminiUseCaseClient
import com.example.shared.presentation.screens.AIService
import com.example.shared.presentation.screens.output.SharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

abstract class BaseResultViewModel(
    private val imageUtils: ImageUtils,
    private val geminiUseCaseClient: GeminiUseCaseClient,
    private val groqUseCase: GroqUseCase,
    private val gigaChatUseCase: GigaChatUseCase,
    private val interstitialAdUseCase: InterstitialAdUseCase,
    protected val speechConverter: SpeechConverter,
    val audioPlayer: AudioPlayer,
    savedStateHandle: SavedStateHandle,
    private val localChat: com.intelliverse.localai.LocalChatProvider,
): ViewModel() {

    abstract val audioPrefixName: String
    var passedImageUris: List<Uri> = emptyList()
    var userTask: String = ""
    var prompt = ""
    protected var imageUsed = false
    private var generativeLanguageURLs: MutableList<String> = mutableListOf()
    protected var passedEditedResult: String = ""

    /**
     * How many services [generateSolutions] is currently waiting on, for the
     * progress bar. Starts at 3 (GEMINI_THINKING, GPT, GROQ); GIGACHAT is a
     * fallback tried only when none of those three answer (see
     * [generateSolutions]), so it only joins the denominator when it
     * actually gets invoked for this run.
     */
    private var maxSolutionResultsCapacity = PRIMARY_SERVICES.size
    private val geminiAttempts: AtomicInteger = AtomicInteger(2)
    // Only one real attempt reaches Gemini now (the old server-relayed second
    // attempt was removed along with this app's own backend), so a single
    // failure must finalize this slot rather than waiting for a second
    // attempt that will never come.
    private val geminiThinkingAttempts: AtomicInteger = AtomicInteger(1)
    /** System instructions and OpenAI prompt*/
    protected var selectedLanguage: Language

    /** Whether the user picked the answer's language on the input screen (mini-apps without that choice have none). */
    private val languageChosen: Boolean = savedStateHandle.get<String>("selectedLanguageCode") != null

    init {
        val selectedLanguageCode = savedStateHandle.get<String>("selectedLanguageCode")
        this.selectedLanguage = selectedLanguageCode?.let { LanguageRegistry.byCode(it) } ?: LanguageRegistry.DEFAULT
        val locale = Locale.forLanguageTag(selectedLanguage.code)
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

    // When AI solution result fetched -- protected, not private: a sub-app
    // overriding launchAdditionalProviders() (StyleTranslator's local
    // models) reports its own results through this same path.
    protected fun onSolutionResult(
        result: Result<String>,
        aiService: AIService
    ) {

        if (_solutionResults.value.containsKey(aiService)) {
            return
        }

        result.onSuccess {

            if (it.isBlank()) {
                // A "successful" call with nothing usable in it (safety filter,
                // empty completion, ...) looks identical to no answer at all in
                // the UI, so without this line there is nothing in the log
                // explaining why a service that didn't error still shows blank.
                Timber.w("$aiService returned a blank response; treating as no answer")
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
            Timber.w(it, "$aiService failed to produce a solution")
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
                AIService.GPT, AIService.GROQ, AIService.GIGACHAT,
                AIService.LOCAL_TRANSLATEGEMMA, AIService.LOCAL_OMNITRANSLATE, AIService.LOCAL_MADLAD, AIService.LOCAL -> {
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
                    uploadResult
                        .onSuccess { generativeLanguageURLs.add(it) }
                        .onFailure { Timber.w(it, "Gemini image upload failed") }
                }
            }
        }
    }

    /**
     * Runs Gemini, Groq, and GigaChat all in parallel and waits for all
     * three. GigaChat was originally fallback-only (called after the others,
     * only if neither answered), changed to always-parallel so it can
     * actually be exercised/verified on demand instead of waiting for a
     * run where every other provider happens to fail. Its tab still sorts
     * last regardless (see [SharableResultScreen]'s orderedResults, sorted
     * by [AIService]'s own declared order with GIGACHAT last).
     */
    fun generateSolutions() = viewModelScope.launch(Dispatchers.IO) {
        clearSolutionResults()
        clearErrors()
        updateSelectedSolutionService(null)
        updateSolutionProgress(0.0f)
        geminiAttempts.set(2)
        geminiThinkingAttempts.set(1)
        if (imageUsed) {
            solvePhotoOnDevice()
            return@launch
        }
        // +1 for GigaChat, debug builds only (release ships no GigaChat key
        // at all -- see shared/build.gradle.kts and gigaChat()'s own gate
        // below); + however many extra providers this sub-app's own
        // launchAdditionalProviders() will actually run this time
        // (StyleTranslator: its installed local models) -- 0 for every other
        // sub-app, which never overrides this.
        // The on-device model answers too (Settings: on by default once one is installed); a photo run only when one can see.
        val askLocal = offersLocalChat && localChat.available(withImages = imageUsed)
        maxSolutionResultsCapacity = PRIMARY_SERVICES.size + (if (BuildConfig.DEBUG) 1 else 0) + (if (askLocal) 1 else 0) + additionalProviderCount()

        val imagesBase64 = if (imageUsed) {
            passedImageUris.mapNotNull { imageUtils.convertUriToByteArray(it) }
                .map { Base64.encodeToString(it, Base64.NO_WRAP) }
        } else {
            emptyList()
        }

        coroutineScope {
            launch {
                setGenerativeLangUrls()
                if (imageUsed && generativeLanguageURLs.isEmpty()) {
                    // Without this Gemini was asked about "the photo" with no
                    // photo attached, and answered anyway.
                    val failure = if (imagesBase64.isEmpty()) {
                        PhotoUnreadableException()
                    } else {
                        PhotoUnreadableException("The photo could not be uploaded to Gemini")
                    }
                    onSolutionResult(Result.failure(failure), AIService.GEMINI_THINKING)
                } else {
                    geminiWithinApp(GeminiApiService.GeminiModel.GEMINI_3_6_FLASH, AIService.GEMINI_THINKING)
                }
            }
            if (imageUsed && imagesBase64.isEmpty()) {
                onSolutionResult(Result.failure(PhotoUnreadableException()), AIService.GROQ)
            } else {
                // GPT (langchain4j's "demo" key, a shared worldwide
                // non-configurable quota that never once produced an
                // answer) has been removed entirely, along with langchain4j
                // itself -- see OpenAiUseCase's deletion and GroqUseCase's
                // rewrite onto direct HTTP.
                launch { groq(imagesBase64) }
            }
            if (BuildConfig.DEBUG) launch { gigaChat() }
            if (askLocal) launch { localModel(if (imageUsed) passedImageUris.mapNotNull { imageUtils.convertUriToByteArray(it) } else emptyList()) }
            launchAdditionalProviders(imagesBase64)
        }
    }

    private val _localModelAdvice = MutableStateFlow<com.intelliverse.localai.LocalModelAdvice?>(null)

    /** Set when a photo run found no on-device model that can take it: which one to get (the result screen's dialog). */
    val localModelAdvice: StateFlow<com.intelliverse.localai.LocalModelAdvice?> = _localModelAdvice

    fun dismissLocalModelAdvice() {
        _localModelAdvice.value = null
    }

    /**
     * For now a photo is answered on this phone only: no cloud model receives
     * it (Gemini, Groq, GigaChat and the sub-app's own extra providers are not
     * asked), whatever Settings says about on-device answers -- it is the only
     * answer. With no on-device model that can see and fits now, the run's
     * answer is [LocalModelNeededException] and [localModelAdvice] says which
     * model to get, or how much memory to free.
     */
    private suspend fun solvePhotoOnDevice() {
        maxSolutionResultsCapacity = 1
        val advice = localChat.adviceFor(withImages = true)
        if (advice != null) {
            _localModelAdvice.value = advice
            onSolutionResult(Result.failure(com.example.shared.domain.ai.LocalModelNeededException("Photos are answered on this phone only. ${advice.why}")), AIService.LOCAL)
            return
        }
        localModel(passedImageUris.mapNotNull { imageUtils.convertUriToByteArray(it) })
    }

    /**
     * How many extra results [launchAdditionalProviders] will produce this
     * run -- added to [maxSolutionResultsCapacity] so [solutionProgress]
     * still reaches 1f. Default 0 (no extra providers); overridden by
     * StyleTranslator to count its installed local models.
     */
    protected open suspend fun additionalProviderCount(): Int = 0

    /**
     * Whether this sub-app's runs also ask the on-device chat model (see
     * [com.intelliverse.localai.LocalChatProvider]). StyleTranslator says no:
     * it asks its translation model instead ([launchAdditionalProviders]).
     */
    protected open val offersLocalChat: Boolean = true

    /**
     * The same prompt the cloud models get, answered on the phone. A small
     * model often wraps the JSON asked for in a code fence or a sentence, or
     * answers in plain text: the object inside is decoded when there is one,
     * otherwise the answer is shown as written rather than thrown away.
     */
    private suspend fun localModel(photos: List<ByteArray>) {
        if (imageUsed && photos.isEmpty()) {
            onSolutionResult(Result.failure(PhotoUnreadableException()), AIService.LOCAL)
            return
        }
        val result = try {
            Result.success(localChat.answer(localPrompt(), photos.map(com.intelliverse.localai.LocalImages::jpeg), localSystemPrompt()))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result.onSuccess { answer ->
            val reply = answer.text
            val decodedResult = runCatching { decodeSolutionResponse(reply) }
                .recoverCatching { e ->
                    if (e is com.example.shared.domain.ai.NothingRecognizedException) throw e
                    decodeSolutionResponse(com.intelliverse.localai.LocalChatProvider.jsonIn(reply))
                }
            // Found nothing: a failure, like any provider's -- not the raw empty JSON shown as the answer.
            (decodedResult.exceptionOrNull() as? com.example.shared.domain.ai.NothingRecognizedException)?.let {
                onSolutionResult(Result.failure(it), AIService.LOCAL)
                return
            }
            val decoded = decodedResult.getOrNull()
            val text = decoded?.first?.takeIf { it.isNotBlank() } ?: reply
            onSolutionResult(Result.success(withProviderFooter(text, "On-device", answer.attribution)), AIService.LOCAL)
        }
        result.onFailure {
            // Routed, then refused at the load by every model (memory moved): the same offer as before the run.
            if (imageUsed && it is ai.localstudio.sdk.LocalAiException.NotEnoughMemory) _localModelAdvice.value = localChat.adviceFor(withImages = true)
            onSolutionResult(Result.failure(it), AIService.LOCAL)
        }
    }

    /**
     * The answer's language for an on-device model, said where a small model
     * keeps it. The cloud models follow the prompt's one closing line
     * ("Provide the Json response in English"); Gemma 4 E2B, asked about a
     * task written in Russian, answered in Russian (SchoolKiller, #163).
     */
    private fun answerLanguageRule(): String? = selectedLanguage.promptName.takeIf { languageChosen }?.let {
        "Write every text value of the answer -- solutions, explanations, titles -- in $it, " +
            "even when the task is written in another language. Only text read from the images stays in its original language."
    }

    /**
     * The choices made on the input screen, one short line each, for an
     * on-device model: they are all in [prompt] too, but spread through a
     * long text a small model follows only in part (see [answerLanguageRule]).
     * Each mini-app lists its own; empty by default.
     */
    protected open fun localRequirements(): List<String> = emptyList()

    /** [answerLanguageRule] and [localRequirements] as one block; null when there is nothing to stress. */
    private fun localRules(): String? = (listOfNotNull(answerLanguageRule()) + localRequirements())
        .takeIf { it.isNotEmpty() }
        ?.joinToString("\n", prefix = "Follow these choices exactly:\n") { "- $it" }

    private fun localSystemPrompt(): String? = localRules()

    /** The cloud models' prompt, with the choices again at its end, where a small model keeps them. */
    private fun localPrompt(): String = localRules()?.let { "$prompt\n\n$it" } ?: prompt

    /**
     * Extra result-producing coroutines beyond Gemini/Groq/GigaChat,
     * launched inside [generateSolutions]' own coroutineScope (so it still
     * waits for them before finishing) -- default none. StyleTranslator
     * overrides this to also run its installed on-device translation
     * models; every other sub-app leaves it as-is.
     */
    protected open fun CoroutineScope.launchAdditionalProviders(imagesBase64: List<String>) {}

    // TEMPORARY diagnostic: appended to the displayed answer text (after
    // decoding, never before -- some sub-apps' decodeSolutionResponse
    // parses the raw response as JSON, which this would break if applied
    // any earlier) so it's visible on-screen which provider AND which
    // specific model actually answered. Most useful for Groq, whose model
    // is now discovered per-account rather than fixed. Remove the helper
    // and its four call sites once multi-provider testing is done -- the
    // Log screen already records this regardless.
    protected fun withProviderFooter(text: String, provider: String, model: String): String =
        "$text\n\n— $provider ($model)"

    private suspend fun groq(imagesBase64: List<String>) {
        val result = groqUseCase.generateGroqSolution(
            imagesBase64 = imagesBase64,
            prompt = prompt,
            responseFormat = responseFormat,
        )
        result.onSuccess {
            try {
                val decodedResponse = decodeSolutionResponse(it)
                val textWithFooter = withProviderFooter(
                    decodedResponse.first, "Groq", groqUseCase.lastUsedModel ?: "unknown model"
                )
                onSolutionResult(Result.success(textWithFooter), AIService.GROQ)
                if (imageUsed && sharedViewModel.ocrResults.value[AIService.GROQ].isNullOrBlank()) {
                    sharedViewModel.updateOcrResults(
                        AIService.GROQ,
                        decodedResponse.second,
                        override = false
                    )
                }
            } catch (e: Exception) {
                onSolutionResult(Result.failure(e), AIService.GROQ)
            }
        }
        result.onFailure {
            onSolutionResult(Result.failure(it), AIService.GROQ)
        }
    }

    /**
     * Now always run in parallel with Gemini/Groq (was fallback-only,
     * called after them and only if neither answered -- changed so it can
     * actually be tested/verified instead of waiting for a run where every
     * other provider happens to fail). Gets no image either way: GigaChat's
     * endpoint does not accept this app's inline-image request shape (see
     * [GigaChatUseCase]).
     */
    private suspend fun gigaChat() {
        val result = gigaChatUseCase.generateGigaChatSolution(prompt = prompt)
        result.onSuccess {
            try {
                val decodedResponse = decodeSolutionResponse(it)
                val textWithFooter = withProviderFooter(decodedResponse.first, "GigaChat", "GigaChat-2")
                onSolutionResult(Result.success(textWithFooter), AIService.GIGACHAT)
                if (imageUsed && sharedViewModel.ocrResults.value[AIService.GIGACHAT].isNullOrBlank()) {
                    sharedViewModel.updateOcrResults(
                        AIService.GIGACHAT,
                        decodedResponse.second,
                        override = false
                    )
                }
            } catch (e: Exception) {
                onSolutionResult(Result.failure(e), AIService.GIGACHAT)
            }
        }
        result.onFailure {
            onSolutionResult(Result.failure(it), AIService.GIGACHAT)
        }
    }

    private suspend fun geminiWithinApp(modelName: String, aiService: AIService) {
        val result = geminiUseCaseClient.generateGeminiSolution(
            generativeLanguageUrls = if (imageUsed) {
                generativeLanguageURLs
            } else {
                emptyList()
            },
            prompt = prompt,
            modelName = modelName,
            responseFormat = responseFormat,
        )
        result.onSuccess {
            try {
                val decodedResponse = decodeSolutionResponse(it)
                val textWithFooter = withProviderFooter(decodedResponse.first, "Gemini", geminiUseCaseClient.lastUsedModel ?: modelName)
                onSolutionResult(Result.success(textWithFooter), aiService)
                if (imageUsed && sharedViewModel.ocrResults.value[aiService].isNullOrBlank()) {
                    sharedViewModel.updateOcrResults(
                        aiService,
                        decodedResponse.second,
                        override = false
                    )
                }
            } catch (e: Exception) {
                onSolutionResult(Result.failure(e), aiService)
            }
        }
        result.onFailure {
            onSolutionResult(Result.failure(it), aiService)
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

    /**
     * The answer shape this sub-app expects from the AI providers -- its own
     * response model, as [ResponseFormat.Json], when [decodeSolutionResponse]
     * parses JSON. Gemini and Groq turn it into native structured output;
     * GigaChat has no such mechanism and is unaffected. [decodeSolutionResponse]
     * stays the typed boundary: an answer that still doesn't decode fails
     * that provider only, exactly as before.
     */
    protected open val responseFormat: ResponseFormat = ResponseFormat.Text

    private companion object {
        /**
         * Used for [maxSolutionResultsCapacity]'s "+1" (GigaChat, always
         * attempted separately -- see [generateSolutions]) rather than to
         * gate GigaChat on these having failed, which is no longer how it
         * works. GPT (and its provider) has been removed entirely --
         * otherwise maxSolutionResultsCapacity would count a result that
         * never arrives, and solutionProgress would never reach 1f.
         */
        val PRIMARY_SERVICES = listOf(AIService.GEMINI_THINKING, AIService.GROQ)
    }
}