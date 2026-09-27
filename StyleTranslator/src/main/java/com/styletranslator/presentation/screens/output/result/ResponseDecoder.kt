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

// A real device log caught Groq producing a backslash immediately followed
// by a Hebrew letter inside a JSON string value -- not one of JSON's own
// valid escapes ("\"", "\\", "/", "b", "f", "n", "r", "t", "u"), so
// kotlinx.serialization's strict parser rejected the whole response with
// "Invalid escaped char". The model produced almost-valid JSON, not
// genuinely broken text; escaping every backslash that isn't already part
// of a valid escape sequence turns it into a literal backslash character
// instead of failing the whole decode over one malformed escape.
private val invalidEscape = Regex("\\\\(?![\"\\\\/bfnrtu])")

fun decodeStyleSolutionResponse(jsonResponse: String): Pair<String, String> {
    val cleanedJson = jsonResponse.trim()
        .removeSurrounding("```json", "```")
        .trim()
        // The lambda overload, not .replace(regex, "...") -- the string
        // overload runs Java Matcher.replaceAll semantics under the hood,
        // where a literal backslash in the replacement text has its own
        // separate escaping rules layered on top of Kotlin's own string
        // escaping; the lambda form returns its result verbatim, with no
        // second layer of escaping to get wrong.
        .replace(invalidEscape) { "\\\\" }

    val styleSolutionResponse: StyleSolutionResponse = json.decodeFromString(cleanedJson)

    return buildString {
        appendLine(styleSolutionResponse.translatedText)
    } to (styleSolutionResponse.ocrText ?: "")
}