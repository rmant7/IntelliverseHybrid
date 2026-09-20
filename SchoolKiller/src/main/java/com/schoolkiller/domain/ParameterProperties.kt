package com.schoolkiller.domain

import com.example.shared.domain.prompt.options.ExplanationLevelOption
import com.example.shared.domain.prompt.options.GradeOption
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import java.util.Locale

data class ParameterProperties(
    val grade: GradeOption = GradeOption.NONE,
    val language: SolutionLanguageOption = SolutionLanguageOption.fromLocale(Locale.getDefault())
        ?: SolutionLanguageOption.DEFAULT,
    val explanationLevel: ExplanationLevelOption = ExplanationLevelOption.SHORT_EXPLANATION,
) {
    companion object {
        val defaultProperties = ParameterProperties()
    }
}