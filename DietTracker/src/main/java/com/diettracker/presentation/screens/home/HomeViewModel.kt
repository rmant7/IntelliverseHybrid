package com.diettracker.presentation.screens.home

import androidx.lifecycle.viewModelScope
import com.diettracker.data.DataStoreRepository
import com.example.shared.data.repositories.DeleteFileRepository
import com.example.shared.data.repositories.SaveFileRepository
import com.diettracker.domain.ParameterProperties
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.ads.OpenAdUseCase
import com.example.shared.domain.prompt.options.GenderOption
import com.example.shared.domain.prompt.options.PhysicalActivityOption
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
            readLanguageOptionState()
            readGenderOptionState()
            readHeightOptionState()
            readWeightOptionState()
            readAgeOptionState()
            readPhysicalActivityOptionState()
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
        persistGenderOptionState(defaultProperties.gender)
        persistAgeOptionState(defaultProperties.age)
        persistHeightOptionState(defaultProperties.height)
        persistWeightOptionState(defaultProperties.weight)
        persistPhysicalActivityOptionState(defaultProperties.physicalActivity)
    }

    fun updateSelectedLanguageOption(newLanguageSelection: SolutionLanguageOption) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(language = newLanguageSelection)
        }
        persistLanguageOptionState(newLanguageSelection)
    }

    fun updateSelectedPhysicalActivityOption(newPhysicalActivitySelection: PhysicalActivityOption?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(physicalActivity = newPhysicalActivitySelection)
        }
        persistPhysicalActivityOptionState(newPhysicalActivitySelection)
    }

    fun updateSelectedHeightOption(newHeightSelection: Int?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(height = newHeightSelection)
        }
        persistHeightOptionState(newHeightSelection)
    }

    fun updateSelectedWeightOption(newWeightSelection: Int?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(weight = newWeightSelection)
        }
        persistWeightOptionState(newWeightSelection)
    }

    fun updateSelectedGenderOption(newGenderSelection: GenderOption?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(gender = newGenderSelection)
        }
        persistGenderOptionState(newGenderSelection)
    }

    fun updateSelectedAgeOption(newAge: Int?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(age = newAge)
        }
        persistAgeOptionState(newAge)
    }

    private fun persistLanguageOptionState(solutionLanguageOption: SolutionLanguageOption) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistLanguageOptionState(languageOption = solutionLanguageOption)
        }
    }

    private fun persistPhysicalActivityOptionState(physicalActivityOption: PhysicalActivityOption?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistPhysicalActivityOptionState(physicalActivityOption = physicalActivityOption)
        }
    }

    private fun persistGenderOptionState(genderOption: GenderOption?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistGenderOptionState(genderOption = genderOption)
        }
    }

    private fun persistHeightOptionState(heightOption: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistHeightOptionState(heightOption = heightOption)
        }
    }

    private fun persistWeightOptionState(weightOption: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistWeightOptionState(weightOption = weightOption)
        }
    }

    private fun persistAgeOptionState(ageOption: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistAgeOptionState(ageOption = ageOption)
        }
    }

    private fun readGenderOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readGenderOptionState
                .map { genderOption -> genderOption?.let { GenderOption.valueOf(it) } }
                .catch { exception ->
                    Timber.e(exception, "Error reading gender option state")
                    updateSelectedGenderOption(GenderOption.MALE)
                }
                .collect { genderOption ->
                    updateSelectedGenderOption(genderOption)
                }
        }
    }

    private fun readPhysicalActivityOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readPhysicalActivityOptionState
                .map { physicalActivity -> physicalActivity?.let { PhysicalActivityOption.valueOf(it) } }
                .catch { exception ->
                    Timber.e(exception, "Error reading physical activity option state")
                    updateSelectedPhysicalActivityOption(PhysicalActivityOption.MODERATE_ACTIVE)
                }
                .collect { languageOption ->
                    updateSelectedPhysicalActivityOption(languageOption)
                }
        }
    }

    private fun readHeightOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readHeightOptionState
                .map { it?.toInt() }
                .catch { exception ->
                    Timber.e(exception, "Error reading height option state")
                }
                .collect { heightOption ->
                    updateSelectedHeightOption(heightOption)
                }
        }
    }

    private fun readWeightOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readWeightOptionState
                .map { it?.toInt() }
                .catch { exception ->
                    Timber.e(exception, "Error reading weight option state")
                }
                .collect { weightOption ->
                    updateSelectedWeightOption(weightOption)
                }
        }
    }

    private fun readAgeOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readAgeOptionState
                .map { it?.toInt() }
                .catch { exception ->
                    Timber.e(exception, "Error reading age option state")
                }
                .collect { ageOption ->
                    updateSelectedAgeOption(ageOption)
                }
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

    fun getSelectedLanguage(): Int {
        return parametersPropertiesState.value.language.arrayIndex
    }

}