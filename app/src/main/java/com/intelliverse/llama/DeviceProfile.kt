package com.intelliverse.llama

/**
 * RAM-budget math for deciding whether a local model is safe to load --
 * ported verbatim (only the package changed) from rmant7/AI's own
 * core/registry/DeviceProfile.kt. Pure Kotlin, zero Android dependency.
 *
 * [usableRamBytes] is a generous policy ceiling derived mostly from total
 * RAM; [liveRamBytes] is the strict "would this actually load right now"
 * figure derived from live free RAM. rmant7/AI keeps both because a real
 * device report there showed three models all pass the generous check
 * ([fitsBudget]) and then fail to load anyway -- [fitsLiveMemory] is the
 * stricter check that would have caught it.
 */
data class DeviceProfile(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val ramBudgetFraction: Double = BASE_RAM_FRACTION,
    val freeRamSafetyFactor: Double = FREE_RAM_SAFETY_FACTOR,
) {
    init {
        require(freeRamSafetyFactor > 0 && freeRamSafetyFactor <= 1.0)
        require(availableRamBytes in 0..totalRamBytes)
        require(ramBudgetFraction in 0.05..MAX_RAM_FRACTION)
    }

    val usableRamBytes: Long
        get() = maxOf(
            (totalRamBytes * ramBudgetFraction).toLong(),
            (availableRamBytes * freeRamSafetyFactor).toLong(),
        ).coerceAtMost((totalRamBytes * MAX_RAM_FRACTION).toLong())

    val liveRamBytes: Long
        get() = (availableRamBytes * freeRamSafetyFactor).toLong()

    fun fitsBudget(artifactSizeBytes: Long): Boolean =
        artifactSizeBytes <= 0 || artifactSizeBytes * ESTIMATE_NUMERATOR / ESTIMATE_DENOMINATOR <= usableRamBytes

    fun fitsLiveMemory(artifactSizeBytes: Long): Boolean =
        artifactSizeBytes <= 0 || artifactSizeBytes * ESTIMATE_NUMERATOR / ESTIMATE_DENOMINATOR <= liveRamBytes

    companion object {
        const val BASE_RAM_FRACTION = 0.35
        const val FREE_RAM_SAFETY_FACTOR = 0.6
        const val MAX_RAM_FRACTION = 0.95
        const val ESTIMATE_NUMERATOR = 13L
        const val ESTIMATE_DENOMINATOR = 10L
    }
}
