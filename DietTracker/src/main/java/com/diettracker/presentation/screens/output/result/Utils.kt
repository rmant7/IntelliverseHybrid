package com.diettracker.presentation.screens.output.result

import com.example.shared.domain.usecases.TextUtils

internal fun addProperties(tasks: String, additional: Boolean): String {
    if (tasks.isBlank()) return ""
    val cleanedJsonStr = TextUtils.htmlToJsonString(tasks)
    return if (additional) {
        "additional Foods: $cleanedJsonStr."
    } else {
        "$cleanedJsonStr."
    }
}