package com.schoolkiller.presentation.screens.output.result

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TaskSolutionResponse(
    val titles: Map<String, String>,
    val solutions: List<String>,
    // Nothing to read is the normal case (no QR/barcode in the photo, or no
    // photo at all): empty, not a reason to discard the solutions.
    val qrContents: List<String> = emptyList(),
    val barcodeContents: List<String> = emptyList(),
    val ocrText: String? = null,
)

private val json = Json { ignoreUnknownKeys = true }

fun decodeTaskSolutionResponse(jsonResponse: String): Pair<String, String> {
    val cleanedJson = jsonResponse.trim()
        .removeSurrounding("```json", "```")
        .trim()

    val taskSolutionResponse: TaskSolutionResponse = json.decodeFromString(cleanedJson)

    return buildString {
        if (taskSolutionResponse.solutions.isNotEmpty()) {
            appendLine("${taskSolutionResponse.titles["solutions"]}:")
            appendLine()
            taskSolutionResponse.solutions.forEachIndexed { index, solution ->
                appendLine("${index + 1}. $solution")
                appendLine("")
            }
            appendLine()
        }
        if (taskSolutionResponse.qrContents.isNotEmpty()) {
            appendLine("${taskSolutionResponse.titles["qrContents"]}:")
            appendLine()
            taskSolutionResponse.qrContents.forEachIndexed { index, content ->
                appendLine("${index + 1}. $content")
                appendLine("")
            }
            appendLine()
        }
        if (taskSolutionResponse.barcodeContents.isNotEmpty()) {
            appendLine("${taskSolutionResponse.titles["barcodeContents"]}:")
            taskSolutionResponse.barcodeContents.forEachIndexed { index, content ->
                appendLine("${index + 1}. $content")
                appendLine("")
            }
        }
    } to (taskSolutionResponse.ocrText ?: "")
}