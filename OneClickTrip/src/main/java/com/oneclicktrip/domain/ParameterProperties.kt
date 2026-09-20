package com.oneclicktrip.domain

import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.domain.prompt.options.TransportationType
import com.example.shared.domain.prompt.options.TripStyle
import java.util.Locale

data class ParameterProperties(
    val language: SolutionLanguageOption = SolutionLanguageOption.fromLocale(Locale.getDefault())
        ?: SolutionLanguageOption.DEFAULT,
    val originLocation: String = "",
    val maxBudget: Int? = null,
    val tripDuration: Int? = null,
    val travelersNumber: Int? = null,
    val transportationTypes: List<TransportationType> = emptyList(),
    val tripStyles: List<TripStyle> = emptyList(),
    val tripPath: List<String> = emptyList(),
    val isOneWay: Boolean = false
) {
    companion object {
        val defaultProperties = ParameterProperties()
    }
}