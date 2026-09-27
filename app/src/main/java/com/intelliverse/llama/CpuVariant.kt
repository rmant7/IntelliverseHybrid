package com.intelliverse.llama

import java.io.File

/**
 * Runtime detection of which CPU-feature build of llama_jni this device can
 * safely run -- ported verbatim (only the package changed) from rmant7/AI's
 * own ai.localstudio.whisper.CpuVariant. See app/src/main/cpp/CMakeLists.txt
 * for why three separate builds exist instead of one: a single +i8mm build
 * SIGILLs on a CPU that doesn't support that instruction (confirmed on a
 * real device there), and the reverse gives up real performance on newer
 * CPUs that do.
 */
enum class CpuVariant(val librarySuffix: String) {
    I8MM("_i8mm"),
    DOTPROD("_dotprod"),
    BASELINE(""),
    ;

    companion object {
        fun detect(cpuinfo: String): CpuVariant {
            val perCore = cpuinfo.lineSequence()
                .filter { it.trimStart().startsWith("Features") && ':' in it }
                .map { line -> line.substringAfter(':').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet() }
                .toList()
            if (perCore.isEmpty()) return BASELINE
            val common = perCore.reduce { acc, features -> acc intersect features }
            val dotprodFp16 = "asimddp" in common && "fphp" in common && "asimdhp" in common
            return when {
                dotprodFp16 && "i8mm" in common -> I8MM
                dotprodFp16 -> DOTPROD
                else -> BASELINE
            }
        }

        val current: CpuVariant by lazy {
            runCatching { detect(File("/proc/cpuinfo").readText()) }.getOrDefault(BASELINE)
        }

        /** Tries [current] first, then falls through to progressively safer
         * variants if a load fails (e.g. an ABI/library mismatch) -- never
         * upward, since a weaker variant is always safe to run on a CPU that
         * supports a stronger one. */
        fun loadBest(baseName: String, loader: (String) -> Unit = System::loadLibrary): String? {
            for (variant in entries.dropWhile { it != current }) {
                val name = baseName + variant.librarySuffix
                if (runCatching { loader(name) }.isSuccess) return name
            }
            return null
        }
    }
}
