package com.matterofchoice

data class AnalysisState(
    val isLoading: Boolean = false,
    val analysis: String? = null,
    val error: String? = null
)