package com.styletranslator.presentation.screens.output.result

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class StyleSolutionResponse(
    val titles: Map<String, String>,
    val translatedText: String,
    val ocrText: String? = null
)

private val json = Json { ignoreUnknownKeys = true }

fun decodeStyleSolutionResponse(jsonResponse: String): Pair<String, String> {
    val cleanedJson = jsonResponse.trim()
        .removeSurrounding("```json", "```")
        .trim()

    val styleSolutionResponse: StyleSolutionResponse = json.decodeFromString(cleanedJson)

    return buildString {
        appendLine(styleSolutionResponse.translatedText)
    } to (styleSolutionResponse.ocrText ?: "")
}