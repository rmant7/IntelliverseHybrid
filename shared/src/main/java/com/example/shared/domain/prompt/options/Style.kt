package com.example.shared.domain.prompt.options

import android.content.Context
import com.example.shared.R

enum class Style(private val arrayIndex: Int, val style: String) {

    POETIC(0, "poetic"),
    PROFESSIONAL(1, "professional"),
    LEGAL(2, "legal"),
    TECHNICAL(3, "technical"),
    ACADEMIC(4, "academic"),
    JOURNALISTIC(5, "journalistic"),
    CREATIVE(6, "creative"),
    PERSUASIVE(7, "persuasive"),
    NARRATIVE(8, "narrative"),
    DESCRIPTIVE(9, "descriptive"),
    EXPOSITORY(10, "expository"),
    HUMOROUS(11, "humorous"),
    INFORMAL(12, "informal"),
    BUSINESS(13, "business"),
    SCIENTIFIC(14, "scientific"),
    ROMANTIC(15, "romantic"),
    HISTORICAL(16, "historical"),
    PHILOSOPHICAL(17, "philosophical"),
    SELF_HELP(18, "self_help"),
    TRAVEL(19, "travel"),
    MARKETING(20, "marketing");

    fun getString(context: Context): String {
        val explanationsArray = context.resources.getStringArray(R.array.styles)
        return explanationsArray[arrayIndex]
    }
}