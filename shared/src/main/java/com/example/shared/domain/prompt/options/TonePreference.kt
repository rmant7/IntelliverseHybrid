package com.example.shared.domain.prompt.options

import android.content.Context
import com.example.shared.R

enum class TonePreference(private val arrayIndex: Int, val tone: String) {

    FORMAL(0, "formal"),
    CASUAL(1, "casual"),
    FRIENDLY(2, "friendly"),
    SERIOUS(3, "serious"),
    EMPATHETIC(4, "empathetic"),
    ASSERTIVE(5, "assertive"),
    OPTIMISTIC(6, "optimistic"),
    HUMOROUS(7, "humorous"),
    NEUTRAL(8, "neutral");

    fun getString(context: Context): String {
        val toneArray = context.resources.getStringArray(R.array.tones)
        return toneArray[arrayIndex]
    }
}
