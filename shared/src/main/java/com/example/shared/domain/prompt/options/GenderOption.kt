package com.example.shared.domain.prompt.options

import android.content.Context
import com.example.shared.R

enum class GenderOption(private val arrayIndex: Int, val gender: String) {

    MALE(0, "male"),
    FEMALE(1, "female");

    fun getString(context: Context): String {
        val genderArray = context.resources.getStringArray(R.array.gender)
        return genderArray[arrayIndex]
    }
}