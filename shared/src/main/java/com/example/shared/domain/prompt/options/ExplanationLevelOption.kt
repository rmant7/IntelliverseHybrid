package com.example.shared.domain.prompt.options

import android.content.Context
import com.example.shared.R

enum class ExplanationLevelOption(private val arrayIndex: Int, val detailsLevel: String) {

    NO_EXPLANATION(0, "no details"),
    SHORT_EXPLANATION(1, "briefly"),
    DETAILED_EXPLANATION(2, "in detail");

    fun getString(context: Context): String {
        val explanationsArray = context.resources.getStringArray(R.array.explanations)
        return explanationsArray[arrayIndex]
    }
}