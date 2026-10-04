package com.styletranslator.presentation.screens.output.result

import com.example.shared.domain.ai.ResponseFormat
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.domain.usecases.ai.client.GeminiUseCaseClient
import com.example.shared.domain.usecases.ai.GigaChatUseCase
import com.example.shared.domain.usecases.ai.GroqUseCase
import com.example.shared.domain.usecases.AudioPlayer
import com.example.shared.domain.usecases.ImageUtils
import com.example.shared.ads.InterstitialAdUseCase
import com.example.shared.presentation.screens.AIService
import com.example.shared.presentation.screens.output.result.BaseResultViewModel
import com.example.shared.presentation.screens.output.result.doubleQuotes
import com.example.shared.presentation.screens.output.result.jsonResponseLanguage
import com.example.shared.presentation.screens.output.result.ocrTextJsonEntry
import com.intelliverse.llama.LocalLlamaSession
import com.intelliverse.llama.TranslationPrompts
import com.intelliverse.models.LocalModelSeed
import com.intelliverse.models.ModelStore
import com.intelliverse.models.TranslationModels
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@HiltViewModel
class ResultViewModel @Inject constructor(
    imageUtils: ImageUtils,
    geminiUseCaseClient: GeminiUseCaseClient,
    groqUseCase: GroqUseCase,
    gigaChatUseCase: GigaChatUseCase,
    interstitialAdUseCase: InterstitialAdUseCase,
    speechConverter: SpeechConverter,
    audioPlayer: AudioPlayer,
    private val localLlamaSession: LocalLlamaSession,
    @ApplicationContext appContext: Context,
    savedStateHandle: SavedStateHandle
) : BaseResultViewModel(imageUtils, geminiUseCaseClient, groqUseCase, gigaChatUseCase, interstitialAdUseCase, speechConverter, audioPlayer, savedStateHandle) {

    override val audioPrefixName: String
        get() = "styletranslator"

    private val modelStore = ModelStore(appContext)

    /**
     * On-device local models as additional parallel tabs alongside
     * Gemini/Groq/GigaChat -- literal translation only (source text plus a
     * target language code), not this screen's own elaborate style/tone/
     * gender/mentality prompt: these are small, specialized translation
     * models, not general instruction followers, and can't reliably follow
     * that whole prompt+JSON-output contract the way a cloud model does.
     * Skipped entirely for an image-based run: none of the catalog models
     * take image input, and there's no extracted OCR text ready at this
     * point (that only exists after Gemini's own vision call resolves).
     */
    // Exactly one local result per run (see runLocalModels), not one per
    // installed model -- so this is 0 or 1, never a model count.
    override suspend fun additionalProviderCount(): Int =
        if (imageUsed) 0 else if (ALL_LOCAL_MODELS.any { (seed, _) -> modelStore.isInstalled(seed) }) 1 else 0

    override fun CoroutineScope.launchAdditionalProviders(imagesBase64: List<String>) {
        if (!imageUsed) launch { runLocalModels() }
    }

    /**
     * Walks [ALL_LOCAL_MODELS] top to bottom -- TranslateGemma, then MADLAD,
     * then OmniTranslate last (weakest quality, see its own catalog note) --
     * and reports exactly one result: the first installed model that loads
     * and produces a translation. Anything earlier in the list that fails
     * to load or generate is skipped silently, no separate tab for it.
     *
     * Sequential, not one launch{} per model: [LocalLlamaSession] only ever
     * holds one loaded model at a time (matches this app's realistic phone
     * RAM budget), so running candidates one after another is both simpler
     * and avoids two coroutines racing to load/evict each other's model.
     * It also means whichever model answered last run is still the one
     * resident in memory -- [LocalLlamaSession.load] is then a no-op, so as
     * long as the top-of-list model keeps succeeding, every run after the
     * first is instant with no reload.
     */
    private suspend fun runLocalModels() {
        val targetLangCode = selectedLanguage.code
        val targetLangName = selectedLanguage.promptName
        val sourceText = passedEditedResult.ifBlank { userTask }

        var lastFailure: Throwable = IllegalStateException("No installed local model produced a translation")
        var lastAttempted: AIService? = null
        for ((seed, aiService) in ALL_LOCAL_MODELS) {
            if (!modelStore.isInstalled(seed)) continue
            lastAttempted = aiService
            val result = runLocalModel(seed, aiService, targetLangCode, targetLangName, sourceText)
            result.onSuccess {
                onSolutionResult(Result.success(it), aiService)
                return
            }
            result.onFailure { lastFailure = it }
        }
        lastAttempted?.let { onSolutionResult(Result.failure(lastFailure), it) }
    }

    /** Loads and runs a single candidate model; does not report -- the caller reports exactly once for the whole run. */
    private suspend fun runLocalModel(
        seed: LocalModelSeed,
        aiService: AIService,
        targetLangCode: String,
        targetLangName: String,
        sourceText: String,
    ): Result<String> {
        return try {
            val loaded = localLlamaSession.load(modelStore.finalFile(seed).absolutePath)
            if (!loaded) return Result.failure(IllegalStateException("Failed to load ${seed.title}"))

            val prompt = TranslationPrompts.buildPrompt(seed, targetLangCode, targetLangName, sourceText)
            val raw = StringBuilder()
            localLlamaSession.generate(prompt).collect { token -> raw.append(token) }
            val cleaned = TranslationPrompts.stripThinking(raw.toString())
            Result.success(formatLocalResult(cleaned, seed.title))
        } catch (e: Exception) {
            Timber.w(e, "Local model ${seed.id} failed to produce a translation")
            Result.failure(e)
        }
    }

    /**
     * A short translation (a word or two, the common case -- someone reads
     * it and taps Play) doesn't need a full-size "— Local (Model)" footer
     * eating space below it; the model name still shows, just small and
     * above the text instead. Longer, multi-line output keeps the footer
     * as before -- there the extra line at the bottom costs nothing.
     */
    private fun formatLocalResult(cleaned: String, modelTitle: String): String {
        val lineCount = cleaned.count { it == '\n' } + 1
        val isShort = lineCount <= SHORT_TEXT_MAX_LINES && cleaned.length <= SHORT_TEXT_MAX_CHARS
        return if (isShort) {
            "<small style=\"opacity:0.6\">Local — $modelTitle</small><br>$cleaned"
        } else {
            withProviderFooter(cleaned, "Local", modelTitle)
        }
    }

    private var tonePreference: String? = null
    private var style: String? = null
    private var mentality: String? = null
    private var translationScale: Float? = null
    private var transformationLevel = ""
    private var category: String? = null
    private var sourceGender: String? = null
    private var targetGender: String? = null
    private var sourceAge: Int? = null
    private var targetAge: Int? = null

    init {
        savedStateHandle.get<String>("tonePreference")?.let { tonePreference = it }
        savedStateHandle.get<String>("style")?.let { style = it }
        savedStateHandle.get<String>("mentality")?.let { mentality = it }
        savedStateHandle.get<String?>("translationScale")
            ?.toFloatOrNull()
            ?.let { translationScale = it }
        savedStateHandle.get<String>("transformationLevel")?.let { transformationLevel = it }
        savedStateHandle.get<String>("category")?.let { category = it }
        savedStateHandle.get<String>("sourceGender")?.let { sourceGender = it }
        savedStateHandle.get<String>("targetGender")?.let { targetGender = it }
        savedStateHandle.get<String?>("sourceAge")?.toIntOrNull()?.let { sourceAge = it }
        savedStateHandle.get<String?>("targetAge")?.toIntOrNull()?.let { targetAge = it }

        fun buildSolvingPrompt(): String {
            val description = if (imageUsed) {
                "Text to be translated is provided in the attached images.\n${
                    addText(
                        userTask,
                        additional = true
                    )
                }"
            } else if (passedEditedResult.isNotBlank()) {
                "${addText(passedEditedResult, additional = false)}\n\n${addText(userTask, additional = true)}"
            } else {
                addText(userTask, additional = false)
            }

            return """
            
    - Generate a JSON response that includes a translation of the given text according to the specified input parameters.
    - The response **must include a "titles" section** containing localized section headers. **Use the exact keys from the example JSON below for "titles"**.
    - The response must **translate** section titles (inside "titles") into ${selectedLanguage.promptName}.
    
    ### **Requirements for AI Processing:**
    1. **Translate the given text** into the target language while preserving the original intent.
    2. **Apply the specified input parameters below** during translation. $transformationLevel
    3. Do not preserve the speaker’s and receiver's original gender, age, or voice unless it matches the source and target parameters. Rephrase and adapt expressions, references, and relationships as needed to fully embody the new perspective.
    ${
                if (translationScale != null) {
                    "3. The translated text should be approximately $translationScale times its original length while maintaining coherence."
                } else ""
            }

    ### **Input Parameters:**
    ${if (description.isNotBlank()) "- **Texts to translate:** $description" else ""}
    
    - **Target Language:** ${selectedLanguage.promptName}
      - The language into which the text should be translated.
      - The output will be fully localized, including grammar, idioms, and cultural nuances.
      
    ${if (sourceAge != null) {"""
    - **Source Age:** $sourceAge
      - The age of the **person speaking** in the translated text.
      - Affects **how the text is written**: word choice, complexity, life perspective, and cultural references.
      - Example: A **child's speech** uses **simpler words**, while an **elderly person's speech** is **more reflective and mature**."""
    } else ""}

    ${if (targetAge != null) {"""
    - **Target Age:** $targetAge
      - The age of the **intended audience** receiving the translated text.
      - Affects **how the text is adapted**: clarity, readability, and tone.
      - Example: A **child's translation** should be **playful and engaging**, while an **elderly person's translation** should be **polite and refined**."""
    } else ""}
    
    ${if (sourceGender != null) {"""
    - **Source Gender:** $sourceGender
      - The gender of the **person speaking** in the translated text.
      - The translation **must adjust** not only **pronouns** but also **perspective, relationship roles, and emotional framing**.
      - Example:
        - **Male → Female Flip:** *"I take care of my girlfriend"* → *"I love how my boyfriend takes care of me."*
        - **Female → Male Flip:** *"I feel safe with my boyfriend"* → *"I always protect my girlfriend."*"""
    } else ""}
    
    ${if (targetGender != null) {"""
    - **Target Gender:** $targetGender
      - The gender of the **person receiving** the translated text.
      - Affects **pronoun usage, word choice, and emotional tone** to match the audience’s perspective.
      - Example:
        - **Target Gender: Male** → Uses masculine forms in gendered languages.
        - **Target Gender: Female** → Uses feminine forms in gendered languages."""
    } else ""}

    ${if (category != null) {"""
    - **Category:** $category
      - Defines the **context** in which the translated text is used (e.g., Relationship, Workplace, Education).  
      - Affects **word choice, formality, and phrasing** to match the given context."""
    } else ""}

    ${if (tonePreference != null) {"""
    - **Tone:** $tonePreference
      - Determines the **emotional and expressive quality** of the translation.  
      - The translation will adjust **sentence structure, word emphasis, and style accordingly.**"""
    } else ""}

    ${if (style != null) {"""
    - **Style:** $style
      - Specifies the **writing style** applied to the translation.  
      - Affects **text structure, vocabulary, and flow**."""
    } else ""}          

    ${if (mentality != null) {"""
    - **Mentality:** $mentality
      - Defines the **cultural and cognitive perspective** applied to the translation.  
      - Influences **word choice, phrasing, and expression** based on common ways of thinking in the selected mentality."""
    } else ""}
      

    Strictly format the response as a JSON object with the following structure:
    - "titles" (Map<String, String>) - A map of **localized section headers** (must match the keys in the example JSON).
    - "translatedText" (String) - The translated text in the specified language, considering category, tone, style, and mentality.
    ${ocrTextJsonEntry(imageUsed)}
    
    Example Output:
    {
      "titles": {
          "translated_text": "<'Translated Text' translated>",
      },
      ${if (imageUsed) {""""ocrText": "I am delighted to take care of my girlfriend, always ensuring that she feels loved and protected.","""} else ""}
      "translatedText": "I feel so lucky to have a boyfriend who always makes me feel loved and protected.",
    }
    
    $doubleQuotes
    ${jsonResponseLanguage(selectedLanguage.promptName)}
""".trimIndent()

        }
        prompt = buildSolvingPrompt()
    }

    override fun decodeSolutionResponse(response: String): Pair<String, String> = decodeStyleSolutionResponse(response)

    /** Gemini/Groq are asked for [StyleSolutionResponse] as structured JSON; [decodeStyleSolutionResponse] still decodes it. */
    override val responseFormat: ResponseFormat = ResponseFormat.Json.of("style_solution", StyleSolutionResponse.serializer())

    private companion object {
        // Attempt order for runLocalModels -- OmniTranslate deliberately
        // last, see its own catalog note on why (weaker quality, especially
        // rare/RTL languages).
        val ALL_LOCAL_MODELS: List<Pair<LocalModelSeed, AIService>> = listOfNotNull(
            TranslationModels.byId("translategemma-4b")?.let { it to AIService.LOCAL_TRANSLATEGEMMA },
            TranslationModels.byId("madlad400-3b-mt-q4")?.let { it to AIService.LOCAL_MADLAD },
            TranslationModels.byId("omnitranslate-1-1")?.let { it to AIService.LOCAL_OMNITRANSLATE },
        )

        // "Short" = the common one/two-word translation someone reads and
        // immediately taps Play on -- see formatLocalResult.
        const val SHORT_TEXT_MAX_LINES = 2
        const val SHORT_TEXT_MAX_CHARS = 160
    }
}