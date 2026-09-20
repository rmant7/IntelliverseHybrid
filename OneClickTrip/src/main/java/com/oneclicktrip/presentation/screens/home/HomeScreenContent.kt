package com.oneclicktrip.presentation.screens.home

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneclicktrip.R.string
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.domain.prompt.options.TransportationType
import com.example.shared.domain.prompt.options.TripStyle
import com.example.shared.presentation.MultiSelectExposedDropBox
import com.example.shared.presentation.common.ApplicationScaffold
import com.example.shared.presentation.common.StaticLabelTextField
import com.example.shared.presentation.screens.home.SettingsActionRow
import com.example.shared.presentation.screens.home.UploadFileMethodOptions
import com.example.shared.presentation.screens.home.checkImageValidity
import com.example.shared.presentation.screens.home.homeInputSection
import com.example.shared.presentation.screens.home.imageGridItems
import java.util.Locale


@Composable
fun HomeScreenContent(
    viewModel: HomeViewModel,
    onNavigateToResultScreen: (List<Uri>, String, Int, String, List<String>, List<String>, List<String>, Boolean, Boolean, Int?, Int?, Int?) -> Unit
) {
    //val viewModel: HomeViewModel = hiltViewModel()
    val parameterScreenProperties =
        viewModel.parametersPropertiesState.collectAsStateWithLifecycle().value
    val allImageUris = viewModel.allImageUris.collectAsState()
    val selectedImageUris = viewModel.selectedUris.collectAsState()

    val tripPath = parameterScreenProperties.tripPath
    val transportationTypes = parameterScreenProperties.transportationTypes
    val tripStyles = parameterScreenProperties.tripStyles

    val context = LocalContext.current
    val lazyColumnState = rememberLazyListState()

    val selectImageOrTextWarningMessage = stringResource(string.select_image_or_text_warning)
    val corruptedUploadedFile = stringResource(string.corrupted_loaded_file)
    stringResource(string.missing_required_settings)
    val userTextTask = viewModel.userTextTask.collectAsState()
    val placeHolderUserTaskText =
        stringResource(string.additional_info_TextField_placeholder_text_oneclicktrip)
    val label = stringResource(string.sentence)
    val days = stringResource(string.days)
    val newYork = stringResource(string.new_york)
    val secretShowAd = viewModel.secretShowAd.collectAsState()
    val oneWay = stringResource(string.one_way_trip)
    val roundWay = stringResource(string.round_trip)

    val travelersNumberOptions = (1..20).toList()
    val oneWayOptions = listOf(false, true)

    @Composable
    fun SolveButton() {

        Button(
            onClick = {
                viewModel.onSolve()
                checkImageValidity(
                    selectedImageUris.value,
                    context,
                    viewModel,
                    corruptedUploadedFile,
                    selectImageOrTextWarningMessage,
                    expectImages = false
                ) {
                    onNavigateToResultScreen(
                        selectedImageUris.value,
                        userTextTask.value,
                        viewModel.getSelectedLanguage(),
                        parameterScreenProperties.originLocation,
                        tripPath,
                        transportationTypes.map { it.type },
                        tripStyles.map { it.style },
                        parameterScreenProperties.isOneWay,
                        secretShowAd.value,
                        parameterScreenProperties.maxBudget,
                        parameterScreenProperties.tripDuration,
                        parameterScreenProperties.travelersNumber
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

                        OutlinedTextField(
                            value = parameterScreenProperties.originLocation,
                            onValueChange = {
                                viewModel.updateSelectedOriginLocationOption(it)
                            },
                            label = { Text(stringResource(id = string.origin_location_label)) },
                            placeholder = { Text(newYork) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        CityPathEditor(
                            selectedCities = tripPath,
                            onAddCity = { viewModel.updateTripPath(tripPath + it) },
                            onRemoveCity = { viewModel.updateTripPath(tripPath - it) },
                            onMoveCity = { fromIndex, toIndex ->
                                if (fromIndex !in parameterScreenProperties.tripPath.indices) return@CityPathEditor

                                val newList = parameterScreenProperties.tripPath.toMutableList()
                                val city = newList.removeAt(fromIndex)

                                val safeToIndex = toIndex.coerceIn(0, newList.size)
                                newList.add(safeToIndex, city)
                                viewModel.updateTripPath(newList)
                            }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.trip_type_label,
                            selectedOption = parameterScreenProperties.isOneWay,
                            options = oneWayOptions,
                            onOptionSelected = {
                                if (it != null) {
                                    viewModel.updateOneWayOption(it)
                                }
                            },
                            optionToString = { option, _ -> if (option) oneWay else roundWay },
                            valueRequired = true
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.travelers_number_label,
                            selectedOption = parameterScreenProperties.travelersNumber,
                            options = travelersNumberOptions,
                            onOptionSelected = viewModel::updateSelectedTravelersNumberOption,
                            optionToString = { option, _ -> option.toString() }
                        )

                        StaticLabelTextField(
                            value = parameterScreenProperties.tripDuration?.toString() ?: "",
                            onValueChange = {
                                viewModel.updateSelectedTripDurationOption(it.toIntOrNull())
                            },
                            label = stringResource(id = string.trip_duration_label),
                            placeholderText = "3" ,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = { Text(days) },
                            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                        )

                        MultiSelectExposedDropBox(
                            label = string.trip_style_label,
                            selectedOptions = tripStyles,
                            options = TripStyle.entries.toList(),
                            onOptionsChanged = viewModel::updateTripStyles,
                            optionToString = { option, context -> option.getString(context) }
                        )

                        StaticLabelTextField(
                            value = parameterScreenProperties.maxBudget?.toString() ?: "",
                            onValueChange = {
                                viewModel.updateSelectedMaxBudgetOption(it.toIntOrNull())// change it to be string always
                            },
                            label = stringResource(id = string.max_budget_label),
                            placeholderText = "5000" ,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Text("$") },
                            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                        )

                        MultiSelectExposedDropBox(
                            label = string.transportation_type_label,
                            selectedOptions = transportationTypes,
                            options = TransportationType.entries.toList(),
                            onOptionsChanged = viewModel::updateTransportationTypes,
                            optionToString = { option, context -> option.getString(context) }
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