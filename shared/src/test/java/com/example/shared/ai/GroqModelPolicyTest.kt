package com.example.shared.ai

import com.example.shared.domain.ai.GroqModelPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqModelPolicyTest {

    // Shaped like a real account's /models: text-only Qwen listed before the
    // vision one, plus speech and safety models.
    private val account = listOf(
        "openai/gpt-oss-120b",
        "qwen/qwen3-32b",
        "whisper-large-v3",
        "meta-llama/llama-guard-4-12b",
        "qwen/qwen3.6-27b",
        "llama-3.3-70b-versatile",
        "playai-tts",
        "meta-llama/llama-4-scout-17b-16e-instruct",
    )

    private fun error(message: String, code: String? = null) =
        """{"error":{"message":"$message","type":"invalid_request_error"${code?.let { ",\"code\":\"$it\"" } ?: ""}}}"""

    @Test
    fun known_vision_models_come_first_and_non_chat_models_never() {
        val candidates = GroqModelPolicy().candidates(account, hasImages = true)
        assertEquals(
            listOf(
                "qwen/qwen3.6-27b",
                "meta-llama/llama-4-scout-17b-16e-instruct",
                "qwen/qwen3-32b",
                "openai/gpt-oss-120b",
                "llama-3.3-70b-versatile",
            ),
            candidates,
        )
    }

    @Test
    fun a_model_that_rejected_an_image_is_not_offered_images_again_but_still_text() {
        val policy = GroqModelPolicy()
        policy.markTextOnly("qwen/qwen3-32b")
        assertFalse("qwen/qwen3-32b" in policy.candidates(account, hasImages = true))
        assertTrue("qwen/qwen3-32b" in policy.candidates(account, hasImages = false))
    }

    @Test
    fun text_requests_drop_only_non_chat_models() {
        val candidates = GroqModelPolicy().candidates(account, hasImages = false)
        assertEquals(5, candidates.size)
        assertFalse(candidates.any { "whisper" in it || "guard" in it || "tts" in it })
    }

    @Test
    fun image_rejection_is_recognised() {
        assertTrue(GroqModelPolicy.isImageInputRejected(400, error("messages[1].content must be a string")))
        assertTrue(GroqModelPolicy.isImageInputRejected(400, error("'messages.1' : for 'role:user' the following must be satisfied[('messages.1.content' : value must be a string)]")))
        assertTrue(GroqModelPolicy.isImageInputRejected(400, error("This model does not support image input")))
        assertTrue(GroqModelPolicy.isImageInputRejected(400, error("Vision is not supported for this model")))
    }

    @Test
    fun other_failures_are_not_image_rejection() {
        // A broken picture fails everywhere: must not mark a vision model text-only.
        assertFalse(GroqModelPolicy.isImageInputRejected(400, error("Invalid image data")))
        assertFalse(GroqModelPolicy.isImageInputRejected(400, error("Failed to validate JSON against the image schema", "json_validate_failed")))
        assertFalse(GroqModelPolicy.isImageInputRejected(400, error("response_format json_schema is not supported")))
        assertFalse(GroqModelPolicy.isImageInputRejected(413, error("image is not supported: request too large")))
        assertFalse(GroqModelPolicy.isImageInputRejected(400, "not json"))
    }
}
