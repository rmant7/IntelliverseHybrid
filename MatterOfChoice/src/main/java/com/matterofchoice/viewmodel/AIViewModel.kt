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

    companion object {
        // Minimum number of loaded-but-unanswered cases to keep buffered at all times.
        private const val CASE_BUFFER_MIN = 3
    }

    private val sharedPreferences =
        application.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    private val geminiRepository = GeminiRepository()

    private var allCasesList: List<Case> = emptyList()
    private var i = 0 // File index counter

    private var _state = mutableStateOf(GameState())
    val state: State<GameState> = _state

    /**
     * Initializes the game if not already started. This is a guard against
     * double-generating, meant for Game.kt's own auto-resume check (landing
     * on the Game screen with no cases loaded yet) -- it only takes the
     * fresh-start branch when the ViewModel is still at its pristine
     * defaults. Settings' "Generate Cases" button must NOT call this; use
     * startNewGame() there instead, which always starts a new game
     * unconditionally regardless of what state a previous one left.
     */
    fun initiateGame() {
        if (_state.value.casesList.isEmpty()) {
            Timber.i("AIViewModel: starting fresh game")
            // A fresh game must read 0/0 at its first case -- see commit message
            // for why MainActivity.onCreate()/resetGame() alone weren't enough.
            sharedPreferences.edit {
                putInt("userScore", 0)
                putInt("totalScore", 0)
            }
            loadInitialCases()
        } else {
            Timber.d("AIViewModel: initiateGame skipped (cases: ${_state.value.casesList.size})")
            if (_state.value.isLoading && _state.value.casesList.isNotEmpty()) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Unconditionally starts a brand-new game: the user just picked new
     * settings and clicked "Generate Cases", and that has to actually
     * generate something regardless of whatever the ViewModel's in-memory
     * state currently holds (a finished game, one abandoned mid-buffer, or a
     * stuck isLoading from earlier). See initiateGame()'s doc comment for
     * why that guarded method isn't the right call here.
     */
    fun startNewGame() {
        Timber.i("AIViewModel: starting a new game from Settings (forced).")
        sharedPreferences.edit {
            putInt("userScore", 0)
            putInt("totalScore", 0)
        }
        allCasesList = emptyList()
        _state.value = GameState(
            isLoading = false,
            isFetchingMore = false,
            casesList = emptyList(),
            userChoices = emptyMap(),
            analysisResult = null,
            analysisData = null,
            error = null
        )
        loadInitialCases()
    }

    fun onUserChoice(caseId: String, choice: String) {
        val updatedChoices = _state.value.userChoices.toMutableMap()
        updatedChoices[caseId] = choice
        _state.value = _state.value.copy(userChoices = updatedChoices)
        Timber.d("AIViewModel: user chose for case $caseId -> $choice")
        maybeReplenishCases()
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

                Timber.i("AIViewModel: analysis completed successfully")

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
                    isFetchingMore = false,
                    casesList = emptyList(),
                    userChoices = emptyMap(),
                    analysisResult = null,
                    analysisData = null,
                    error = null
                )

                Timber.i("AIViewModel: game reset complete.")
            } catch (e: Exception) {
                Timber.e(e, "AIViewModel: failed to reset game")
                _state.value = _state.value.copy(error = "Reset failed: ${e.message}")
            }
        }
    }

    /**
     * Loads the first batch of cases for a new game (blocking, full-screen loader).
     */
    private fun loadInitialCases() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val cases = generateCaseBatch()
                allCasesList = allCasesList + cases
                _state.value = _state.value.copy(
                    isLoading = false,
                    casesList = cases,
                    error = null
                )
                Timber.i("AIViewModel: initial batch loaded (${cases.size} cases)")
            } catch (e: Exception) {
                Timber.e(e, "AIViewModel: failed to load initial cases")
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to start game: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    /**
     * Keeps a rolling buffer of unanswered cases so the player never sees a
     * fixed total or hits a hard stop: as soon as the number of loaded-but-
     * not-yet-answered cases drops to CASE_BUFFER_MIN, silently fetch another
     * batch in the background and append it. Mirrors the original Python
     * backend's prefetch_only/commit_answers buffering
     * (unified_server/apps/MatterOfChoice/app.py), just without a separate
     * commit step since there's no server-side session to reconcile here.
     */
    private fun maybeReplenishCases() {
        val unanswered = _state.value.casesList.count { !_state.value.userChoices.containsKey(it.case_id) }
        if (unanswered > CASE_BUFFER_MIN || _state.value.isFetchingMore || _state.value.isLoading) {
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isFetchingMore = true)
            try {
                val moreCases = generateCaseBatch()
                allCasesList = allCasesList + moreCases
                _state.value = _state.value.copy(
                    casesList = _state.value.casesList + moreCases,
                    isFetchingMore = false
                )
                Timber.i("AIViewModel: replenished buffer with ${moreCases.size} cases (${_state.value.casesList.size} total loaded)")
            } catch (e: Exception) {
                // Don't surface this as a screen-level error -- the player still has
                // whatever was already loaded. They'll just hit the "waiting for more
                // cases" state if they run out before the next answer retriggers this.
                Timber.e(e, "AIViewModel: buffer replenish failed; will retry on next answer")
                _state.value = _state.value.copy(isFetchingMore = false)
            }
        }
    }

    private suspend fun generateCaseBatch(): List<Case> {
        val userSubject = sharedPreferences.getString(PrefKeys.USER_SUBJECT, "life skills")!!
        val language = sharedPreferences.getString("userLanguage", "English")!!
        val age = sharedPreferences.getString(PrefKeys.USER_AGE, "25")!!.toIntOrNull() ?: 25
        val difficulty = sharedPreferences.getString(PrefKeys.USER_DIFFICULTY, "normal")!!.lowercase()
        val questionType = sharedPreferences.getString(PrefKeys.USER_QUESTION_TYPE, "behavioral") ?: "behavioral"
        val subType = sharedPreferences.getString(PrefKeys.USER_SUBTYPE, "scenario_analysis") ?: "scenario_analysis"
        val sex = sharedPreferences.getString(PrefKeys.USER_GENDER, "any")!!

        Timber.d("AIViewModel: generating case batch (questionType=$questionType, subType=$subType)...")

        return geminiRepository.generateCases(
            language = language,
            subject = userSubject,
            difficulty = difficulty,
            questionType = questionType,
            subType = subType,
            age = age,
            sex = sex,
            previousAnswers = _state.value.userChoices,
            previousCases = allCasesList
        )
    }

    fun clearAnalysisData() {
        _state.value = _state.value.copy(analysisData = null, analysisResult = null)
        Timber.d("AIViewModel: cleared analysis data.")
    }
}
