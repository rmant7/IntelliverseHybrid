package com.intelliverse.models

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Installed : DownloadState
    data class Resolving(val repoId: String) : DownloadState
    /** [projector]: the weights are in, this is the vision projector coming after them. */
    data class Running(val downloadedBytes: Long, val totalBytes: Long, val projector: Boolean = false) : DownloadState
    /** Part of a file is on disk -- cancelled, or the app was killed mid-download; starting again resumes from there. */
    data class Paused(val downloadedBytes: Long, val totalBytes: Long, val projector: Boolean = false) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Orchestrates resolve -> download -> verify for the local model catalog --
 * ported from rmant7/AI's own ModelDownloads.kt. Owns its own
 * app-lifetime coroutine scope rather than a ViewModel's, so a download
 * survives screen rotation/navigation away from the Models screen.
 *
 * [onDownloadStarted] is [ModelDownloads]' own way of saying "keep the
 * process alive" -- confirmed necessary on a real device: with nothing
 * calling this, all three catalog downloads failed the moment the app was
 * backgrounded (switched away to check a browser), not from a slow network
 * but because Android has no reason to protect a plain background
 * coroutine's socket from being torn down. [ModelsModule] wires this to
 * start [ModelDownloadService] (a foreground service with a visible
 * notification), the same hook shape rmant7/AI's own ModelDownloads
 * exposes for exactly this reason.
 */
class ModelDownloads(
    context: Context,
    private val store: ModelStore = ModelStore(context),
    private val resolver: HuggingFaceResolver = HuggingFaceResolver(),
    private val downloader: ModelDownloader = ModelDownloader(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val onDownloadStarted: () -> Unit = {},
) {
    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states

    private val _projectorErrors = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Why a vision model's projector could not be fetched, by model id -- its weights are in and it chats. */
    val projectorErrors: StateFlow<Map<String, String>> = _projectorErrors

    private val jobs = mutableMapOf<String, Job>()
    private val cancelFlags = mutableMapOf<String, Boolean>()

    init {
        // After a crash or a kill mid-download the .part file is all that is left: shown as paused (Resume), not as never started.
        // Weights in with a projector cut short is paused too: the model chats, Resume adds its vision.
        LocalModelCatalog.ALL.forEach { seed ->
            when {
                store.isInstalled(seed) && seed.vision && !store.hasProjector(seed) && store.projectorPartFile(seed).length() > 0 ->
                    setState(seed.id, pausedProjector(seed))
                store.isInstalled(seed) -> setState(seed.id, DownloadState.Installed)
                store.partFile(seed).length() > 0 -> setState(seed.id, paused(seed))
            }
        }
    }

    private fun setState(id: String, state: DownloadState) {
        _states.update { it + (id to state) }
    }

    /**
     * Downloads what [seed] is missing: its weights, then -- for a vision
     * model -- its projector. Each resumes from its own .part file. Weights
     * already in (a model downloaded before it could see, or a projector cut
     * short) means only the projector is fetched.
     */
    fun start(seed: LocalModelSeed) {
        if (jobs[seed.id]?.isActive == true) return
        cancelFlags[seed.id] = false
        onDownloadStarted()
        setState(seed.id, DownloadState.Resolving(seed.repoIds.first()))
        jobs[seed.id] = scope.launch {
            try {
                if (!store.isInstalled(seed)) {
                    val artifact = resolver.resolveAny(seed.repoIds, seed.quantPriority).getOrElse { e ->
                        setState(seed.id, DownloadState.Failed(e.message ?: "Resolve failed"))
                        return@launch
                    }
                    if (!fetch(seed, artifact, store.partFile(seed), store.finalFile(seed), projector = false)) return@launch
                }
                if (seed.vision && !store.hasProjector(seed)) {
                    _projectorErrors.update { it - seed.id }
                    setState(seed.id, DownloadState.Resolving(seed.repoIds.first()))
                    val projector = resolver.resolveProjector(seed.repoIds).getOrElse { e ->
                        // The weights work without it: the model chats, it just cannot see. Said on its card, not hidden.
                        Timber.w(e, "No vision projector for ${seed.id}")
                        _projectorErrors.update { it + (seed.id to (e.message ?: "not found")) }
                        setState(seed.id, DownloadState.Installed)
                        return@launch
                    }
                    Timber.i("${seed.id}: vision projector ${projector.downloadUrl} (${projector.sizeBytes} bytes)")
                    if (!fetch(seed, projector, store.projectorPartFile(seed), store.projectorFile(seed), projector = true)) return@launch
                }
                setState(seed.id, DownloadState.Installed)
            } catch (e: ModelDownloader.CancelledException) {
                setState(
                    seed.id,
                    when {
                        store.isInstalled(seed) && store.projectorPartFile(seed).length() > 0 -> pausedProjector(seed)
                        store.isInstalled(seed) -> DownloadState.Installed
                        store.partFile(seed).length() > 0 -> paused(seed)
                        else -> DownloadState.Idle
                    },
                )
            } catch (e: Exception) {
                Timber.w(e, "Model download failed for ${seed.id}")
                setState(seed.id, DownloadState.Failed(e.message ?: "Download failed"))
            }
        }
    }

    /** One file into [part], checked for size, then renamed to [final]; false (with the state set) when it came out wrong. */
    private fun fetch(seed: LocalModelSeed, artifact: RemoteArtifact, part: java.io.File, final: java.io.File, projector: Boolean): Boolean {
        val fallbackTotal = if (projector) seed.projectorApproxBytes else seed.approxSizeBytes
        downloader.download(
            artifact = artifact,
            destination = part,
            onProgress = { downloaded, total ->
                setState(seed.id, DownloadState.Running(downloaded, if (total > 0) total else fallbackTotal, projector))
            },
            isCancelled = { cancelFlags[seed.id] == true },
        )
        val finalSize = part.length()
        if (artifact.sizeBytes > 0 && finalSize != artifact.sizeBytes) {
            part.delete()
            setState(seed.id, DownloadState.Failed("Size mismatch after download"))
            return false
        }
        part.renameTo(final)
        return true
    }

    private fun paused(seed: LocalModelSeed) =
        DownloadState.Paused(store.partFile(seed).length(), maxOf(seed.approxSizeBytes, store.partFile(seed).length()))

    private fun pausedProjector(seed: LocalModelSeed) =
        DownloadState.Paused(
            store.projectorPartFile(seed).length(),
            maxOf(seed.projectorApproxBytes, store.projectorPartFile(seed).length()),
            projector = true,
        )

    fun cancel(seed: LocalModelSeed) {
        cancelFlags[seed.id] = true
    }

    fun delete(seed: LocalModelSeed) {
        cancel(seed)
        store.delete(seed)
        setState(seed.id, DownloadState.Idle)
    }
}
