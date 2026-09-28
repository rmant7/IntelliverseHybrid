package com.schoolkiller.domain

import com.example.shared.domain.language.Language
import com.example.shared.domain.language.LanguageRegistry
import com.example.shared.domain.prompt.options.ExplanationLevelOption
import com.example.shared.domain.prompt.options.GradeOption
import java.util.Locale

data class ParameterProperties(
    val grade: GradeOption = GradeOption.NONE,
    val language: Language = LanguageRegistry.fromLocale(Locale.getDefault())
        ?: LanguageRegistry.DEFAULT,
    val explanationLevel: ExplanationLevelOption = ExplanationLevelOption.SHORT_EXPLANATION,
) {
    companion object {
        val defaultProperties = ParameterProperties()
    }
}