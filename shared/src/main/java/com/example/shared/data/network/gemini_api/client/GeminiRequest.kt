package com.example.shared.data.network.gemini_api.client

object GeminiRequest {

    fun buildGeminiRequest(
        prompt: String,
        systemInstruction: String,
        fileUris: List<String> = emptyList() // Default to no file URIs
    ): String {
        val fileDataParts = fileUris.joinToString(separator = ",") { uri ->
            val escapedFileUri = uri.replace("\"", "\\\"")
            """
            {"file_data": {"mime_type": "image/jpeg", "file_uri": "$escapedFileUri"}}
            """.trimIndent()
        }

        val parts = if (fileDataParts.isBlank()) {
            """
            {"text":"$prompt"}
            """.trimIndent()
        } else {
            """
            {"text":"$prompt"},
            $fileDataParts
            """.trimIndent()
        }

        return """
        { 
            "system_instruction": {
                "parts": [
                    { "text": "$systemInstruction" }
                ]
            },
            "contents": [{
                "parts": [
                    $parts
                ]
            }]
        }
        """.trimIndent()
    }
}