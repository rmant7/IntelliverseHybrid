package com.oneclicktrip.presentation.screens.home

import androidx.lifecycle.viewModelScope
import com.oneclicktrip.data.DataStoreRepository
import com.example.shared.data.repositories.DeleteFileRepository
import com.example.shared.data.repositories.SaveFileRepository
import com.oneclicktrip.domain.ParameterProperties
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.ads.OpenAdUseCase
import com.example.shared.domain.prompt.options.TransportationType
import com.example.shared.domain.prompt.options.TripStyle
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
            readOriginLocationOptionState()
            readMaxBudgetOptionState()
            readTripDurationOptionState()
            readTravelersNumberOptionState()
            readTransportationTypesState()
            readTripStylesState()
            readTripPathState()
            readOneWayState()
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
        persistOriginLocationOptionState(defaultProperties.originLocation)
        persistMaxBudgetOptionState(defaultProperties.maxBudget)
        persistTripDurationOptionState(defaultProperties.tripDuration)
        persistTravelersNumberOptionState(defaultProperties.travelersNumber)
        persistTransportationTypes(defaultProperties.transportationTypes)
        persistTripStyles(defaultProperties.tripStyles)
        persistTripPath(defaultProperties.tripPath)
        persistOneWayOptionState(defaultProperties.isOneWay)
    }

    fun updateSelectedLanguageOption(newLanguageSelection: SolutionLanguageOption) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(language = newLanguageSelection)
        }
        persistLanguageOptionState(newLanguageSelection)
    }

    fun updateSelectedOriginLocationOption(newOriginLocationSelection: String) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(originLocation = newOriginLocationSelection)
        }
        persistOriginLocationOptionState(newOriginLocationSelection)
    }

    fun updateSelectedMaxBudgetOption(newMaxBudget: Int?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(maxBudget = newMaxBudget)
        }
        persistMaxBudgetOptionState(newMaxBudget)
    }

    fun updateSelectedTripDurationOption(newTripDuration: Int?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(tripDuration = newTripDuration)
        }
        persistTripDurationOptionState(newTripDuration)
    }

    fun updateSelectedTravelersNumberOption(newTravelersNumber: Int?) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(travelersNumber = newTravelersNumber)
        }
        persistTravelersNumberOptionState(newTravelersNumber)
    }

    fun updateTransportationTypes(updatedList: List<TransportationType>) {
        _parametersPropertiesState.update { current ->
            current.copy(transportationTypes = updatedList)
        }
        persistTransportationTypes(updatedList)
    }

    fun updateTripStyles(updatedList: List<TripStyle>) {
        _parametersPropertiesState.update { current ->
            current.copy(tripStyles = updatedList)
        }
        persistTripStyles(updatedList)
    }

    fun updateTripPath(updatedList: List<String>) {
        _parametersPropertiesState.update { current ->
            current.copy(tripPath = updatedList)
        }
        persistTripPath(updatedList)
    }

    fun updateOneWayOption(newOneWayOption: Boolean) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(isOneWay = newOneWayOption)
        }
        persistOneWayOptionState(newOneWayOption)
    }

    private fun persistLanguageOptionState(solutionLanguageOption: SolutionLanguageOption) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistLanguageOptionState(languageOption = solutionLanguageOption)
        }
    }

    private fun persistOriginLocationOptionState(originLocationOption: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistOriginLocationOptionState(originLocationOption = originLocationOption)
        }
    }

    private fun persistMaxBudgetOptionState(maxBudgetOption: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistMaxBudgetOptionState(maxBudgetOption = maxBudgetOption)
        }
    }

    private fun persistTripDurationOptionState(tripDurationOption: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistTripDurationOptionState(tripDurationOption = tripDurationOption)
        }
    }

    private fun persistTravelersNumberOptionState(travelersNumberOption: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistTravelersNumberOptionState(travelersNumberOption = travelersNumberOption)
        }
    }

    private fun persistTransportationTypes(types: List<TransportationType>) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistTransportationTypes(types)
        }
    }

    private fun persistTripStyles(styles: List<TripStyle>) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistTripStyles(styles)
        }
    }

    private fun persistTripPath(path: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistTripPathOptionState(path)
        }
    }

    private fun persistOneWayOptionState(oneWayOption: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistOneWayOptionState(oneWayOption = oneWayOption)
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

    private fun readOriginLocationOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readOriginLocationOptionState
                .catch { exception ->
                    Timber.e(exception, "Error origin location option state")
                    updateSelectedLanguageOption(SolutionLanguageOption.DEFAULT)
                }
                .collect { originLocationOption ->
                    updateSelectedOriginLocationOption(originLocationOption)
                }
        }
    }

    private fun readMaxBudgetOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readMaxBudgetOptionState
                .map { it?.toInt() }
                .catch { exception ->
                    Timber.e(exception, "Error reading max budget option state")
                }
                .collect { maxBudgetOption ->
                    updateSelectedMaxBudgetOption(maxBudgetOption)
                }
        }
    }

    private fun readTripDurationOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readTripDurationOptionState
                .map { it?.toInt() }
                .catch { exception ->
                    Timber.e(exception, "Error reading trip duration option state")
                }
                .collect { tripDurationOption ->
                    updateSelectedTripDurationOption(tripDurationOption)
                }
        }
    }

    private fun readTravelersNumberOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readTravelersNumberOptionState
                .map { it?.toInt() }
                .catch { exception ->
                    Timber.e(exception, "Error reading travelers number option state")
                }
                .collect { travelersNumberOption ->
                    updateSelectedTravelersNumberOption(travelersNumberOption)
                }
        }
    }

    private fun readTransportationTypesState() {
        viewModelScope.launch {
            dataStoreRepository.readTransportationTypesState
                .catch { Timber.e(it) }
                .collect { list ->
                    updateTransportationTypes(list)
                }
        }
    }

    private fun readTripStylesState() {
        viewModelScope.launch {
            dataStoreRepository.readTripStylesState
                .catch { Timber.e(it) }
                .collect { list ->
                    updateTripStyles(list)
                }
        }
    }

    private fun readTripPathState() {
        viewModelScope.launch {
            dataStoreRepository.readTripPathOptionState
                .catch { Timber.e(it) }
                .collect { list ->
                    updateTripPath(list)
                }
        }
    }

    private fun readOneWayState() {
        viewModelScope.launch {
            dataStoreRepository.readOneWayOptionState
                .map { it.toBoolean() }
                .catch { Timber.e(it) }
                .collect { newOneWayOption ->
                    updateOneWayOption(newOneWayOption)
                }
        }
    }

    fun getSelectedLanguage(): Int {
        return parametersPropertiesState.value.language.arrayIndex
    }

}