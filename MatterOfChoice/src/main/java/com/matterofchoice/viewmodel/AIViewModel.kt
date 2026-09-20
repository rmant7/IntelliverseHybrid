package com.matterofchoice.viewmodel

import android.app.Application
import android.content.Context
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
import timber.log.Timber
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
            Timber.d("AIViewModel: starting fresh game for Turn 1")
            // A fresh game must read 0/0 at its first case -- see commit message
            // for why MainActivity.onCreate()/resetGame() alone weren't enough.
            sharedPreferences.edit {
                putInt("userScore", 0)
                putInt("totalScore", 0)
            }
            initiateGameForTurn(1)
        } else {
            Timber.d("AIViewModel: initiateGame skipped (turn: ${_state.value.currentTurn}, cases: ${_state.value.casesList.size})")
            if (_state.value.isLoading && _state.value.casesList.isNotEmpty()) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun onUserChoice(caseId: String, choice: String) {
        val updatedChoices = _state.value.userChoices.toMutableMap()
        updatedChoices[caseId] = choice
        _state.value = _state.value.copy(userChoices = updatedChoices)
        Timber.d("AIViewModel: user chose for case $caseId -> $choice")
    }

    /**
     * Performs AI-driven analysis via GeminiRepository.
     */
    fun performAnalysis() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val userLanguage = sharedPreferences.getString("userLanguage", "English")!!
                val questionType = sharedPreferences.getString(PrefKeys.USER_QUESTION_TYPE, "behavioral") ?: "behavioral"
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

                Timber.d("AIViewModel: analysis completed successfully")

            } catch (e: Exception) {
                Timber.e(e, "AIViewModel: analysis failed")
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
            Timber.e("AIViewModel: failed to get external storage directory.")
            return
        }

        val outputDirectory = caseFile.parentFile
        if (outputDirectory != null && !outputDirectory.exists() && !outputDirectory.mkdirs()) {
            Timber.e("AIViewModel: failed to create directory: ${outputDirectory.absolutePath}")
            return
        }

        try {
            FileWriter(caseFile).use { writer ->
                writer.write(caseData.toString() + " user choice: $userChoice")
            }
            Timber.d("AIViewModel: saved user choice to ${caseFile.absolutePath}")
        } catch (e: IOException) {
            Timber.e(e, "AIViewModel: failed to save user choice")
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

                Timber.d("AIViewModel: game reset complete.")
            } catch (e: Exception) {
                Timber.e(e, "AIViewModel: failed to reset game")
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
                Timber.d("AIViewModel: max turns reached; no further turns will be generated.")
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
                val questionType = sharedPreferences.getString(PrefKeys.USER_QUESTION_TYPE, "behavioral") ?: "behavioral"
                val subType = sharedPreferences.getString(PrefKeys.USER_SUBTYPE, "scenario_analysis") ?: "scenario_analysis"
                val sex = sharedPreferences.getString(PrefKeys.USER_GENDER, "any")!!

                val previousAnswers = _state.value.userChoices
                val previousCases = allCasesList

                Timber.d("AIViewModel: generating cases for turn $turn (questionType=$questionType, subType=$subType)...")

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

                Timber.d("AIViewModel: turn $turn cases loaded successfully (${responseCases.size} cases)")

            } catch (e: Exception) {
                Timber.e(e, "AIViewModel: failed to initiate turn $turn")
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to start turn $turn: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun clearAnalysisData() {
        _state.value = _state.value.copy(analysisData = null, analysisResult = null)
        Timber.d("AIViewModel: cleared analysis data.")
    }
}
