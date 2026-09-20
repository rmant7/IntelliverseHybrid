package com.diettracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.shared.domain.prompt.options.GenderOption
import com.example.shared.domain.prompt.options.PhysicalActivityOption
import com.example.shared.domain.prompt.options.SolutionLanguageOption
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
        val physicalActivityOption = stringPreferencesKey(name = Constants.PHYSICAL_ACTIVITY_OPTION)
        val genderOption = stringPreferencesKey(name = Constants.GENDER_OPTION)
        val weightOption = stringPreferencesKey(name = Constants.WEIGHT_OPTION)
        val heightOption = stringPreferencesKey(name = Constants.HEIGHT_OPTION)
        val ageOption = stringPreferencesKey(name = Constants.AGE_OPTION)
    }

    private val dataStore = context.dataStore

    suspend fun persistLanguageOptionState(languageOption: SolutionLanguageOption) {
        dataStore.edit { preference ->
            preference[PreferenceKeys.languageOptionState] = languageOption.name
        }
    }

    suspend fun persistPhysicalActivityOptionState(physicalActivityOption: PhysicalActivityOption?) {
        dataStore.edit { preference ->
            if (physicalActivityOption != null) {
                preference[PreferenceKeys.physicalActivityOption] = physicalActivityOption.name
            } else {
                preference.remove(PreferenceKeys.physicalActivityOption)
            }
        }
    }

    suspend fun persistGenderOptionState(genderOption: GenderOption?) {
        dataStore.edit { preference ->
            if (genderOption != null) {
                preference[PreferenceKeys.genderOption] = genderOption.name
            } else {
                preference.remove(PreferenceKeys.genderOption)
            }
        }
    }

    suspend fun persistHeightOptionState(heightOption: Int?) {
        dataStore.edit { preference ->
            if (heightOption != null) {
                preference[PreferenceKeys.heightOption] = heightOption.toString()
            } else {
                preference.remove(PreferenceKeys.heightOption)
            }
        }
    }

    suspend fun persistWeightOptionState(weightOption: Int?) {
        dataStore.edit { preference ->
            if (weightOption != null) {
                preference[PreferenceKeys.weightOption] = weightOption.toString()
            } else {
                preference.remove(PreferenceKeys.weightOption)
            }
        }
    }

    suspend fun persistAgeOptionState(ageOption: Int?) {
        dataStore.edit { preference ->
            if (ageOption != null) {
                preference[PreferenceKeys.ageOption] = ageOption.toString()
            } else {
                preference.remove(PreferenceKeys.ageOption)
            }
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

    val readPhysicalActivityOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.physicalActivityOption]
        }

    val readGenderOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.genderOption]
        }

    val readHeightOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.heightOption]
        }

    val readWeightOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.weightOption]
        }

    val readAgeOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.ageOption]
        }

}