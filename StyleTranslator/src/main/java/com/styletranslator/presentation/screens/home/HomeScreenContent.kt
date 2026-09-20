package com.styletranslator.presentation.screens.home

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
import com.example.shared.domain.prompt.options.Category
import com.styletranslator.R.string
import com.example.shared.domain.prompt.options.GenderOption
import com.example.shared.domain.prompt.options.Mentality
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.domain.prompt.options.Style
import com.example.shared.domain.prompt.options.TonePreference
import com.example.shared.domain.prompt.options.TransformationLevel
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
    onNavigateToResultScreen: (List<Uri>, String, String, Int, Boolean, String?, String?, Int?, Int?, String?, String?, String?, String?, Float?) -> Unit
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
        stringResource(string.additional_info_TextField_placeholder_text_style_translator)
    val label = stringResource(string.sentence)
    val secretShowAd = viewModel.secretShowAd.collectAsState()

    val ageOptions = (5 .. 100 step 5).toList()
    val scaleOptions = listOf(0.1f, 0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 5f, 10f)

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

                    val styleInstruction = when (parameterScreenProperties.style) {
                        Style.POETIC -> "Craft verse that prioritizes rhyme above all else. Every line should contribute to a consistent rhyming scheme—be it couplets, alternate rhyme, or another structured form. Use poetic devices like alliteration, assonance, and metaphor to enrich the rhythm and imagery, but always maintain lyrical flow through strong, audible rhymes. Emphasize musicality, emotional depth, and artistic elegance through meticulously rhymed composition."
                        Style.PROFESSIONAL -> "Clear and structured communication, typically used in corporate or professional settings."
                        Style.LEGAL -> "Formal and precise language used in legal documents and contracts."
                        Style.TECHNICAL -> "Concise and straightforward language used in manuals and technical documentation."
                        Style.ACADEMIC -> "Structured and formal style used in scholarly articles and research papers."
                        Style.JOURNALISTIC -> "Concise, factual, and objective writing style used in news and media."
                        Style.CREATIVE -> "Imaginative and artistic writing style often found in fiction and storytelling."
                        Style.PERSUASIVE -> "Convincing style aimed at influencing opinions or prompting actions."
                        Style.NARRATIVE -> "Storytelling style that includes characters, plot, and narrative flow."
                        Style.DESCRIPTIVE -> "Vivid and detailed language that paints a picture for the reader."
                        Style.EXPOSITORY -> "Informative and structured style used to explain or clarify topics."
                        Style.HUMOROUS -> "Light-hearted, witty, and entertaining writing style meant to amuse readers."
                        Style.INFORMAL -> "Casual and conversational tone often used in personal communication."
                        Style.BUSINESS -> "Professional and concise communication style used in business environments."
                        Style.SCIENTIFIC -> "Objective and evidence-based writing used in scientific research."
                        Style.ROMANTIC -> "Evocative and emotional style focusing on love and relationships."
                        Style.HISTORICAL -> "Writing that reflects historical context, language, and style of a specific era."
                        Style.PHILOSOPHICAL -> "Analytical and reflective writing that explores fundamental philosophical ideas."
                        Style.SELF_HELP -> "Encouraging and motivational writing aimed at personal development."
                        Style.TRAVEL -> "Descriptive and experiential style that shares travel experiences and insights."
                        Style.MARKETING -> "Persuasive and engaging writing designed to promote products or services."
                        null -> null
                    }

                    val mentalityInstruction = when (parameterScreenProperties.mentality) {
                        Mentality.RUSSIAN -> "Collectivist, emphasis on spirituality, emotional expression."
                        Mentality.EAST_EUROPEAN -> "Pragmatic, resilient, often influenced by historical transitions."
                        Mentality.WEST_EUROPEAN -> "Individualistic, rational, emphasis on efficiency and rules."
                        Mentality.SOUTHEAST_ASIAN -> "Harmony-focused, respectful of elders, community-oriented."
                        Mentality.CHINESE -> "Confucian values, hierarchical, importance of education and saving face."
                        Mentality.CAUCASIAN -> "Honor-bound, family-oriented, strong sense of tradition."
                        Mentality.NORTH_AMERICAN -> "Individualistic, entrepreneurial, direct communication."
                        Mentality.LATIN_AMERICAN -> "Collectivist, emphasis on relationships, passionate and expressive."
                        Mentality.MIDDLE_EASTERN -> "Hospitality-driven, religious influence, strong family ties."
                        Mentality.AFRICAN -> "Communal, oral tradition, emphasis on spirituality and ancestry."
                        Mentality.NORDIC -> "Egalitarian, reserved, emphasis on nature and social welfare."
                        Mentality.SOUTH_ASIAN -> "Hierarchical (caste), spiritual, family-centric."
                        Mentality.JAPANESE -> "Group harmony, politeness, respect for tradition and seniority."
                        Mentality.AUSTRALIAN -> "Laid-back, egalitarian, independent."
                        Mentality.MEDITERRANEAN -> "Family-oriented, expressive, value personal connections."
                        Mentality.CENTRAL_ASIAN -> "Nomadic traditions, hospitality, respect for elders."
                        Mentality.GERMANIC -> "Structured, efficient, detail-oriented."
                        Mentality.EASTERN_EUROPEAN_BALKAN -> "Strong sense of identity, resilience, hospitality."
                        Mentality.ARCTIC_INDIGENOUS -> "Connection to nature, communal living, respect for tradition."
                        Mentality.JEWISH -> "Emphasis on learning, community, tradition, and social justice."
                        null -> null
                    }

                    val toneInstruction = when (parameterScreenProperties.tonePreference) {
                        TonePreference.FORMAL -> "Professional and respectful tone, suitable for formal communication."
                        TonePreference.CASUAL -> "Relaxed and conversational tone, used in informal settings."
                        TonePreference.FRIENDLY -> "Warm and engaging tone, making the text feel approachable."
                        TonePreference.SERIOUS -> "Serious and direct tone, focusing on facts and clarity."
                        TonePreference.EMPATHETIC -> "Supportive and understanding tone, acknowledging emotions."
                        TonePreference.ASSERTIVE -> "Confident and decisive tone, conveying authority and clarity."
                        TonePreference.OPTIMISTIC -> "Positive and uplifting tone, inspiring motivation."
                        TonePreference.HUMOROUS -> "Light-hearted and fun tone, aiming to entertain."
                        TonePreference.NEUTRAL -> "Neutral and objective tone, avoiding strong emotional cues."
                        null -> null
                    }

                    val transformationLevelInstruction =
                        when (parameterScreenProperties.transformationLevel) {
                            TransformationLevel.MINIMAL -> "**Make minor grammatical and syntactical adjustments** to ensure the translation is fluent and natural. Do **not** significantly alter sentence structure or meaning. Adjust **gendered pronouns and grammatical structures** appropriately but **do not change the overall narrative perspective**. The translation should remain **as close as possible to the original text**, making only necessary linguistic refinements."

                            TransformationLevel.MODERATE -> "**Reframe the text naturally to align with the provided input-parameters' gender, age, and cultural context.** Adjust **pronouns, possessives, and relationship roles** as needed. The translation should **adapt tone and sentence structure** slightly while keeping the core meaning intact."

                            TransformationLevel.COMPLETE -> "Make sure **the perspective is fully shifted** based on the provided input-parameters' gender, age, and other input parameters. The transformation **must go beyond pronoun and grammar changes**. If necessary, **adjust cultural and contextual nuances** to match the specified *mentality, category, and style*."
                        }

                    val categoryInstruction = when (parameterScreenProperties.category) {
                        Category.RELATIONSHIP -> "Texts related to love, dating, friendships, and emotional connections."
                        Category.WORKPLACE -> "Professional communication, office culture, and workplace scenarios."
                        Category.EDUCATION -> "Academic writing, student interactions, and educational discussions."
                        Category.HEALTH -> "Topics related to physical and mental health, wellness, and self-care."
                        Category.FINANCE -> "Financial advice, investment strategies, and economic discussions."
                        Category.TRAVEL -> "Travel experiences, tourism recommendations, and location-based insights."
                        Category.TECHNOLOGY -> "Technology-related content, including software, hardware, and IT discussions."
                        Category.ENTERTAINMENT -> "Movies, music, books, and pop culture topics."
                        Category.SELF_IMPROVEMENT -> "Personal development, motivation, and self-help strategies."
                        Category.GENERAL -> "General-purpose text that does not fit into a specific category."
                        null -> null
                    }
                    onNavigateToResultScreen(
                        selectedImageUris.value,
                        userTextTask.value,
                        transformationLevelInstruction,
                        viewModel.getSelectedLanguage(),
                        secretShowAd.value,
                        parameterScreenProperties.sourceGender?.gender,
                        parameterScreenProperties.targetGender?.gender,
                        parameterScreenProperties.sourceAge,
                        parameterScreenProperties.targetAge,
                        categoryInstruction,
                        styleInstruction,
                        mentalityInstruction,
                        toneInstruction,
                        parameterScreenProperties.translationScale
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
                            label = string.transformation_level_label,
                            selectedOption = parameterScreenProperties.transformationLevel,
                            options = TransformationLevel.entries.toList(),
                            onOptionSelected = {
                                if (it != null) viewModel.updateSelectedTransformationLevelOption(it)
                            },
                            optionToString = { option, context -> option.getString(context) },
                            valueRequired = true
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.source_gender_label,
                            selectedOption = parameterScreenProperties.sourceGender,
                            options = GenderOption.entries.toList(),
                            onOptionSelected = viewModel::updateSelectedSourceGenderOption,
                            optionToString = { option, context -> option.getString(context) }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.source_age_label,
                            selectedOption = parameterScreenProperties.sourceAge,
                            options = ageOptions,
                            onOptionSelected = viewModel::updateSelectedSourceAgeOption,
                            optionToString = { option, _ -> option.toString() }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.target_gender_label,
                            selectedOption = parameterScreenProperties.targetGender,
                            options = GenderOption.entries.toList(),
                            onOptionSelected = viewModel::updateSelectedTargetGenderOption,
                            optionToString = { option, context -> option.getString(context) }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.target_age_label,
                            selectedOption = parameterScreenProperties.targetAge,
                            options = ageOptions,
                            onOptionSelected = viewModel::updateSelectedTargetAgeOption,
                            optionToString = { option, _ -> option.toString() }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.category_label,
                            selectedOption = parameterScreenProperties.category,
                            options = Category.entries.toList(),
                            onOptionSelected = viewModel::updateSelectedCategoryOption,
                            optionToString = { option, context -> option.getString(context) }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.tone_label,
                            selectedOption = parameterScreenProperties.tonePreference,
                            options = TonePreference.entries.toList(),
                            onOptionSelected = viewModel::updateSelectedToneOption,
                            optionToString = { option, context -> option.getString(context) }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.style_label,
                            selectedOption = parameterScreenProperties.style,
                            options = Style.entries.toList(),
                            onOptionSelected = viewModel::updateSelectedStyleOption,
                            optionToString = { option, context -> option.getString(context) }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.mentality_label,
                            selectedOption = parameterScreenProperties.mentality,
                            options = Mentality.entries.toList(),
                            onOptionSelected = viewModel::updateSelectedMentalityOption,
                            optionToString = { option, context -> option.getString(context) }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = string.translation_scale_label,
                            selectedOption = parameterScreenProperties.translationScale,
                            options = scaleOptions,
                            onOptionSelected = viewModel::updateSelectedTranslationScaleOption,
                            optionToString = { option, _ -> "×${option}" }
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