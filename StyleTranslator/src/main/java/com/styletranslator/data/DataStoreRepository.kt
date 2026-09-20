package com.styletranslator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.shared.domain.prompt.options.Category
import com.example.shared.domain.prompt.options.GenderOption
import com.example.shared.domain.prompt.options.Mentality
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.domain.prompt.options.Style
import com.example.shared.domain.prompt.options.TonePreference
import com.example.shared.domain.prompt.options.TransformationLevel
import com.styletranslator.domain.ParameterProperties
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
        val sourceGenderOption = stringPreferencesKey(name = Constants.SOURCE_GENDER_OPTION)
        val targetGenderOption = stringPreferencesKey(name = Constants.TARGET_GENDER_OPTION)
        val sourceAgeOption = stringPreferencesKey(name = Constants.SOURCE_AGE_OPTION)
        val targetAgeOption = stringPreferencesKey(name = Constants.TARGET_AGE_OPTION)
        val categoryOptionState = stringPreferencesKey(name = Constants.CATEGORY_OPTION)
        val tonePreferenceOptionState = stringPreferencesKey(name = Constants.TONE_PREFERENCE_OPTION)
        val transformationLevelOptionState = stringPreferencesKey(name = Constants.TRANSFORMATION_LEVEL_OPTION)
        val styleOptionState = stringPreferencesKey(name = Constants.STYLE_OPTION)
        val mentalityOptionState = stringPreferencesKey(name = Constants.MENTALITY_OPTION)
        val translationScaleState = stringPreferencesKey(name = Constants.TRANSLATION_SCALE_OPTION)
    }

    private val dataStore = context.dataStore

    suspend fun persistLanguageOptionState(languageOption: SolutionLanguageOption) {
        dataStore.edit { preference ->
            preference[PreferenceKeys.languageOptionState] = languageOption.name
        }
    }

    suspend fun persistSourceGenderOptionState(genderOption: GenderOption?) {
        dataStore.edit { preferences ->
            if (genderOption != null) {
                preferences[PreferenceKeys.sourceGenderOption] = genderOption.name
            } else {
                preferences.remove(PreferenceKeys.sourceGenderOption)
            }
        }
    }

    suspend fun persistTargetGenderOptionState(genderOption: GenderOption?) {
        dataStore.edit { preferences ->
            if (genderOption != null) {
                preferences[PreferenceKeys.targetGenderOption] = genderOption.name
            } else {
                preferences.remove(PreferenceKeys.targetGenderOption)
            }
        }
    }

    suspend fun persistSourceAgeOptionState(ageOption: Int?) {
        dataStore.edit { preferences ->
            if (ageOption != null) {
                preferences[PreferenceKeys.sourceAgeOption] = ageOption.toString()
            } else {
                preferences.remove(PreferenceKeys.sourceAgeOption)
            }
        }
    }

    suspend fun persistTargetAgeOptionState(ageOption: Int?) {
        dataStore.edit { preferences ->
            if (ageOption != null) {
                preferences[PreferenceKeys.targetAgeOption] = ageOption.toString()
            } else {
                preferences.remove(PreferenceKeys.targetAgeOption)
            }
        }
    }

    suspend fun persistCategoryOptionState(categoryOption: Category?) {
        dataStore.edit { preferences ->
            if (categoryOption != null) {
                preferences[PreferenceKeys.categoryOptionState] = categoryOption.name
            } else {
                preferences.remove(PreferenceKeys.categoryOptionState)
            }
        }
    }

    suspend fun persistToneOptionState(toneOption: TonePreference?) {
        dataStore.edit { preferences ->
            if (toneOption != null) {
                preferences[PreferenceKeys.tonePreferenceOptionState] = toneOption.name
            } else {
                preferences.remove(PreferenceKeys.tonePreferenceOptionState)
            }
        }
    }

    suspend fun persistTransformationLevelOptionState(transformationLevelOption: TransformationLevel) {
        dataStore.edit { preference ->
            preference[PreferenceKeys.transformationLevelOptionState] = transformationLevelOption.name
        }
    }

    suspend fun persistStyleOptionState(styleOption: Style?) {
        dataStore.edit { preferences ->
            if (styleOption != null) {
                preferences[PreferenceKeys.styleOptionState] = styleOption.name
            } else {
                preferences.remove(PreferenceKeys.styleOptionState)
            }
        }
    }

    suspend fun persistMentalityOptionState(mentalityOption: Mentality?) {
        dataStore.edit { preference ->
            if (mentalityOption != null) {
                preference[PreferenceKeys.mentalityOptionState] = mentalityOption.name
            } else {
                preference.remove(PreferenceKeys.mentalityOptionState)
            }
        }
    }

    suspend fun persistTranslationScaleOptionState(translationScaleOption: Float?) {
        dataStore.edit { preference ->
            if (translationScaleOption != null) {
                preference[PreferenceKeys.translationScaleState] = translationScaleOption.toString()
            } else {
                preference.remove(PreferenceKeys.translationScaleState)
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

    val readSourceGenderOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.sourceGenderOption]
            //?: ParameterProperties.defaultProperties.sourceGender.name
        }

    val readTargetGenderOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.targetGenderOption]
            //?: ParameterProperties.defaultProperties.targetGender.name
        }

    val readSourceAgeOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.sourceAgeOption]
            //?: ParameterProperties.defaultProperties.sourceAge.toString()
        }

    val readTargetAgeOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.targetAgeOption]
            //?: ParameterProperties.defaultProperties.targetAge.toString()
        }

    val readCategoryOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.categoryOptionState]
            //?: ParameterProperties.defaultProperties.category.name
        }

    val readToneOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.tonePreferenceOptionState]
            //?: ParameterProperties.defaultProperties.tonePreference.name
        }

    val readTransformationLevelOptionState: Flow<String> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.transformationLevelOptionState]
                ?: ParameterProperties.defaultProperties.transformationLevel.name
        }

    val readStyleOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.styleOptionState]
            //?: ParameterProperties.defaultProperties.style.name
        }

    val readMentalityOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.mentalityOptionState]
                // ?: ParameterProperties.defaultProperties.mentality.name
        }

    val readTranslationStateOptionState: Flow<String?> = dataStore.data
        .catch { exception ->
            emit(checkError(exception))
        }.map { preferences ->
            preferences[PreferenceKeys.translationScaleState]
        }


}