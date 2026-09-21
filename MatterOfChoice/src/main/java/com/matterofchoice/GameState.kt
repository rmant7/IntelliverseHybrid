package com.matterofchoice

import android.graphics.Bitmap
import com.matterofchoice.api.AnalysisResultResponse
import com.matterofchoice.model.Case
import com.matterofchoice.screens.AnalysisResult

data class GameState(
    val isLoading: Boolean = false,
    // True while a buffer-replenish batch is being fetched in the background
    // (see AIViewModel.maybeReplenishCases()) -- unlike isLoading, this never
    // blocks the UI; the player keeps answering already-loaded cases while it's true.
    val isFetchingMore: Boolean = false,
    val casesList: List<Case> = emptyList(),
    val error: String? = null,
    val image: Bitmap? = null,
    val analysisResult: String? = null,
    val userChoices: Map<String, String> = emptyMap(),
    val analysisData: AnalysisResultResponse? = null,
    val allCases: List<Case> = emptyList()
)
