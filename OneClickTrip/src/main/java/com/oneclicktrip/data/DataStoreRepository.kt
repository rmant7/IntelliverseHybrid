package com.oneclicktrip.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.domain.prompt.options.TransportationType
import com.example.shared.domain.prompt.options.TripStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale
import javax.inject.Inject


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFERENCE_NAME)

@ViewModelScoped
open class DataStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object PreferenceKeys {
        val languageOptionState = stringPreferencesKey(name = Constants.LANGUAGE_OPTION)
        val originLocationOptionState = stringPreferencesKey(name = Constants.ORIGIN_LOCATION)
        val maxBudgetOptionState = stringPreferencesKey(name = Constants.MAX_BUDGET_OPTION)
        val tripDurationOptionState = stringPreferencesKey(name = Constants.TRIP_DURATION_OPTION)
        val travelersNumberOptionState = stringPreferencesKey(name = Constants.TRAVELERS_NUMBER_OPTION)
        val transportationTypesOption = stringPreferencesKey(name = Constants.TRANSPORTATION_TYPES)
        val tripStylesOption = stringPreferencesKey(name = Constants.TRIP_STYLES)
        val tripPathOptionState = stringPreferencesKey(name = Constants.TRIP_PATH)
        val oneWayOptionState = stringPreferencesKey(name = Constants.ONE_WAY)
    }

    private val dataStore = context.dataStore

    suspend fun persistLanguageOptionState(languageOption: SolutionLanguageOption) {
        dataStore.edit { preference ->
            preference[PreferenceKeys.languageOptionState] = languageOption.name
        }
    }

    suspend fun persistOriginLocationOptionState(originLocationOption: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.originLocationOptionState] = originLocationOption
        }
    }

    suspend fun persistMaxBudgetOptionState(maxBudgetOption: Int?) {
        dataStore.edit { preferences ->
            if (maxBudgetOption != null) {
                preferences[PreferenceKeys.maxBudgetOptionState] = maxBudgetOption.toString()
            } else {
                preferences.remove(PreferenceKeys.maxBudgetOptionState)
            }
        }
    }

    suspend fun persistTripDurationOptionState(tripDurationOption: Int?) {
        dataStore.edit { preferences ->
            if (tripDurationOption != null) {
                preferences[PreferenceKeys.tripDurationOptionState] = tripDurationOption.toString()
            } else {
                preferences.remove(PreferenceKeys.tripDurationOptionState)
            }
        }
    }

    suspend fun persistTravelersNumberOptionState(travelersNumberOption: Int?) {
        dataStore.edit { preferences ->
            if (travelersNumberOption != null) {
                preferences[PreferenceKeys.travelersNumberOptionState] = travelersNumberOption.toString()
            } else {
                preferences.remove(PreferenceKeys.travelersNumberOptionState)
            }
        }
    }

    suspend fun persistTransportationTypes(list: List<TransportationType>) {
        val value = list.joinToString(",") { it.name }
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.transportationTypesOption] = value
        }
    }

    suspend fun persistTripStyles(list: List<TripStyle>) {
        val value = list.joinToString(",") { it.name }
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.tripStylesOption] = value
        }
    }

    suspend fun persistTripPathOptionState(list: List<String>) {
        val value = list.joinToString(",")
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.tripPathOptionState] = value
        }
    }

    suspend fun persistOneWayOptionState(oneWayOption: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.oneWayOptionState] = oneWayOption.toString()
        }
    }

    private fun checkError(exception: Throwable): Preferences {
        return when (exception) {
            is IOException -> emptyPreferences()
            is IllegalArgumentException -> {
                emptyPreferences()
            }
            else -> throw exception
        }
    }

    val readLanguageOptionState: Flow<String> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.languageOptionState]
                ?: SolutionLanguageOption.fromLocale(Locale.getDefault())?.name
                ?: SolutionLanguageOption.DEFAULT.name
        }

    val readOriginLocationOptionState: Flow<String> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.originLocationOptionState] ?: ""
        }

    val readMaxBudgetOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.maxBudgetOptionState]
        }

    val readTripDurationOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.tripDurationOptionState]
        }

    val readTravelersNumberOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.travelersNumberOptionState]
        }

    val readTransportationTypesState: Flow<List<TransportationType>> = dataStore.data
        .catch { emit(checkError(it)) }
        .map { preferences ->
            preferences[PreferenceKeys.transportationTypesOption]
                ?.split(",")
                ?.mapNotNull { runCatching { TransportationType.valueOf(it) }.getOrNull() }
                ?: emptyList()
        }

    val readTripStylesState: Flow<List<TripStyle>> = dataStore.data
        .catch { emit(checkError(it)) }
        .map { preferences ->
            preferences[PreferenceKeys.tripStylesOption]
                ?.split(",")
                ?.mapNotNull { runCatching { TripStyle.valueOf(it) }.getOrNull() }
                ?: emptyList()
        }

    val readTripPathOptionState: Flow<List<String>> = dataStore.data
        .catch { emit(checkError(it)) }
        .map { preferences ->
            preferences[PreferenceKeys.tripPathOptionState]
                ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        }

    val readOneWayOptionState: Flow<String> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.oneWayOptionState] ?: false.toString()
        }

}