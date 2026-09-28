package com.diettracker.domain

import com.example.shared.domain.language.Language
import com.example.shared.domain.language.LanguageRegistry
import com.example.shared.domain.prompt.options.ExplanationLevelOption
import com.example.shared.domain.prompt.options.GenderOption
import com.example.shared.domain.prompt.options.PhysicalActivityOption
import java.util.Locale

data class ParameterProperties(
    val language: Language = LanguageRegistry.fromLocale(Locale.getDefault())
        ?: LanguageRegistry.DEFAULT,
    val explanationLevel: ExplanationLevelOption = ExplanationLevelOption.SHORT_EXPLANATION,
    val physicalActivity: PhysicalActivityOption? = null,
    val gender: GenderOption? = null,
    val height: Int? = null,
    val weight: Int? = null,
    val age: Int? = null,
) {
    companion object {
        val defaultProperties = ParameterProperties()
    }
}