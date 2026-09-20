package com.example.shared.domain.prompt.options

import android.content.Context
import com.example.shared.R

enum class Category(private val arrayIndex: Int, val category: String) {

    RELATIONSHIP(0, "relationship"),
    WORKPLACE(1, "workplace"),
    EDUCATION(2, "education"),
    HEALTH(3, "health"),
    FINANCE(4, "finance"),
    TRAVEL(5, "travel"),
    TECHNOLOGY(6, "technology"),
    ENTERTAINMENT(7, "entertainment"),
    SELF_IMPROVEMENT(8, "self_improvement"),
    GENERAL(9, "general");

    fun getString(context: Context): String {
        val categoryArray = context.resources.getStringArray(R.array.categories)
        return categoryArray[arrayIndex]
    }
}
