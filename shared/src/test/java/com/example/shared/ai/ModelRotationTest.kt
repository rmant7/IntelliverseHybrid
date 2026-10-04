package com.example.shared.ai

import com.example.shared.domain.ai.ModelCooldownStore
import com.example.shared.domain.ai.ModelRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelRotationTest {

    private val dir: File = Files.createTempDirectory("cooldowns").toFile()
    private var now = 1_000_000L
    private val store = ModelCooldownStore(File(dir, "c.json")) { now }

    private class Overloaded : Exception("503")
    private class BadRequest : Exception("400")

    private fun rotation() = ModelRotation("gemini", listOf("primary", "second", "third"), store)

    @Test
    fun an_overloaded_model_is_skipped_and_cooled_down_and_the_next_one_answers() {
        val tried = mutableListOf<String>()
        val (model, result) = rotation().run({ it is Overloaded }) { m ->
            tried += m
            if (m == "primary") Result.failure(Overloaded()) else Result.success("answer from $m")
        }
        assertEquals("second", model)
        assertEquals("answer from second", result.getOrNull())
        assertEquals(listOf("primary", "second"), tried)
        assertTrue(store.isOnCooldown("gemini", "primary"))
        assertEquals("the next request skips it", listOf("second", "third"), rotation().candidates())
    }

    @Test
    fun any_other_failure_is_the_answer_without_trying_other_models() {
        val tried = mutableListOf<String>()
        val (_, result) = rotation().run({ it is Overloaded }) { m -> tried += m; Result.failure<String>(BadRequest()) }
        assertTrue(result.exceptionOrNull() is BadRequest)
        assertEquals(listOf("primary"), tried)
        assertFalse(store.isOnCooldown("gemini", "primary"))
    }

    @Test
    fun cooldown_escalates_on_consecutive_failures_and_expires() {
        assertEquals(ModelCooldownStore.INITIAL_COOLDOWN_MS, store.markFailed("gemini", "m"))
        now += 1_000
        assertEquals(ModelCooldownStore.INITIAL_COOLDOWN_MS * 2, store.markFailed("gemini", "m"))
        now += ModelCooldownStore.INITIAL_COOLDOWN_MS * 2 + 1
        assertFalse("expired", store.isOnCooldown("gemini", "m"))
        now += ModelCooldownStore.MAX_COOLDOWN_MS + 1
        assertEquals("long after the last failure: a fresh problem, short tier again", ModelCooldownStore.INITIAL_COOLDOWN_MS, store.markFailed("gemini", "m"))
    }

    @Test
    fun cooldowns_survive_a_new_store_on_the_same_file() {
        store.markFailed("gemini", "primary")
        assertTrue(ModelCooldownStore(File(dir, "c.json")) { now }.isOnCooldown("gemini", "primary"))
    }

    @Test
    fun with_every_model_cooling_down_the_primary_is_still_tried() {
        listOf("primary", "second", "third").forEach { store.markFailed("gemini", it) }
        assertEquals(listOf("primary"), rotation().candidates())
        val (_, result) = rotation().run({ it is Overloaded }) { Result.failure<String>(Overloaded()) }
        assertTrue(result.exceptionOrNull() is Overloaded)
    }
}
