package com.styletranslator.domain

import com.example.shared.domain.language.Language
import com.example.shared.domain.language.LanguageRegistry
import com.example.shared.domain.prompt.options.Category
import com.example.shared.domain.prompt.options.GenderOption
import com.example.shared.domain.prompt.options.Mentality
import com.example.shared.domain.prompt.options.Style
import com.example.shared.domain.prompt.options.TonePreference
import com.example.shared.domain.prompt.options.TransformationLevel
import java.util.Locale

data class ParameterProperties(
    val language: Language = LanguageRegistry.fromLocale(Locale.getDefault())
        ?: LanguageRegistry.DEFAULT,
    val sourceGender: GenderOption? = null,
    val targetGender: GenderOption? = null,
    val sourceAge: Int? = null,
    val targetAge: Int? = null,
    val category: Category? = null,
    val style: Style? = null,
    val mentality: Mentality? = null,
    val transformationLevel: TransformationLevel = TransformationLevel.COMPLETE,
    val tonePreference: TonePreference? = null,
    val translationScale: Float? = null
) {
    companion object {
        val defaultProperties = ParameterProperties()
    }
}