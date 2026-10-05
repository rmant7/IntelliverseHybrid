package com.styletranslator.presentation.screens.output.result

import com.example.shared.domain.ai.ResponseFormat
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
import ai.localstudio.sdk.Language
import ai.localstudio.sdk.LocalAi
import ai.localstudio.sdk.LocalCapability
import ai.localstudio.sdk.LocalModel
import ai.localstudio.sdk.ModelQuery
import ai.localstudio.sdk.TranslationRequest
import com.intelliverse.localai.LocalAiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val localAi: LocalAi,
    private val localAiSettings: LocalAiSettings,
    savedStateHandle: SavedStateHandle
) : BaseResultViewModel(imageUtils, geminiUseCaseClient, groqUseCase, gigaChatUseCase, interstitialAdUseCase, speechConverter, audioPlayer, savedStateHandle) {

    override val audioPrefixName: String
        get() = "styletranslator"

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
        if (imageUsed) 0 else if (translators().isNotEmpty()) 1 else 0

    /** Every installed model that translates, through the SDK -- the Models screen decides which goes first. */
    private suspend fun translators(): List<LocalModel> =
        runCatching { localAi.models(ModelQuery(capability = LocalCapability.TRANSLATION)) }.getOrDefault(emptyList())

    override fun CoroutineScope.launchAdditionalProviders(imagesBase64: List<String>) {
        if (!imageUsed) launch { runLocalModels() }
    }

    /**
     * Exactly one local result per run, through the SDK: the translation
     * model chosen on the Models screen first, then every other installed
     * translator in turn -- the first that answers is the result. One model
     * is loaded at a time (the SDK serializes), so whichever answered stays
     * loaded for the next run.
     */
    private suspend fun runLocalModels() {
        val request = TranslationRequest(
            text = passedEditedResult.ifBlank { userTask },
            source = Language("auto", "the source language"),
            target = Language(selectedLanguage.code, selectedLanguage.promptName),
        )
        val chosen = localAiSettings.translationModelId
        // Chosen first; then dedicated translators (TranslateGemma, MADLAD, OmniTranslate -- catalog
        // order, OmniTranslate last for its weaker quality) before chat models asked to translate.
        val models = translators().sortedWith(
            compareByDescending<LocalModel> { it.id == chosen }.thenBy { LocalCapability.TEXT in it.capabilities },
        )
        var lastFailure: Throwable = IllegalStateException("No installed local model produced a translation")
        for (model in models) {
            val result = runCatching { localAi.translate(request, model.id) }
            result.onSuccess { text ->
                onSolutionResult(Result.success(formatLocalResult(text, model.displayName)), serviceFor(model.id))
                return
            }
            result.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Timber.w(it, "Local translation by ${model.id} failed")
                lastFailure = it
            }
        }
        models.lastOrNull()?.let { onSolutionResult(Result.failure(lastFailure), serviceFor(it.id)) }
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
        /** The result tab a local model's answer lands in: its own for the three translation models, one shared for any other. */
        fun serviceFor(modelId: String): AIService = when (modelId) {
            "translategemma-4b" -> AIService.LOCAL_TRANSLATEGEMMA
            "madlad400-3b-mt-q4" -> AIService.LOCAL_MADLAD
            "omnitranslate-1-1" -> AIService.LOCAL_OMNITRANSLATE
            else -> AIService.LOCAL
        }

        // "Short" = the common one/two-word translation someone reads and
        // immediately taps Play on -- see formatLocalResult.
        const val SHORT_TEXT_MAX_LINES = 2
        const val SHORT_TEXT_MAX_CHARS = 160
    }
}