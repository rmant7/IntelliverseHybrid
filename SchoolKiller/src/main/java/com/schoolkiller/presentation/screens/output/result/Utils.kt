package com.schoolkiller.presentation.screens.output.result

import com.example.shared.domain.usecases.TextUtils
import com.example.shared.presentation.screens.output.result.doubleQuotes

internal fun getTasks(tasks: String, additional: Boolean): String {
    if (tasks.isBlank()) return ""
    val cleanedJsonStr = TextUtils.htmlToJsonString(tasks)
    return if (additional) {
        "Additional tasks to solve are:\n$cleanedJsonStr"
    } else {
        "Tasks to solve are:\n$cleanedJsonStr"
    }
}

val onlySolutions: String
    get() = "**Do not show the tasks themselves, focus on solving them**."

val noTasks: String
    get() = "If neither tasks nor QR/barcodes are present, do not mention them. Instead, provide a detailed description of the images."

val imageQrMsg: String
    get() = "If the images contain a QR code or barcode, scan it to extract and analyze its content, ensuring that the extracted information is included in the response."