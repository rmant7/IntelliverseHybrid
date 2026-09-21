package com.matterofchoice.screens


import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.matterofchoice.BottomNav
import com.matterofchoice.GameState
import com.matterofchoice.R
import com.matterofchoice.Screens
import com.matterofchoice.common.GameButton
import com.matterofchoice.model.Case
import com.matterofchoice.model.Option
import com.matterofchoice.ui.theme.titleFont
import com.matterofchoice.viewmodel.AIViewModel
import kotlinx.coroutines.launch
import androidx.core.content.edit


@Composable
fun MainScreen(aiViewModel: AIViewModel = viewModel()) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNav(navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screens.SettingsScreen.screen,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screens.GameScreen.screen) { Game(navController, aiViewModel) }
            composable(Screens.ResultScreen.screen) { Result() }
            composable(Screens.AnalysisScreen.screen) { AnalysisScreen(navController = navController, viewModel = aiViewModel) }
            composable(Screens.SettingsScreen.screen) {
                Settings(navController = navController, viewmodel = aiViewModel)
            }
        }
    }
}


@Composable
fun Game(navController: NavHostController, viewmodel: AIViewModel) {
    SetUpCase(navController = navController, state = viewmodel.state.value, viewModel = viewmodel)
}

@Composable
fun Loader() {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.my_animate))
    val progress by animateLottieCompositionAsState(
        isPlaying = true,
        composition = composition,
        restartOnPlay = true,
        iterations = LottieConstants.IterateForever
    )
    LottieAnimation(
        modifier = Modifier.size(400.dp),
        composition = composition,
        progress = { progress },
    )
}


@Composable
fun SetUpCase(viewModel: AIViewModel, navController: NavHostController, state: GameState) {
    val context = LocalContext.current.applicationContext
    var currentCaseIndex by rememberSaveable { mutableIntStateOf(0) }
    var currentSelection by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // Track current selections

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.casesList, state.userChoices) {
        if (state.casesList.isNotEmpty()) {
            // How many of the currently-loaded cases have already been answered
            val answeredCases = state.casesList.count { currentCase ->
                state.userChoices.containsKey(currentCase.case_id)
            }

            currentCaseIndex = if (answeredCases < state.casesList.size) {
                // Not all loaded cases are answered yet -- show the next unanswered one
                answeredCases
            } else {
                // All loaded cases are answered; the buffer replenish (triggered by the
                // last answer) is fetching more in the background. cases.size here means
                // "waiting for more" -- see the currentCaseIndex >= cases.size branch below.
                state.casesList.size
            }
            currentSelection = emptyMap() // Reset local UI selection for the new/current case
            Log.d("SetUpCase", "currentCaseIndex updated to: $currentCaseIndex based on $answeredCases answered of ${state.casesList.size} loaded")
        } else {
            currentCaseIndex = 0 // No cases loaded yet
            currentSelection = emptyMap()
        }
    }

    // Trigger case generation when cases list is empty but we're not loading and no error
    LaunchedEffect(state.casesList.isEmpty(), state.isLoading, state.error) {
        if (state.casesList.isEmpty() && !state.isLoading && state.error == null) {
            Log.d("SetUpCase", "Cases list is empty. Triggering case generation.")
            viewModel.initiateGame()
        }
    }


    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Loader()
        }
    } else if (state.error != null) {
        // Handle error state
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Log.e("SetUpCase", "An error occurred: ${state.error}")
            Text(
                text = state.error ?: stringResource(R.string.error_something_went_wrong),
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 20.dp)
            )
            GameButton(
                onClick = {
                    viewModel.resetGame()
                    navController.navigate(Screens.SettingsScreen.screen)
                },
                text = stringResource(R.string.button_new_game)
            )
        }
    }  else if (state.casesList.isNotEmpty()) {
        // Handle the success state where we have cases
        val scrollState = rememberScrollState()
        val cases = state.casesList
        val sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        val currentCaseToDisplayIndex = if (currentCaseIndex >= cases.size && cases.isNotEmpty()) cases.size - 1 else currentCaseIndex
        val currentCase = if (currentCaseToDisplayIndex < cases.size) cases[currentCaseToDisplayIndex] else null
        val caseId = currentCase?.case_id ?: "unknown_$currentCaseToDisplayIndex"

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Header UI
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    // No fixed total is shown -- the case buffer is generated
                    // continuously, so the player is never told how many
                    // questions are left in the session. R.string.rounds ("Rounds")
                    // described the old fixed-turn structure and no longer fits.
                    text = "Case ${if (currentCaseIndex >= cases.size) cases.size else currentCaseIndex + 1}",
                    fontFamily = titleFont,
                    textAlign = TextAlign.Justify,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(3.dp)
                )
            }

            // Main content column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Scenario",
                        fontFamily = titleFont,
                        fontSize = 28.sp,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold
                    )
                    Image(
                        painter = painterResource(R.drawable.fire),
                        modifier = Modifier.size(28.dp),
                        contentDescription = null
                    )
                    val userScore = sharedPreferences.getInt("userScore", 0)
                    val totalScore = sharedPreferences.getInt("totalScore", 0)
                    Text(
                        text = "$userScore / $totalScore",
                        fontFamily = titleFont,
                        fontSize = 18.sp,
                    )
                }

                if (currentCaseIndex < cases.size && currentCase != null) {
                    Text(
                        text = currentCase.case ?: "No question available",
                        fontFamily = titleFont,
                        textAlign = TextAlign.Justify,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
                    )

                    // Display options
                    currentCase.options?.forEachIndexed { index, option ->
                        val choiceLetter = ('A' + index).toString()
                        val isSelected = currentSelection[caseId] == choiceLetter

                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
                            onClick = {
                                // Update current selection immediately when user clicks
                                currentSelection = currentSelection + (caseId to choiceLetter)
                                coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                            },
                            border = BorderStroke(
                                width = 2.dp,
                                color = if (isSelected) Color.Green else Color.LightGray
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 5.dp),
                                    tint = Color.Green
                                )
                            }
                            Text(
                                text = "$choiceLetter. ${option.option}",
                                color = colorScheme.onSurface,
                                fontFamily = titleFont,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                } else if (currentCaseIndex >= cases.size) {
                    // All currently-loaded cases are answered. The buffer replenish
                    // (triggered by the last answer, see AIViewModel.maybeReplenishCases)
                    // is fetching more in the background -- this is normally brief.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (state.isFetchingMore) {
                            Loader()
                            Text(
                                text = "Loading next case...",
                                fontFamily = titleFont,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Text(
                                text = "Couldn't load more cases.",
                                fontFamily = titleFont,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }

                // Button section - different buttons based on context
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Check if user has selected an option for the current case
                    val hasSelectionForCurrentCase = currentSelection.containsKey(caseId)

                    if (hasSelectionForCurrentCase) {
                        // User picked an option for this case -- confirm it and move on,
                        // or jump to analysis with everything answered so far. There's no
                        // fixed end: the buffer keeps refilling in the background.
                        GameButton(
                            onClick = {
                                currentSelection[caseId]?.let { selectedChoice ->
                                    viewModel.onUserChoice(caseId, selectedChoice)
                                    if (currentCase != null) {
                                        calculateScore(currentCase, selectedChoice, context)
                                    }
                                }
                                currentSelection = emptyMap() // Reset selection for next case
                                coroutineScope.launch { scrollState.animateScrollTo(0) }
                            },
                            text = "Next Case",
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GameButton(
                            onClick = {
                                if (!state.userChoices.containsKey(caseId) && currentSelection.containsKey(caseId)) {
                                    currentSelection[caseId]?.let { selectedChoice ->
                                        viewModel.onUserChoice(caseId, selectedChoice)
                                        if (currentCase != null) {
                                            calculateScore(currentCase, selectedChoice, context)
                                        }
                                    }
                                }
                                navController.navigate(Screens.AnalysisScreen.screen)
                            },
                            text = "Analyze",
                        )
                    } else if (currentCaseIndex >= cases.size) {
                        // Waiting for the buffer, or it failed -- let the player bail out
                        // to analysis on what they've already answered, or start over.
                        GameButton(
                            onClick = { navController.navigate(Screens.AnalysisScreen.screen) },
                            text = "Analyze",
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GameButton(
                            onClick = {
                                viewModel.resetGame()
                                navController.navigate(Screens.SettingsScreen.screen)
                            },
                            text = "New Game",
                        )
                    } else {
                        // No selection yet - show "New Game" button only
                        GameButton(
                            onClick = {
                                viewModel.resetGame()
                                navController.navigate(Screens.SettingsScreen.screen)
                            },
                            text = "New Game",

                            )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    } else {
        // This is the initial state before the user has played a game or if list is empty
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GameButton(
                onClick = {
                    viewModel.initiateGame()
                },
                text = "Start Game"
            )
        }
    }
}

@Preview
@Composable
fun Test(){
    Column(horizontalAlignment = Alignment.CenterHorizontally) {


        GameButton(
                onClick = {
//                    if (selectedItem != null) {
//                        calculateScore(
//                            cases[caseNum - 1],
//                            selectedItem!!.option,
//                            context
//                        )
//                        viewmodel.onUserChoice(cases[caseNum - 1].case_id, selectedItem!!.option)
//
//                        round.intValue++
//                        sharedPreferences.edit { putInt("rounds", round.intValue) }
//                        caseNum++
//                        selectedItem = null
//                        coroutineScope.launch {
//                            scrollState.animateScrollTo(0)
//                        }
//                    }
                },
                text = stringResource(R.string.button_next)
            )

        GameButton(
            text = stringResource(R.string.analyze),
            onClick = {
//                if (selectedItem != null) {
//                    calculateScore(
//                        cases[caseNum - 1],
//                        selectedItem!!.option,
//                        context
//                    )
//                    viewmodel.onUserChoice(cases[caseNum - 1].case_id, selectedItem!!.option)
////                                    viewmodel.performAnalysis()
//                    navController.navigate(Screens.AnalysisScreen.screen)
//                }

            }
        )
    }
}



fun calculateScore(case: Case, selectedChoice: String, context: Context) {
    val sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    // Get the current total scores
    val currentTotalUserScore = sharedPreferences.getInt("userScore", 0)
    val currentTotalOptimalScore = sharedPreferences.getInt("totalScore", 0)

    // Find the selected option
    val selectedOptionNumber = selectedChoice[0] - 'A' + 1 // Convert letter to number
    val selectedOption = case.options?.find { it.number == selectedOptionNumber }

    // Find the optimal option (optimal field contains the option number as string)
    val optimalOptionNumber = case.optimal?.toIntOrNull()
    val optimalOption = case.options?.find { it.number == optimalOptionNumber }

    var currentUserCaseScore = 0
    var currentOptimalCaseScore = 0

    // Calculate scores
    selectedOption?.let {
        currentUserCaseScore = it.knowledge + it.personalGrowth + it.timeManagement +
                (it.health ?: 0) + (it.wealth ?: 0) + (it.relationships ?: 0) +
                (it.happiness ?: 0) + (it.karma ?: 0) + (it.environmentalImpact ?: 0) +
                (it.socialResponsibility ?: 0)
    }

    optimalOption?.let {
        currentOptimalCaseScore = it.knowledge + it.personalGrowth + it.timeManagement +
                (it.health ?: 0) + (it.wealth ?: 0) + (it.relationships ?: 0) +
                (it.happiness ?: 0) + (it.karma ?: 0) + (it.environmentalImpact ?: 0) +
                (it.socialResponsibility ?: 0)
    }

    // Add current case scores to the totals and save
    sharedPreferences.edit {
        putInt("userScore", currentTotalUserScore + currentUserCaseScore)
        putInt("totalScore", currentTotalOptimalScore + currentOptimalCaseScore)
        apply()
    }

    Log.d(
        "ScoreUpdate",
        "User Case Score: $currentUserCaseScore, New Total User Score: ${currentTotalUserScore + currentUserCaseScore}"
    )
    Log.d(
        "ScoreUpdate",
        "Optimal Case Score: $currentOptimalCaseScore, New Total Optimal Score: ${currentTotalOptimalScore + currentOptimalCaseScore}"
    )
}
