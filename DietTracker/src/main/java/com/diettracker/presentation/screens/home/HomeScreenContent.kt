package com.diettracker.presentation.screens.home

import com.example.shared.presentation.ExposedDropBox
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diettracker.R.string
import com.example.shared.domain.prompt.options.GenderOption
import com.example.shared.domain.prompt.options.PhysicalActivityOption
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.presentation.common.ApplicationScaffold
import com.example.shared.presentation.screens.home.SettingsActionRow
import com.example.shared.presentation.screens.home.UploadFileMethodOptions
import com.example.shared.presentation.screens.home.homeInputSection
import com.example.shared.presentation.screens.home.imageGridItems
import com.example.shared.presentation.screens.home.onNextClick
import java.util.Locale


@Composable
fun HomeScreenContent(
    viewModel: HomeViewModel,
    onNavigateToResultScreen: (List<Uri>, String, Int, Boolean, String?, String?, Int?, Int?, Int?) -> Unit
) {
    //val viewModel: HomeViewModel = hiltViewModel()
    val parameterScreenProperties =
        viewModel.parametersPropertiesState.collectAsStateWithLifecycle().value
    val allImageUris = viewModel.allImageUris.collectAsState()
    val selectedImageUris = viewModel.selectedUris.collectAsState()

    val context = LocalContext.current
    val lazyColumnState = rememberLazyListState()

    val selectImageOrTextWarningMessage = stringResource(string.select_image_or_text_warning)
    val corruptedUploadedFile = stringResource(string.corrupted_loaded_file)
    stringResource(string.missing_required_settings)
    val userTextTask = viewModel.userTextTask.collectAsState()
    val placeHolderUserTaskText =
        stringResource(string.additional_info_TextField_placeholder_text_calories)
    val label = stringResource(string.foods)
    val secretShowAd = viewModel.secretShowAd.collectAsState()

    val ageOptions = (5 .. 100 step 5).toList()
    val heightOptions = (120..220 step 5).toList()
    val weightOptions = (20..140 step 5).toList()


    @Composable
    fun SolveButton() {
        Button(
            onClick = {
                viewModel.onSolve()
                onNextClick(
                    userTextTask.value,
                    selectedImageUris.value,
                    context,
                    viewModel,
                    corruptedUploadedFile = corruptedUploadedFile,
                    selectImageOrTextWarningMessage = selectImageOrTextWarningMessage
                ) {
                    onNavigateToResultScreen(
                        selectedImageUris.value,
                        userTextTask.value,
                        viewModel.getSelectedLanguage(),
                        secretShowAd.value,
                        parameterScreenProperties.physicalActivity?.activityLevel,
                        parameterScreenProperties.gender?.gender,
                        parameterScreenProperties.age,
                        parameterScreenProperties.height,
                        parameterScreenProperties.weight
                    )
                }
            },
            modifier = Modifier.wrapContentWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            enabled = true
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Sending"
            )
        }
    }

    ApplicationScaffold(
        isShowed = true,
        content = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                state = lazyColumnState
            ) {
                homeInputSection(
                    userText = userTextTask.value,
                    onTextChange = viewModel::updateUserTextTask,
                    placeholderText = placeHolderUserTaskText,
                    label = label,
                    onCameraClick = {
                        viewModel.updateSelectedUploadMethodOption(
                            UploadFileMethodOptions.TAKE_A_PICTURE
                        )
                    },
                    onUploadClick = {
                        viewModel.updateSelectedUploadMethodOption(
                            UploadFileMethodOptions.UPLOAD_AN_IMAGE
                        )
                    },
                    onEraseClick = {
                        viewModel.updateUserTextTask("")
                    }
                ) { SolveButton() }

                imageGridItems(
                    allUris = allImageUris.value,
                    selectedUris = selectedImageUris.value,
                    viewModel = viewModel,
                    solveButton = { SolveButton() }
                )

                item {
                    Text(
                        text = stringResource(string.settings),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        ExposedDropBox(
                            maxHeightIn = 400.dp,
                            label = string.solution_language_label,
                            selectedOption = parameterScreenProperties.language,
                            options = SolutionLanguageOption.entries.toList(),
                            onOptionSelected = {
                                if (it != null) {
                                    val locale = Locale.forLanguageTag(it.languageTag)
                                    val isLanguageSupported = viewModel.isLanguageSupported(locale)
                                    if (!isLanguageSupported) {
                                        AlertDialog.Builder(context)
                                            .setTitle(string.install_language)
                                            .setMessage(string.language_not_supported)
                                            .setPositiveButton(string.install) { _, _ ->
                                                val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            }
                                            .setNegativeButton(com.example.shared.R.string.cancel, null)
                                            .show()
                                    }
                                    viewModel.updateSelectedLanguageOption(it)
                                }
                            },
                            optionToString = { option, context -> option.getString(context) },
                            valueRequired = true
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.gender_label,
                            selectedOption = parameterScreenProperties.gender,
                            options = GenderOption.entries.toList(),
                            onOptionSelected = viewModel::updateSelectedGenderOption,
                            optionToString = { option, context -> option.getString(context) }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.age_label,
                            selectedOption = parameterScreenProperties.age,
                            options = ageOptions,
                            onOptionSelected = viewModel::updateSelectedAgeOption,
                            optionToString = { option, _ -> option.toString() }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.physical_activity_label,
                            selectedOption = parameterScreenProperties.physicalActivity,
                            options = PhysicalActivityOption.entries.toList(),
                            onOptionSelected = viewModel::updateSelectedPhysicalActivityOption,
                            optionToString = { option, context -> option.getString(context) }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.height_label, // Add this string resource
                            selectedOption = parameterScreenProperties.height,
                            options = heightOptions,
                            onOptionSelected = viewModel::updateSelectedHeightOption,
                            optionToString = { option, _ -> "$option cm" }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.weight_label, // Add this string resource
                            selectedOption = parameterScreenProperties.weight,
                            options = weightOptions,
                            onOptionSelected = viewModel::updateSelectedWeightOption,
                            optionToString = { option, _ -> "$option kg" }
                        )

                    }

                    SettingsActionRow(
                        onResetClick = viewModel::resetSettings,
                        solveButton = { SolveButton() }
                    )
                }
            }
        }
    )

}