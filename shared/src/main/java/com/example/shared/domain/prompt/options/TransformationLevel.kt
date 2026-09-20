package com.example.shared.domain.prompt.options

import android.content.Context
import com.example.shared.R

enum class TransformationLevel(private val arrayIndex: Int, val level: String) {

    MINIMAL(0, "minimal"),
    MODERATE(1, "moderate"),
    COMPLETE(2, "complete");

    fun getString(context: Context): String {
        val levelsArray = context.resources.getStringArray(R.array.transformation_levels)
        return levelsArray[arrayIndex]
    }
}
