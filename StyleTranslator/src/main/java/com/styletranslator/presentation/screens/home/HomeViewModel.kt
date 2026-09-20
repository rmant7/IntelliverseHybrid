package com.styletranslator.presentation.screens.home

import androidx.lifecycle.viewModelScope
import com.styletranslator.data.DataStoreRepository
import com.example.shared.data.repositories.DeleteFileRepository
import com.example.shared.data.repositories.SaveFileRepository
import com.styletranslator.domain.ParameterProperties
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.ads.OpenAdUseCase
import com.example.shared.domain.prompt.options.Category
import com.example.shared.domain.prompt.options.GenderOption
import com.example.shared.domain.prompt.options.Mentality
import com.example.shared.domain.prompt.options.Style
import com.example.shared.domain.prompt.options.TonePreference
import com.example.shared.domain.prompt.options.TransformationLevel
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
            readSourceGenderOptionState()
            readTargetGenderOptionState()
            readSourceAgeOptionState()
            readTargetAgeOptionState()
            readCategoryOptionState()
            readStyleOptionState()
            readMentalityOptionState()
            readTranslationScaleOptionState()
            readToneOptionState()
            readTransformationLevelOptionState()
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
        persistCategoryOptionState(defaultProperties.category)
        persistToneOptionState(defaultProperties.tonePreference)
        persistTransformationLevelOptionState(defaultProperties.transformationLevel)
        persistStyleOptionState(defaultProperties.style)
        persistMentalityOptionState(defaultProperties.mentality)
        persistTranslationScaleOptionState(defaultProperties.translationScale)
        persistSourceGenderOptionState(defaultProperties.sourceGender)
        persistTargetGenderOptionState(defaultProperties.targetGender)
        persistSourceAgeOptionState(defaultProperties.sourceAge)
        persistTargetAgeOptionState(defaultProperties.targetAge)
    }

    fun updateSelectedCategoryOption(newCategorySelection: Category?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(category = newCategorySelection)
        }
        persistCategoryOptionState(newCategorySelection)
    }

    fun updateSelectedToneOption(newToneSelection: TonePreference?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(tonePreference = newToneSelection)
        }
        persistToneOptionState(newToneSelection)
    }

    fun updateSelectedTransformationLevelOption(newTransformationLevelSelection: TransformationLevel) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(transformationLevel = newTransformationLevelSelection)
        }
        persistTransformationLevelOptionState(newTransformationLevelSelection)
    }

    fun updateSelectedStyleOption(newStyleSelection: Style?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(style = newStyleSelection)
        }
        persistStyleOptionState(newStyleSelection)
    }

    fun updateSelectedMentalityOption(newMentalitySelection: Mentality?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(mentality = newMentalitySelection)
        }
        persistMentalityOptionState(newMentalitySelection)
    }

    fun updateSelectedTranslationScaleOption(newTranslationScaleSelection: Float?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(translationScale = newTranslationScaleSelection)
        }
        persistTranslationScaleOptionState(newTranslationScaleSelection)
    }

    fun updateSelectedLanguageOption(newLanguageSelection: SolutionLanguageOption) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(language = newLanguageSelection)
        }
        persistLanguageOptionState(newLanguageSelection)
    }

    fun updateSelectedSourceGenderOption(newGenderSelection: GenderOption?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(sourceGender = newGenderSelection)
        }
        persistSourceGenderOptionState(newGenderSelection)
    }

    fun updateSelectedTargetGenderOption(newGenderSelection: GenderOption?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(targetGender = newGenderSelection)
        }
        persistTargetGenderOptionState(newGenderSelection)
    }

    fun updateSelectedSourceAgeOption(newAge: Int?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(sourceAge = newAge)
        }
        persistSourceAgeOptionState(newAge)
    }

    fun updateSelectedTargetAgeOption(newAge: Int?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(targetAge = newAge)
        }
        persistTargetAgeOptionState(newAge)
    }

    private fun persistCategoryOptionState(categoryOption: Category?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistCategoryOptionState(categoryOption)
        }
    }

    private fun persistToneOptionState(toneOption: TonePreference?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistToneOptionState(toneOption)
        }
    }

    private fun persistTransformationLevelOptionState(transformationLevelOption: TransformationLevel) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistTransformationLevelOptionState(transformationLevelOption)
        }
    }

    private fun persistStyleOptionState(styleOption: Style?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistStyleOptionState(styleOption)
        }
    }

    private fun persistMentalityOptionState(mentalityOption: Mentality?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistMentalityOptionState(mentalityOption)
        }
    }

    private fun persistTranslationScaleOptionState(translationScaleOption: Float?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistTranslationScaleOptionState(translationScaleOption)
        }
    }

    private fun persistLanguageOptionState(solutionLanguageOption: SolutionLanguageOption) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistLanguageOptionState(languageOption = solutionLanguageOption)
        }
    }

    private fun persistSourceGenderOptionState(genderOption: GenderOption?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistSourceGenderOptionState(genderOption = genderOption)
        }
    }

    private fun persistTargetGenderOptionState(genderOption: GenderOption?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistTargetGenderOptionState(genderOption = genderOption)
        }
    }

    private fun persistSourceAgeOptionState(ageOption: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistSourceAgeOptionState(ageOption = ageOption)
        }
    }

    private fun persistTargetAgeOptionState(ageOption: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistTargetAgeOptionState(ageOption = ageOption)
        }
    }

    private fun readCategoryOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readCategoryOptionState
                .map { category -> category?.let { Category.valueOf(it) } }
                .catch { exception ->
                    Timber.e(exception, "Error reading category option state")
                    updateSelectedCategoryOption(Category.GENERAL) // Default fallback
                }
                .collect { categoryOption ->
                    updateSelectedCategoryOption(categoryOption)
                }
        }
    }

    private fun readToneOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readToneOptionState
                .map { tonePreference -> tonePreference?.let { TonePreference.valueOf(it) } }
                .catch { exception ->
                    Timber.e(exception, "Error reading tone option state")
                    updateSelectedToneOption(TonePreference.NEUTRAL)
                }
                .collect { toneOption ->
                    updateSelectedToneOption(toneOption)
                }
        }
    }

    private fun readTransformationLevelOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readTransformationLevelOptionState
                .map { TransformationLevel.valueOf(it) }
                .catch { exception ->
                    Timber.e(exception, "Error reading transformation level option state")
                    updateSelectedTransformationLevelOption(TransformationLevel.MODERATE)
                }
                .collect { transformationLevelOption ->
                    updateSelectedTransformationLevelOption(transformationLevelOption)
                }
        }
    }

    private fun readStyleOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readStyleOptionState
                .map { style -> style?.let { Style.valueOf(it) } }
                .catch { exception ->
                    Timber.e(exception, "Error reading style option state")
                }
                .collect { styleOption ->
                    updateSelectedStyleOption(styleOption)
                }
        }
    }

    private fun readMentalityOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readMentalityOptionState
                .map { mentality -> mentality?.let { Mentality.valueOf(it) } }
                .catch { exception ->
                    Timber.e(exception, "Error reading mentality option state")
                }
                .collect { mentalityOption ->
                    updateSelectedMentalityOption(mentalityOption)
                }
        }
    }

    private fun readTranslationScaleOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readTranslationStateOptionState
                .map { it?.toFloat() }
                .catch { exception ->
                    Timber.e(exception, "Error reading translation scale option state")
                }
                .collect { translationScaleOption ->
                    updateSelectedTranslationScaleOption(translationScaleOption)
                }
        }
    }

    private fun readSourceGenderOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readSourceGenderOptionState
                .map { genderOption -> genderOption?.let { GenderOption.valueOf(it) } }
                .catch { exception ->
                    Timber.e(exception, "Error reading source gender option state")
                    updateSelectedSourceGenderOption(GenderOption.MALE)
                }
                .collect { genderOption ->
                    updateSelectedSourceGenderOption(genderOption)
                }
        }
    }

    private fun readTargetGenderOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readTargetGenderOptionState
                .map { genderOption -> genderOption?.let { GenderOption.valueOf(it) } }
                .catch { exception ->
                    Timber.e(exception, "Error reading target gender option state")
                    updateSelectedTargetGenderOption(GenderOption.MALE)
                }
                .collect { genderOption ->
                    updateSelectedTargetGenderOption(genderOption)
                }
        }
    }

    private fun readSourceAgeOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readSourceAgeOptionState
                .map { it?.toInt() }
                .catch { exception ->
                    Timber.e(exception, "Error reading source age option state")
                }
                .collect { ageOption ->
                    updateSelectedSourceAgeOption(ageOption)
                }
        }
    }

    private fun readTargetAgeOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readTargetAgeOptionState
                .map { it?.toInt() }
                .catch { exception ->
                    Timber.e(exception, "Error reading target age option state")
                }
                .collect { ageOption ->
                    updateSelectedTargetAgeOption(ageOption)
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