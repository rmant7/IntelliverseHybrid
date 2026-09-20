package com.matterofchoice.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.matterofchoice.GameState
import com.matterofchoice.api.GeminiRepository
import com.matterofchoice.model.Case
import com.matterofchoice.screens.PrefKeys
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.IOException

class AIViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences =
        application.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    private val geminiRepository = GeminiRepository()

    private var allCasesList: List<Case> = emptyList()
    private var i = 0 // File index counter

    private var _state = mutableStateOf(GameState())
    val state: State<GameState> = _state

    /**
     * Initializes the first turn if not already started.
     */
    fun initiateGame() {
        if (_state.value.currentTurn == 1 && _state.value.casesList.isEmpty()) {
            Log.d("AIViewModel", "Starting fresh game for Turn 1")
            initiateGameForTurn(1)
        } else {
            Log.d("AIViewModel", "initiateGame skipped (turn: ${_state.value.currentTurn}, cases: ${_state.value.casesList.size})")
            if (_state.value.isLoading && _state.value.casesList.isNotEmpty()) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun onUserChoice(caseId: String, choice: String) {
        val updatedChoices = _state.value.userChoices.toMutableMap()
        updatedChoices[caseId] = choice
        _state.value = _state.value.copy(userChoices = updatedChoices)
        Log.d("AIViewModel", "User chose for case $caseId -> $choice")
    }

    /**
     * Performs AI-driven analysis via GeminiRepository.
     */
    fun performAnalysis() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val userLanguage = sharedPreferences.getString("userLanguage", "English")!!
                val questionType = "behavioral"
                val role = "Parent"

                val analysisResult = geminiRepository.submitAnalysis(
                    answers = _state.value.userChoices,
                    allCases = allCasesList,
                    role = role,
                    questionType = questionType,
                    language = userLanguage
                )

                _state.value = _state.value.copy(
                    analysisResult = analysisResult.overall_judgement,
                    analysisData = analysisResult,
                    isLoading = false
                )

                Log.d("AIViewModel", "Analysis completed successfully")

            } catch (e: Exception) {
                Log.e("AIViewModel", "Analysis failed", e)
                _state.value = _state.value.copy(
                    error = "Analysis failed: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    /**
     * Saves a case and user choice to a local JSON file.
     */
    fun saveUserChoice(context: Context, caseData: Case, userChoice: String) {
        val outputPath = context.getExternalFilesDir("tool")?.absolutePath ?: ""
        val caseFile = File(outputPath, "option_$i.json")
        i++

        if (outputPath.isEmpty()) {
            Log.e("AIViewModel", "Failed to get external storage directory.")
            return
        }

        val outputDirectory = caseFile.parentFile
        if (outputDirectory != null && !outputDirectory.exists() && !outputDirectory.mkdirs()) {
            Log.e("AIViewModel", "Failed to create directory: ${outputDirectory.absolutePath}")
            return
        }

        try {
            FileWriter(caseFile).use { writer ->
                writer.write(caseData.toString() + " user choice: $userChoice")
            }
            Log.d("AIViewModel", "Saved user choice to ${caseFile.absolutePath}")
        } catch (e: IOException) {
            Log.e("AIViewModel", "Failed to save user choice", e)
        }
    }

    /**
     * Resets the game state and clears shared preferences.
     */
    fun resetGame() {
        viewModelScope.launch {
            try {
                sharedPreferences.edit {
                    putInt("userScore", 0)
                    putInt("totalScore", 0)
                    putInt("rounds", 1)
                    apply()
                }

                allCasesList = emptyList()

                _state.value = GameState(
                    isLoading = false,
                    casesList = emptyList(),
                    userChoices = emptyMap(),
                    currentTurn = 1,
                    analysisResult = null,
                    analysisData = null,
                    error = null
                )

                Log.d("AIViewModel", "Game reset complete.")
            } catch (e: Exception) {
                Log.e("AIViewModel", "Failed to reset game", e)
                _state.value = _state.value.copy(error = "Reset failed: ${e.message}")
            }
        }
    }

    /**
     * Moves to the next turn and fetches new cases.
     */
    fun nextTurn() {
        viewModelScope.launch {
            val currentTurn = _state.value.currentTurn
            if (currentTurn >= 3) {
                Log.d("AIViewModel", "Max turns reached; no further turns will be generated.")
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }

            val nextTurn = currentTurn + 1
            _state.value = _state.value.copy(
                isLoading = true,
                currentTurn = nextTurn,
                casesList = emptyList(),
                error = null
            )

            initiateGameForTurn(nextTurn)
        }
    }

    /**
     * Generates new cases for a given turn using GeminiRepository.
     */
    private fun initiateGameForTurn(turn: Int) {
        viewModelScope.launch {
            if (!_state.value.isLoading) {
                _state.value = _state.value.copy(isLoading = true, error = null)
            }

            try {
                val userSubject = sharedPreferences.getString(PrefKeys.USER_SUBJECT, "life skills")!!
                val language = sharedPreferences.getString("userLanguage", "English")!!
                val age = sharedPreferences.getString(PrefKeys.USER_AGE, "25")!!.toIntOrNull() ?: 25
                val difficulty = sharedPreferences.getString(PrefKeys.USER_DIFFICULTY, "normal")!!.lowercase()
                val questionType = "behavioral"
                val subType = "scenario_analysis"
                val sex = sharedPreferences.getString(PrefKeys.USER_GENDER, "any")!!

                val previousAnswers = _state.value.userChoices
                val previousCases = allCasesList

                Log.d("AIViewModel", "Generating cases for turn $turn...")

                val responseCases = geminiRepository.generateCases(
                    language = language,
                    subject = userSubject,
                    difficulty = difficulty,
                    questionType = questionType,
                    subType = subType,
                    age = age,
                    sex = sex,
                    previousAnswers = previousAnswers,
                    previousCases = previousCases
                )

                allCasesList = allCasesList + responseCases

                _state.value = _state.value.copy(
                    isLoading = false,
                    casesList = responseCases,
                    error = null
                )

                Log.d("AIViewModel", "Turn $turn cases loaded successfully (${responseCases.size} cases)")

            } catch (e: Exception) {
                Log.e("AIViewModel", "Failed to initiate turn $turn", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to start turn $turn: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun clearAnalysisData() {
        _state.value = _state.value.copy(analysisData = null, analysisResult = null)
        Log.d("AIViewModel", "Cleared analysis data.")
    }
}
