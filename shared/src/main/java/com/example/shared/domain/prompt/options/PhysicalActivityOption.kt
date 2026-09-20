package com.example.shared.domain.prompt.options

import android.content.Context
import com.example.shared.R

enum class PhysicalActivityOption(private val arrayIndex: Int, val activityLevel: String) {

    SEDENTARY(0, "sedentary"),
    LIGHTLY_ACTIVE(1, "lightly active"),
    MODERATE_ACTIVE(2, "moderate active"),
    VERY_ACTIVE(3, "very active"),
    EXTREMELY_ACTIVE(4, "extremely active");


    fun getString(context: Context): String {
        val explanationsArray = context.resources.getStringArray(R.array.physical_activity)
        return explanationsArray[arrayIndex]
    }
}