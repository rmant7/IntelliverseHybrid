package com.example.shared.domain.prompt.options

import android.content.Context
import com.example.shared.R

enum class TripStyle(private val arrayIndex: Int, val style: String) {

    FOOD(0, "food"),
    HISTORY(1, "history"),
    CULTURE(2, "culture"),
    NATURE(3, "nature"),
    RELAXATION(4, "relaxation"),
    ADVENTURE(5, "adventure"),
    NIGHTLIFE(6, "nightlife"),
    SHOPPING(7, "shopping"),
    FAMILY(8, "family"),
    ROMANTIC(9, "romantic");

    fun getString(context: Context): String {
        val styleArray = context.resources.getStringArray(R.array.trip_styles)
        return styleArray[arrayIndex]
    }
}
