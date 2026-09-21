package com.matterofchoice.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.matterofchoice.MainActivity
import com.matterofchoice.R
import com.matterofchoice.Screens
import com.matterofchoice.common.DropDownMenu
import com.matterofchoice.common.GameButton
import com.matterofchoice.common.GameTextField
import com.matterofchoice.utils.LanguagePreferenceHelper
import com.matterofchoice.utils.LocaleHelper
import com.matterofchoice.viewmodel.AIViewModel
import timber.log.Timber
import java.util.Locale

object PrefKeys {
    const val MY_PREFS = "MyPrefs"
    const val FIRST_OPEN = "firstOpen"
    const val IS_FIRST = "isFirst"
    const val USER_SUBJECT = "userSubject"
    const val USER_AGE = "userAge"
    const val USER_GENDER = "userGender"
    const val USER_QUESTION_TYPE = "userQuestionType"
    const val USER_SUBTYPE = "subtype"
    const val USER_DIFFICULTY = "difficulty"
}

@Composable
fun Settings(navController: NavController, viewmodel: AIViewModel) {
    UserInput(navController = navController, viewmodel = viewmodel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInput(
    navController: NavController,
    viewmodel: AIViewModel
) {
    val context = LocalContext.current

    val sharedPreferences = context.getSharedPreferences(PrefKeys.MY_PREFS, Context.MODE_PRIVATE)

    var userSubject by remember { mutableStateOf("") }
    var userAge by remember { mutableStateOf("") }

    val questionTypeStudy = stringResource(id = R.string.question_type_study)
    val questionTypeBehavioral = stringResource(id = R.string.question_type_behavioral)
    val questionTypeHiring = stringResource(id = R.string.question_type_hiring)
    val questionTypes = listOf(questionTypeStudy, questionTypeBehavioral, questionTypeHiring)
    // Canonical, locale-independent keys aligned by index with questionTypes above --
    // GeminiRepository switches on these exact lowercase literals ("behavioral"/"study"/
    // "hiring") to pick the analysis aspect. The display list holds localized labels for
    // the dropdown; only this list is what actually gets persisted/sent to the API.
    val questionTypeKeys = listOf("study", "behavioral", "hiring")
    val isExposedType = remember { mutableStateOf(false) }

    val subtypesBehavioral = stringArrayResource(id = R.array.subtypes_behavioral_array).toList()
    val subtypesStudy = stringArrayResource(id = R.array.subtypes_study_array).toList()
    val subtypesHiring = stringArrayResource(id = R.array.subtypes_hiring_array).toList()

    val subtypesMap = mapOf(
        questionTypeBehavioral to subtypesBehavioral,
        questionTypeStudy to subtypesStudy,
        questionTypeHiring to subtypesHiring
    )
    val isExposedSub = remember { mutableStateOf(false) }

    val difficults = stringArrayResource(id = R.array.difficulty_levels_array).toList()
    val isDifficultExposed = remember { mutableStateOf(false) }
    val difficult = remember { mutableStateOf(difficults.firstOrNull() ?: "") }

    val userQuestionType = remember { mutableStateOf(questionTypes.firstOrNull() ?: "") }

    val availableSubtypes = subtypesMap[userQuestionType.value] ?: emptyList()
    val subtype = remember { mutableStateOf(availableSubtypes.firstOrNull() ?: "") }

    LaunchedEffect(userQuestionType.value) {
        val updatedSubtypes = subtypesMap[userQuestionType.value] ?: emptyList()
        subtype.value = updatedSubtypes.firstOrNull() ?: ""
    }

    val genderSelectPrompt = stringResource(id = R.string.gender_select_prompt)
    val genderMale = stringResource(id = R.string.gender_male)
    val genderFemale = stringResource(id = R.string.gender_female)
    val displayGenders = listOf(genderSelectPrompt, genderMale, genderFemale)
    val actualGendersForStorage = listOf("", genderMale, genderFemale)
    val userGender = remember { mutableStateOf(actualGendersForStorage[0]) }
    val isExposedGender = remember { mutableStateOf(false) }

    // Hoisted out of the dropdown block below so onClick (a non-Composable
    // lambda) can also use them to look up the canonical English name for
    // whatever language code ends up selected -- see the button's comment.
    val languageDisplayNames = stringArrayResource(id = R.array.languages)
    val languageCodesArr = stringArrayResource(id = R.array.language_codes)

    val initialLangCode = remember {
        val persisted = LanguagePreferenceHelper.getSelectedLanguage(context.applicationContext)
        if (persisted.isBlank()) {
            // No saved language -> use current system locale
            Locale.getDefault().toLanguageTag()
        } else persisted
    }

    var currentLanguageDisplayName by remember {
        mutableStateOf(Locale.forLanguageTag(initialLangCode).getDisplayName(Locale.getDefault()))
    }

    val currentSelectedLanguageCode = remember { mutableStateOf(initialLangCode) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {
        if (!sharedPreferences.contains(PrefKeys.FIRST_OPEN)) {
            sharedPreferences.edit().putBoolean(PrefKeys.FIRST_OPEN, false).apply()
        }
    }

    val isFormValid by remember(userSubject) {
        mutableStateOf(userSubject.isNotBlank() )
    }

    val persistedLangCode = remember {
        val persisted = LanguagePreferenceHelper.getSelectedLanguage(context.applicationContext)
        if (persisted.isBlank()) {
            // If no saved preference, fallback to system UI language
            Locale.getDefault().toLanguageTag()
        } else persisted
    }

    // 2️⃣ Use it to get display name for UI
    val persistedLangDisplayName = remember(persistedLangCode) {
        Locale.forLanguageTag(persistedLangCode).getDisplayName(Locale.getDefault())
    }

    // 3️⃣ Dropdown states

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = stringResource(id = R.string.settings_subtitle),
                fontSize = 16.sp,
            )

            GameTextField(
                text = userSubject,
                onValueChange = { userSubject = it },
                labelTxt = stringResource(id = R.string.settings_label_subject) 
            )

            DropDownMenu(
                itemsList = questionTypes,
                isExposed = isExposedType,
                selectedItem = userQuestionType,
                hint = stringResource(id = R.string.settings_hint_select_question_type) 
            )
            DropDownMenu(
                itemsList = availableSubtypes,
                isExposed = isExposedSub,
                selectedItem = subtype,
                hint = stringResource(id = R.string.settings_hint_select_subtype) 
            )
            DropDownMenu(
                itemsList = difficults,
                isExposed = isDifficultExposed,
                selectedItem = difficult,
                hint = stringResource(id = R.string.settings_hint_select_difficulty) 
            )
            DropDownMenu(
                itemsList = displayGenders,
                isExposed = isExposedGender,
                selectedItem = userGender,
                hint = stringResource(id = R.string.settings_hint_select_gender_optional) 
            )

            GameTextField(
                text = userAge,
                onValueChange = { userAge = it },
                labelTxt = stringResource(id = R.string.settings_label_age)
            )

            ExposedDropdownMenuBox(
                expanded = languageDropdownExpanded,
                onExpandedChange = { languageDropdownExpanded = !languageDropdownExpanded },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                OutlinedTextField(
                    value = currentLanguageDisplayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(id = R.string.settings_hint_select_language)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = languageDropdownExpanded,
                    onDismissRequest = { languageDropdownExpanded = false }
                ) {
                    val languagePairs = languageDisplayNames.mapIndexedNotNull { index, name ->
                        languageCodesArr.getOrNull(index)?.let { code -> name to code }
                    }.sortedBy { it.first }

                    languagePairs.forEach { (displayName, languageCode) ->
                        DropdownMenuItem(
                            text = { Text(displayName) },
                            onClick = {
                                if (currentSelectedLanguageCode.value != languageCode) {
                                    // LocaleHelper.setLocale both persists (via
                                    // LanguagePreferenceHelper) and applies the locale --
                                    // matches the destination shell's own convention of a
                                    // module owning its locale utils rather than depending on
                                    // a shared LocaleManager that doesn't exist here.
                                    LocaleHelper.setLocale(context.applicationContext, languageCode)
                                    currentSelectedLanguageCode.value = languageCode
                                    currentLanguageDisplayName =
                                        Locale.forLanguageTag(languageCode).getDisplayName(Locale.getDefault())
                                    (context as? MainActivity)?.recreate()
                                }
                                languageDropdownExpanded = false
                            }
                        )
                    }
                }
            }


            GameButton(
                onClick = {
                    // Your existing logic for saving preferences
                    val editor = sharedPreferences.edit() // Get editor here
                    editor.putBoolean(PrefKeys.IS_FIRST, false)
                    editor.putString(PrefKeys.USER_SUBJECT, userSubject).apply()
                    editor.putString(PrefKeys.USER_AGE, userAge).apply()
                    editor.putString(PrefKeys.USER_GENDER, userGender.value).apply()
                    val selectedQuestionTypeKey = questionTypeKeys.getOrElse(
                        questionTypes.indexOf(userQuestionType.value)
                    ) { "behavioral" }
                    editor.putString(PrefKeys.USER_QUESTION_TYPE, selectedQuestionTypeKey).apply()
                    editor.putString(PrefKeys.USER_SUBTYPE, subtype.value).apply()
                    editor.putString(PrefKeys.USER_DIFFICULTY, difficult.value).apply()
                    // AIViewModel reads "userLanguage" from this same
                    // SharedPreferences file to build Gemini's "Write in
                    // $language" instruction -- selecting a language above only
                    // ever called LocaleHelper.setLocale (a separate store, for
                    // the app's own UI locale), so this key was never written
                    // and every game generated in English regardless of what
                    // was picked. Look up the canonical English name by code
                    // (not Locale.getDisplayName(), which renders in whatever
                    // locale is currently active and would confuse the prompt).
                    val selectedLanguageName = languageDisplayNames.getOrNull(
                        languageCodesArr.indexOf(currentSelectedLanguageCode.value)
                    ) ?: "English"
                    editor.putString("userLanguage", selectedLanguageName)
                    editor.apply()

                    Timber.i(
                        "Settings: Generate Cases clicked -- subject=$userSubject, age=$userAge, " +
                        "gender=${userGender.value}, questionType=$selectedQuestionTypeKey, " +
                        "subtype=${subtype.value}, difficulty=${difficult.value}, " +
                        "language=$selectedLanguageName (code=${currentSelectedLanguageCode.value})"
                    )

                    viewmodel.startNewGame()

                    navController.navigate(Screens.GameScreen.screen) {
                        popUpTo(0) { inclusive = true } // Added inclusive as it's common
                    }
                },
                text = stringResource(id = R.string.settings_button_generate_cases),
                enabled = isFormValid
            )
        }
    }
}
