package com.intelliverse.models

import ai.localstudio.sdk.CheckResult
import ai.localstudio.sdk.GenerationOptions
import ai.localstudio.sdk.Language
import ai.localstudio.sdk.LocalAiInput
import ai.localstudio.sdk.LocalCapability
import ai.localstudio.sdk.TranslationRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.localstudio.app.llama.LlamaBridge
import com.intelliverse.localai.IntelliverseLocalAi
import com.intelliverse.localai.LocalAiSettings
import com.intelliverse.localai.LocalChecks
import com.intelliverse.localai.StoredCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** What the Models list shows for: everything, or one purpose. */
enum class PurposeFilter(val label: String) { ALL("All"), CHAT("Chat"), TRANSLATION("Translation") }

/** Which of them: all, only those on the phone, only those not downloaded, only those that passed a check here. */
enum class StatusFilter(val label: String) { ALL("Any"), INSTALLED("Installed"), AVAILABLE("Not installed"), CHECKED("Checked ✓") }

/** A model's device check, as the list and the details read it. */
data class CheckView(
    /** Per capability: PASS / FAIL / STALE. Empty when never checked. */
    val results: Map<LocalCapability, CheckResult>,
    val stored: StoredCheck?,
    /** Why it no longer applies, when it does not. */
    val staleReason: String?,
)

/**
 * The one Models screen: every model this app can run, filtered by what it
 * is for and where it stands; which one answers chat and which translates;
 * downloading, checking on this phone, trying, deleting. Everything that
 * runs a model goes through the local-AI SDK ([IntelliverseLocalAi]).
 */
@HiltViewModel
class ModelsViewModel @Inject constructor(
    val downloads: ModelDownloads,
    private val localAi: IntelliverseLocalAi,
    private val settings: LocalAiSettings,
    private val checks: LocalChecks,
) : ViewModel() {

    val nativeAvailable: Boolean get() = LlamaBridge.isAvailable

    val selection = settings.selection
    val runningCheck = localAi.runningCheck

    var query by mutableStateOf("")
    var purpose by mutableStateOf(PurposeFilter.ALL)
    var status by mutableStateOf(StatusFilter.ALL)

    /** Bumped after a check finishes, so what is read from disk is read again. */
    var checksVersion by mutableIntStateOf(0)
        private set

    var detailsFor by mutableStateOf<LocalModelSeed?>(null)
    var tryFor by mutableStateOf<LocalModelSeed?>(null)
    var tryBusy by mutableStateOf(false)
        private set
    var tryOutput by mutableStateOf("")
        private set
    var message by mutableStateOf<String?>(null)

    fun visible(states: Map<String, DownloadState>): List<LocalModelSeed> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        return LocalModelCatalog.ALL.filter { seed ->
            val installed = states[seed.id] is DownloadState.Installed
            val purposeOk = when (purpose) {
                PurposeFilter.ALL -> true
                PurposeFilter.CHAT -> ModelPurpose.CHAT in seed.purposes
                PurposeFilter.TRANSLATION -> ModelPurpose.TRANSLATION in seed.purposes
            }
            val statusOk = when (status) {
                StatusFilter.ALL -> true
                StatusFilter.INSTALLED -> installed
                StatusFilter.AVAILABLE -> !installed
                StatusFilter.CHECKED -> installed && check(seed)?.results?.values?.any { it == CheckResult.PASS } == true
            }
            val haystack = "${seed.title} ${seed.id} ${seed.paramsLabel} ${seed.note} ${seed.repoIds.joinToString(" ")}".lowercase()
            purposeOk && statusOk && words.all { it in haystack }
        }.sortedByDescending { states[it.id] is DownloadState.Installed }
    }

    fun isInstalled(seed: LocalModelSeed) = localAi.installed().any { it.id == seed.id }

    /** The model chat goes to now -- the chosen one, or the best installed one when none is chosen. */
    fun chatModel(): LocalModelSeed? = localAi.defaultFor(ModelPurpose.CHAT)

    fun translationModel(): LocalModelSeed? = localAi.defaultFor(ModelPurpose.TRANSLATION)

    fun useForChat(seed: LocalModelSeed) {
        settings.chatModelId = seed.id
        message = "${seed.title} now answers chat"
    }

    fun useForTranslation(seed: LocalModelSeed) {
        settings.translationModelId = seed.id
        message = "${seed.title} now translates"
    }

    fun delete(seed: LocalModelSeed) {
        if (runningCheck.value?.modelId == seed.id) {
            message = "${seed.title} is being checked -- delete it after the check"
            return
        }
        downloads.delete(seed)
        checks.forget(seed.id)
        if (settings.chatModelId == seed.id) settings.chatModelId = null
        if (settings.translationModelId == seed.id) settings.translationModelId = null
        checksVersion++
    }

    fun check(seed: LocalModelSeed): CheckView? {
        if (!isInstalled(seed)) return null
        val file = localAi.fileOf(seed)
        val stored = checks.stored(seed.id)
        return CheckView(checks.results(seed.id, file), stored, stored?.let { checks.staleReason(it, file) })
    }

    fun verify(seed: LocalModelSeed) {
        if (runningCheck.value != null) {
            message = "Another check is running -- one at a time"
            return
        }
        viewModelScope.launch {
            try {
                localAi.verify(seed.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Check failed: ${e.message}"
            }
            checksVersion++
        }
    }

    /** The whole check of [seed] as one plain text, for copying into a message. */
    fun report(seed: LocalModelSeed): String = buildString {
        append(seed.title).append(" (").append(seed.id).append(")\n")
        append(seed.paramsLabel).append(" · ").append(seed.repoIds.joinToString(", ")).append("\n")
        if (isInstalled(seed)) append("File: ").append(formatBytes(localAi.fileOf(seed).length())).append("\n")
        val view = check(seed)
        val stored = view?.stored
        if (stored == null) {
            append("\nNot checked on this phone yet.")
            return@buildString
        }
        append("\nChecked ").append(DateFormat.getDateTimeInstance().format(Date(stored.checkedAtEpochMs)))
        append("\n").append(stored.device).append(" · ").append(stored.runtime)
        append("\nsha256 ").append(stored.sha256)
        view.staleReason?.let { append("\nOUT OF DATE: ").append(it) }
        stored.error?.let { append("\nFailed: ").append(it) }
        stored.results.forEach { (cap, result) ->
            append("\n\n").append(capabilityLabel(cap)).append(": ").append(if (result.passed) "PASS" else "FAIL")
            result.detail?.let { append(" -- ").append(it) }
            result.steps.forEach { step ->
                append("\n  ").append(if (step.passed) "✓ " else "✗ ").append(step.title)
                step.answer?.let { append(" — «").append(it).append("»") }
                step.error?.let { append(" — ").append(it) }
                val timing = listOfNotNull(
                    step.firstTokenMs?.let { String.format(Locale.ROOT, "first word %.1f s", it / 1000.0) },
                    step.totalMs?.let { String.format(Locale.ROOT, "total %.1f s", it / 1000.0) },
                )
                if (timing.isNotEmpty()) append(" [").append(timing.joinToString(", ")).append("]")
            }
        }
    }

    fun tryTranslate(seed: LocalModelSeed, targetCode: String, text: String) = runTry {
        val name = Locale(targetCode).getDisplayLanguage(Locale.ENGLISH).ifBlank { targetCode }
        localAi.translate(TranslationRequest(text, Language("auto", "the source language"), Language(targetCode, name)), seed.id)
    }

    fun tryChat(seed: LocalModelSeed, text: String) = runTry {
        val reply = StringBuilder()
        localAi.generate(LocalAiInput(text), GenerationOptions(maxTokens = 512), seed.id).collect { reply.append(it) }
        IntelliverseLocalAi.finalAnswer(reply.toString()) ?: reply.toString()
    }

    private fun runTry(block: suspend () -> String) {
        if (tryBusy) return
        viewModelScope.launch {
            tryBusy = true
            tryOutput = ""
            try {
                tryOutput = block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tryOutput = "Failed: ${e.message}"
            } finally {
                tryBusy = false
            }
        }
    }

    companion object {
        fun capabilityLabel(name: String) = when (name) {
            LocalCapability.TEXT.name -> "Chat"
            LocalCapability.TRANSLATION.name -> "Translation"
            LocalCapability.VISION.name -> "Images"
            else -> name
        }

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1_000_000_000 -> String.format(Locale.ROOT, "%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format(Locale.ROOT, "%.0f MB", bytes / 1_000_000.0)
            else -> "$bytes B"
        }
    }
}
