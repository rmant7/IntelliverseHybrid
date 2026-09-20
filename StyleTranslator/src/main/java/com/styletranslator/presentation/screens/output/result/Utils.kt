package com.styletranslator.presentation.screens.output.result

import com.example.shared.domain.usecases.TextUtils

internal fun addText(text: String, additional: Boolean): String {
    if (text.isBlank()) return ""
    val cleanedJsonStr = TextUtils.htmlToJsonString(text)
    return if (additional) {
        "additional Text: $cleanedJsonStr."
    } else {
        "$cleanedJsonStr."
    }
}