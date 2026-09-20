package com.schoolkiller.presentation.screens.output.result

import androidx.lifecycle.SavedStateHandle
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.domain.usecases.ai.client.GeminiUseCaseClient
import com.example.shared.domain.usecases.ai.GigaChatUseCase
import com.example.shared.domain.usecases.ai.GrokUseCase
import com.example.shared.domain.usecases.ai.OpenAiUseCase
import com.example.shared.domain.usecases.AudioPlayer
import com.example.shared.domain.usecases.ImageUtils
import com.example.shared.ads.InterstitialAdUseCase
import com.example.shared.domain.prompt.options.ExplanationLevelOption
import com.example.shared.domain.usecases.TextUtils
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
    grokUseCase: GrokUseCase,
    gigaChatUseCase: GigaChatUseCase,
    interstitialAdUseCase: InterstitialAdUseCase,
    speechConverter: SpeechConverter,
    audioPlayer: AudioPlayer,
    savedStateHandle: SavedStateHandle
) : BaseResultViewModel(imageUtils, geminiUseCaseClient, openAiUseCase, grokUseCase, gigaChatUseCase, interstitialAdUseCase, speechConverter, audioPlayer, savedStateHandle) {

    override val audioPrefixName: String
        get() = "schoolkiller"
    private var detailsLevel = ""
    private var grade = 0

    init {

        savedStateHandle.get<Int>("grade")?.let { grade = it }
        savedStateHandle.get<String>("detailsLevel")?.let { detailsLevel = it }

        fun buildSolvingPrompt(): String {

            val details = when (detailsLevel) {
                ExplanationLevelOption.DETAILED_EXPLANATION.detailsLevel -> {
                    "Use a step-by-step approach (chain of thought) to analyze each problem before solving it. Provide a detailed explanation for each solution, ensuring clarity and logical progression."
                }
                ExplanationLevelOption.NO_EXPLANATION.detailsLevel -> {
                    "Show calculations/solutions only, without any verbal explanations."
                }
                ExplanationLevelOption.SHORT_EXPLANATION.detailsLevel -> {
                    "Provide concise answers, focusing on the key points and omitting unnecessary details. Keep the answers short and to the point."
                }
                else -> {
                    throw IllegalStateException("Illegal details level inside ResultViewModel")
                }
            }

            val gradeInfo = if (grade != 0) "${grade}th grader" else "students"

            val tasksDescription = when {
                imageUsed -> """
            Tasks to solve are provided in the attached images. 
            $imageQrMsg
            ${getTasks(userTask, additional = true)}
            $noTasks
        """.trimIndent()

                passedEditedResult.isNotBlank() -> """
            ${getTasks(passedEditedResult, false)}
            ${getTasks(userTask, true)}
        """.trimIndent()

                else -> getTasks(userTask, additional = false)
            }

            return """
        You are a helpful and knowledgeable assistant for $gradeInfo.

        $tasksDescription $onlySolutions
        
        - Generate a JSON response that includes the solutions.
          $details

        - The response **must include a \"titles\" section** containing localized section headers.
        - **Use the exact keys from the example JSON below for \"titles\"**.
        - The response must **translate** section titles (inside \"titles\") into ${selectedLanguage.languageName}.

        Strictly format the response as a JSON object with the following structure:

        - \"titles\" (Map<String, String>) – A map of **localized section headers** (must match the keys in the example JSON).
        - \"solutions\" (List<String>) – A list of the solutions corresponding to the detected tasks. $onlySolutions
        - \"qrContents\" (List<String>) – A list of contents corresponding to the content extracted and analyzed from QR codes (if such exist) in the images. Each content matches one QR code.
        - \"barcodeContents\" (List<String>) – A list of contents corresponding to the content extracted and analyzed from barcodes (if such exist) in the images. Each content matches one barcode.
        ${ocrTextJsonEntry(imageUsed)}

        Example Output:
        {
          \"titles\": {
            \"solutions\": \"<'Solutions' translated>\",
            \"qrContents\": \"<'QR Contents' translated>\",
            \"barcodeContents\": \"<'barcode Contents' translated>\"
          },
          ${if (imageUsed) {"""\"ocrText\": \"x + 4 = 9\","""} else ""}
          \"solutions\": [
            \"x + 5 = 10 -> x = 10 - 5 -> x = 5\",
            \"The area of a rectangle with length 5 and width 10 is 5 * 10 = 50\"
          ],
          \"qrContents\": [
            \"Trip description: Visit the Colosseum and the Vatican.\"
          ],
          \"barcodeContents\": [
            \"Special discount code: SAVE10.\"
          ]
        }

        $doubleQuotes
        ${jsonResponseLanguage(selectedLanguage.languageName)}
    """.trimIndent()
        }
        prompt = buildSolvingPrompt()
    }

    override fun toHtml(content: String): String = TextUtils.markdownToHtml(content)
    override fun decodeSolutionResponse(response: String): Pair<String, String> = decodeTaskSolutionResponse(response)
}
