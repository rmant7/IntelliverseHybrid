package com.matterofchoice

import android.graphics.Bitmap
import com.matterofchoice.api.AnalysisResultResponse
import com.matterofchoice.model.Case
import com.matterofchoice.screens.AnalysisResult

data class GameState(
    val isLoading: Boolean = false,
    val casesList: List<Case> = emptyList(),
    val error: String? = null,
    val image: Bitmap? = null,
    val analysisResult: String? = null,
    val userChoices: Map<String, String> = emptyMap(),
    val analysisData: AnalysisResultResponse? = null,
    val currentTurn: Int = 1,
    val allCases: List<Case> = emptyList()
)
