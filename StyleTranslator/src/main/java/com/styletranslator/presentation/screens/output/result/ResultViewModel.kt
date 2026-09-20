package com.styletranslator.presentation.screens.output.result

import androidx.lifecycle.SavedStateHandle
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.domain.usecases.ai.client.GeminiUseCaseClient
import com.example.shared.domain.usecases.ai.OpenAiUseCase
import com.example.shared.domain.usecases.AudioPlayer
import com.example.shared.domain.usecases.ImageUtils
import com.example.shared.ads.InterstitialAdUseCase
import com.example.shared.presentation.screens.output.result.BaseResultViewModel
import com.example.shared.presentation.screens.output.result.doubleQuotes
import com.example.shared.presentation.screens.output.result.jsonResponseLanguage
import com.example.shared.presentation.screens.output.result.ocrTextJsonEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
class ResultViewModel @Inject constructor(
    imageUtils: ImageUtils,
    geminiUseCaseClient: GeminiUseCaseClient,
    openAiUseCase: OpenAiUseCase,
    interstitialAdUseCase: InterstitialAdUseCase,
    speechConverter: SpeechConverter,
    audioPlayer: AudioPlayer,
    savedStateHandle: SavedStateHandle
) : BaseResultViewModel(imageUtils, geminiUseCaseClient, openAiUseCase, interstitialAdUseCase, speechConverter, audioPlayer, savedStateHandle) {

    override val audioPrefixName: String
        get() = "styletranslator"

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
    - The response **must include a \"titles\" section** containing localized section headers. **Use the exact keys from the example JSON below for \"titles\"**.
    - The response must **translate** section titles (inside \"titles\") into ${selectedLanguage.languageName}.
    
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
    
    - **Target Language:** ${selectedLanguage.languageName}
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
        - **Male → Female Flip:** *\"I take care of my girlfriend\"* → *\"I love how my boyfriend takes care of me.\"*
        - **Female → Male Flip:** *\"I feel safe with my boyfriend\"* → *\"I always protect my girlfriend.\"*"""
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
    - \"titles\" (Map<String, String>) - A map of **localized section headers** (must match the keys in the example JSON).
    - \"translatedText\" (String) - The translated text in the specified language, considering category, tone, style, and mentality.
    ${ocrTextJsonEntry(imageUsed)}
    
    Example Output:
    {
      \"titles\": {
          \"translated_text\": \"<'Translated Text' translated>\",
      },
      ${if (imageUsed) {"""\"ocrText\": \"I am delighted to take care of my girlfriend, always ensuring that she feels loved and protected.\","""} else ""}
      \"translatedText\": \"I feel so lucky to have a boyfriend who always makes me feel loved and protected.\",
    }
    
    $doubleQuotes
    ${jsonResponseLanguage(selectedLanguage.languageName)}
""".trimIndent()

        }
        prompt = buildSolvingPrompt()
    }

    override fun decodeSolutionResponse(response: String): Pair<String, String> = decodeStyleSolutionResponse(response)
}