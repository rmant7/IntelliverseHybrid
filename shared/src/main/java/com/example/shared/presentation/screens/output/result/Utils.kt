package com.example.shared.presentation.screens.output.result

import com.example.shared.appName

val doubleQuotes: String
    get() = "Ensure the JSON output uses double quotes for all string literals. Do not include any additional text, comments, or explanations—only return the valid JSON."

fun jsonResponseLanguage(language: String): String = "**Provide the Json response in $language.**"

fun ocrTextJsonEntry(imageUsed: Boolean): String = if (imageUsed) """- \"ocrText\" (String) - The text detected from the images (in its original language).""" else ""

val audioPath: String
    get() = "/storage/emulated/0/Android/data/com.$appName/files/"