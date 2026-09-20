package com.schoolkiller.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.schoolkiller.domain.ParameterProperties
import com.example.shared.domain.prompt.options.ExplanationLevelOption
import com.example.shared.domain.prompt.options.GradeOption
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
        val gradeOptionState = stringPreferencesKey(name = Constants.GRADE_OPTION)
        val languageOptionState = stringPreferencesKey(name = Constants.LANGUAGE_OPTION)
        val explanationLevelOptionState =
            stringPreferencesKey(name = Constants.EXPLANATION_LEVEL_OPTION)
    }

    private val dataStore = context.dataStore

    suspend fun persistGradeOptionState(gradeOption: GradeOption) {
        dataStore.edit { preference ->
            preference[PreferenceKeys.gradeOptionState] = gradeOption.name
        }
    }

    suspend fun persistLanguageOptionState(languageOption: SolutionLanguageOption) {
        dataStore.edit { preference ->
            preference[PreferenceKeys.languageOptionState] = languageOption.name
        }
    }

    suspend fun persistExplanationLevelOptionState(explanationLevelOption: ExplanationLevelOption) {
        dataStore.edit { preference ->
            preference[PreferenceKeys.explanationLevelOptionState] = explanationLevelOption.name
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

    val readGradeOptionState: Flow<String> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.gradeOptionState]
                ?: ParameterProperties.defaultProperties.grade.name
        }

    val readLanguageOptionState: Flow<String> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.languageOptionState]
                ?: SolutionLanguageOption.fromLocale(Locale.getDefault())?.name
                ?: SolutionLanguageOption.DEFAULT.name
        }

    val readExplanationLevelOptionState: Flow<String> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.explanationLevelOptionState]
                ?: ParameterProperties.defaultProperties.explanationLevel.name
        }

}