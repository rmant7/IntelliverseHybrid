package com.intelliverse.models

import android.content.Context
import java.io.File

/**
 * On-disk layout for downloaded model files -- ported from rmant7/AI's own
 * ModelStore.kt. filesDir/models/<seed.id>.gguf, with a .part suffix while a
 * download is in progress (resumed from, not restarted, on retry).
 */
class ModelStore(private val context: Context) {
    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    fun finalFile(seed: LocalModelSeed): File = File(modelsDir, "${seed.id}.gguf")
    fun partFile(seed: LocalModelSeed): File = File(modelsDir, "${seed.id}.gguf.part")

    /** A real GGUF file is at minimum tens of MB; below 50MB it's almost
     * certainly a truncated/partial download that never got renamed, or
     * corrupt -- not a genuinely installed model. */
    fun isInstalled(seed: LocalModelSeed): Boolean =
        finalFile(seed).let { it.exists() && it.length() > 50 * 1024 * 1024 }

    fun delete(seed: LocalModelSeed) {
        finalFile(seed).delete()
        partFile(seed).delete()
    }
}
