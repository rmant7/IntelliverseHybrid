package com.intelliverse.presentation

import ai.localstudio.sdk.GenerationOptions
import ai.localstudio.sdk.LocalAi
import ai.localstudio.sdk.LocalAiException
import ai.localstudio.sdk.LocalAiInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intelliverse.localai.IntelliverseLocalAi
import com.intelliverse.models.ModelPurpose
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One turn: who said it, and what -- [thinking] while a reasoning model has not reached its answer yet. */
data class ChatTurn(val fromUser: Boolean, val text: String, val thinking: Boolean = false, val failed: Boolean = false)

/**
 * On-device chat through the local-AI SDK, with the model chosen on the
 * Models screen. The SDK takes one input per call, so earlier turns ride
 * along inside it, newest last and only as many as fit [HISTORY_CHARS].
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val localAi: LocalAi,
    private val models: IntelliverseLocalAi,
) : ViewModel() {

    val turns = mutableStateListOf<ChatTurn>()
    var input by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    private var job: Job? = null

    /** The model answering now, for the title; null when no chat model is installed. */
    fun modelTitle(): String? = models.defaultFor(ModelPurpose.CHAT)?.title

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        val prompt = promptWith(text)
        turns += ChatTurn(fromUser = true, text = text)
        turns += ChatTurn(fromUser = false, text = "", thinking = true)
        val index = turns.lastIndex
        busy = true
        job = viewModelScope.launch {
            val reply = StringBuilder()
            try {
                localAi.generate(LocalAiInput(prompt, systemPrompt = SYSTEM_PROMPT), GenerationOptions(maxTokens = 1024)).collect {
                    reply.append(it)
                    val answer = IntelliverseLocalAi.finalAnswer(reply.toString())
                    turns[index] = ChatTurn(false, answer.orEmpty(), thinking = answer == null || answer.isEmpty())
                }
                val answer = IntelliverseLocalAi.finalAnswer(reply.toString())
                turns[index] = if (answer.isNullOrBlank()) {
                    ChatTurn(false, "No answer (the model was still thinking when it stopped).", failed = true)
                } else {
                    ChatTurn(false, answer)
                }
            } catch (e: CancellationException) {
                turns[index] = ChatTurn(false, IntelliverseLocalAi.finalAnswer(reply.toString()).orEmpty().ifBlank { "Stopped." })
                throw e
            } catch (e: LocalAiException.NoModel) {
                turns[index] = ChatTurn(false, "No chat model installed. Open Models and download one.", failed = true)
            } catch (e: Exception) {
                turns[index] = ChatTurn(false, e.message ?: "Failed", failed = true)
            } finally {
                busy = false
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun clear() {
        stop()
        turns.clear()
    }

    private fun promptWith(text: String): String {
        val history = turns.filter { !it.failed && it.text.isNotBlank() }
        if (history.isEmpty()) return text
        val earlier = StringBuilder()
        for (turn in history.asReversed()) {
            val line = (if (turn.fromUser) "User: " else "Assistant: ") + turn.text + "\n"
            if (earlier.length + line.length > HISTORY_CHARS) break
            earlier.insert(0, line)
        }
        return "Conversation so far:\n$earlier\nUser: $text"
    }

    private companion object {
        const val SYSTEM_PROMPT = "You are a helpful assistant running on the user's phone. Answer in the language the user writes in. Be concise."
        const val HISTORY_CHARS = 4000
    }
}
