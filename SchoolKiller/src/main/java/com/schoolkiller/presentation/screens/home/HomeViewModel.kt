package com.schoolkiller.presentation.screens.home

import androidx.lifecycle.viewModelScope
import com.schoolkiller.data.DataStoreRepository
import com.example.shared.data.repositories.DeleteFileRepository
import com.example.shared.data.repositories.SaveFileRepository
import com.schoolkiller.domain.ParameterProperties
import com.example.shared.domain.prompt.options.ExplanationLevelOption
import com.example.shared.domain.prompt.options.GradeOption
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.ads.OpenAdUseCase
import com.example.shared.presentation.screens.home.BaseHomeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    saveFileRepository: SaveFileRepository,
    deleteFileRepository: DeleteFileRepository,
    openAdUseCase: OpenAdUseCase,
    private val dataStoreRepository: DataStoreRepository,
    speechConverter: SpeechConverter
) : BaseHomeViewModel(saveFileRepository, deleteFileRepository, openAdUseCase, speechConverter) {

    private val _parametersPropertiesState = MutableStateFlow(ParameterProperties())
    val parametersPropertiesState: StateFlow<ParameterProperties> = _parametersPropertiesState
        .onStart {
            /** This is like the init block */
            readGradeOptionState()
            readLanguageOptionState()
            readExplanationLevelOptionState()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ParameterProperties.defaultProperties
        )

    override fun resetSettings() {
        val defaultProperties = ParameterProperties.defaultProperties.copy(
            // language stay the same
            language = _parametersPropertiesState.value.language
        )
        _parametersPropertiesState.value = defaultProperties
        persistExplanationLevelOptionState(defaultProperties.explanationLevel)
        persistGradeOptionState(defaultProperties.grade)
    }

    private fun readGradeOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readGradeOptionState
                .map { GradeOption.valueOf(it) }
                .catch { exception ->
                    Timber.e(exception, "Error reading grade option state")
                }
                .collect { gradeOption ->
                    updateSelectedGradeOption(gradeOption)
                }
        }
    }

    fun updateSelectedGradeOption(newClassSelection: GradeOption) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(grade = newClassSelection)
        }
        persistGradeOptionState(newClassSelection)
    }

    fun updateSelectedLanguageOption(newLanguageSelection: SolutionLanguageOption) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(language = newLanguageSelection)
        }
        persistLanguageOptionState(newLanguageSelection)
    }

    fun updateSelectedExplanationLevelOption(newExplanationLevelSelection: ExplanationLevelOption) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(explanationLevel = newExplanationLevelSelection)
        }
        persistExplanationLevelOptionState(newExplanationLevelSelection)
    }

    private fun persistGradeOptionState(gradeOption: GradeOption) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistGradeOptionState(gradeOption = gradeOption)
        }
    }

    private fun persistLanguageOptionState(solutionLanguageOption: SolutionLanguageOption) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistLanguageOptionState(languageOption = solutionLanguageOption)
        }
    }

    private fun persistExplanationLevelOptionState(explanationLevelOption: ExplanationLevelOption) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistExplanationLevelOptionState(explanationLevelOption = explanationLevelOption)
        }
    }

    private fun readLanguageOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readLanguageOptionState
                .map { SolutionLanguageOption.valueOf(it) }
                .catch { exception ->
                    Timber.e(exception, "Error reading language option state")
                    updateSelectedLanguageOption(SolutionLanguageOption.DEFAULT)
                }
                .collect { languageOption ->
                    updateSelectedLanguageOption(languageOption)
                }
        }
    }


    private fun readExplanationLevelOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readExplanationLevelOptionState
                .map { ExplanationLevelOption.valueOf(it) }
                .catch { exception ->
                    Timber.e(exception, "Error reading explanation level option state")
                    updateSelectedExplanationLevelOption(ExplanationLevelOption.SHORT_EXPLANATION)
                }
                .collect { explanationLevelOption ->
                    updateSelectedExplanationLevelOption(explanationLevelOption)
                }
        }
    }

    fun getSelectedLanguage(): Int {
        return parametersPropertiesState.value.language.arrayIndex
    }

}