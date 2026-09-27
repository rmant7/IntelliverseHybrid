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
    data class Running(val downloadedBytes: Long, val totalBytes: Long) : DownloadState
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

    private val jobs = mutableMapOf<String, Job>()
    private val cancelFlags = mutableMapOf<String, Boolean>()

    init {
        TranslationModels.ALL.forEach { seed ->
            if (store.isInstalled(seed)) setState(seed.id, DownloadState.Installed)
        }
    }

    private fun setState(id: String, state: DownloadState) {
        _states.update { it + (id to state) }
    }

    fun start(seed: LocalModelSeed) {
        if (jobs[seed.id]?.isActive == true) return
        cancelFlags[seed.id] = false
        onDownloadStarted()
        setState(seed.id, DownloadState.Resolving(seed.repoIds.first()))
        jobs[seed.id] = scope.launch {
            val artifact = resolver.resolveAny(seed.repoIds, seed.quantPriority)
                .getOrElse { e ->
                    setState(seed.id, DownloadState.Failed(e.message ?: "Resolve failed"))
                    return@launch
                }
            try {
                downloader.download(
                    artifact = artifact,
                    destination = store.partFile(seed),
                    onProgress = { downloaded, total ->
                        setState(seed.id, DownloadState.Running(downloaded, if (total > 0) total else seed.approxSizeBytes))
                    },
                    isCancelled = { cancelFlags[seed.id] == true },
                )
                val finalSize = store.partFile(seed).length()
                if (artifact.sizeBytes > 0 && finalSize != artifact.sizeBytes) {
                    store.partFile(seed).delete()
                    setState(seed.id, DownloadState.Failed("Size mismatch after download"))
                    return@launch
                }
                store.partFile(seed).renameTo(store.finalFile(seed))
                setState(seed.id, DownloadState.Installed)
            } catch (e: ModelDownloader.CancelledException) {
                setState(seed.id, DownloadState.Idle)
            } catch (e: Exception) {
                Timber.w(e, "Model download failed for ${seed.id}")
                setState(seed.id, DownloadState.Failed(e.message ?: "Download failed"))
            }
        }
    }

    fun cancel(seed: LocalModelSeed) {
        cancelFlags[seed.id] = true
    }

    fun delete(seed: LocalModelSeed) {
        cancel(seed)
        store.delete(seed)
        setState(seed.id, DownloadState.Idle)
    }
}
