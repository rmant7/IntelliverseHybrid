package com.oneclicktrip.presentation.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.oneclicktrip.R
import com.example.shared.presentation.common.ApplicationScaffold
import com.oneclicktrip.presentation.screens.home.HomeScreen
import com.oneclicktrip.presentation.screens.output.ocr.OcrScreenContent
import com.oneclicktrip.presentation.screens.output.result.ResultScreen
import com.example.shared.presentation.AppTopBar
import com.example.shared.presentation.navigation.BottomNavigationBar
import com.example.shared.presentation.navigation.updateOrInsert
import com.example.shared.presentation.screens.output.SharedViewModel


@Composable
fun Navigation(parentNavController: NavHostController?) {
    val navController = rememberNavController()

    // for each type of screen, keeps the last version of that screen that was visited
    val lastScreensVersions = rememberSaveable(saver = screenListSaver) { mutableStateListOf() }
    //val lastScreensVersions = sharedNavViewModel.lastScreensVersions

    ApplicationScaffold(
        isShowed = true,
        content = {
            NavigationController(
                navController = navController,
                lastScreensVersions
            )
        },
        bottomBar = {
            BottomNavigationBar(navController, lastScreensVersions)
        },
        topBar = {
            AppTopBar(parentNavController, stringResource(R.string.app_name_oneclicktrip), stringResource(R.string.oneclicktrip_info))
        }
    )

}

@Composable
private fun NavigationController(
    navController: NavHostController,
    lastScreensVersions: MutableList<Screen>
) {
    val sharedViewModel: SharedViewModel = hiltViewModel()
    NavHost(
        navController = navController,
        startDestination =  /*sharedNavViewModel.lastMathRoute ?:*/  Screen.Home.createRoute(),
        // Default fadeIn() and fadeOut() effects might slow down transition
        // animation a little bit, but release apk is still much faster than debug one.
        enterTransition = {
            EnterTransition.None
        },
        exitTransition = {
            ExitTransition.None
        }
    ) {

        composable(Screen.Home.createRoute()) {

            HomeScreen(
                onNavigateToResultScreen = { uriList, userTask, selectedLanguageIndex, originLocation, cityPaths, transportationTypes, tripStyles, oneWay, secretShowAd, maxBudget, tripDuration, travelersNumber ->
                    // removes the option to navigate to the ocr screen, as it becomes invalid
                    // when new results are computed
                    val ocrScreen = lastScreensVersions.find { it is Screen.Ocr }
                    ocrScreen?.let { lastScreensVersions.remove(it) }
                    sharedViewModel.reset()
                    val nextScreen = Screen.Result(
                        uriList.map { it.toString() },
                        passedEditedResult = "",
                        userTask,
                        selectedLanguageIndex,
                        originLocation,
                        cityPaths,
                        transportationTypes,
                        tripStyles,
                        oneWay,
                        secretShowAd,
                        maxBudget,
                        tripDuration,
                        travelersNumber
                    )
                    val route = nextScreen.createRoute()
                    navController.navigate(route) {
                        popUpTo(Screen.Home.createRoute()) { saveState = true } // here we use inclusive = false
                    }
                }
            )
            lastScreensVersions.updateOrInsert(Screen.Home)
        }

        composable(
            route = "${Screen.Result.prefixRoute}/{passedImageUris}/{passedEditedResult}/{userTask}/{selectedLanguageIndex}/{originLocation}/{cityPaths}/{transportationTypes}/{tripStyles}/{oneWay}/{secretShowAd}" +
                    "?maxBudget={maxBudget}" +
                    "&tripDuration={tripDuration}" +
                    "&travelersNumber={travelersNumber}",
            arguments = listOf(
                navArgument("passedImageUris") { type = NavType.StringType },
                navArgument("passedEditedResult") { type = NavType.StringType },
                navArgument("userTask") { type = NavType.StringType },
                navArgument("selectedLanguageIndex") { type = NavType.IntType },
                navArgument("originLocation") { type = NavType.StringType },
                navArgument("cityPaths") { type = NavType.StringType },
                navArgument("transportationTypes") { type = NavType.StringType },
                navArgument("tripStyles") { type = NavType.StringType },
                navArgument("oneWay") { type = NavType.BoolType },
                navArgument("secretShowAd") { type = NavType.BoolType },

                navArgument("maxBudget") { type = NavType.StringType; nullable = true },
                navArgument("tripDuration") { type = NavType.StringType; nullable = true },
                navArgument("travelersNumber") { type = NavType.StringType; nullable = true },
            )
        ) { backStackEntry ->
            val encodedUris = backStackEntry.arguments?.getString("passedImageUris") ?: ""
            val passedImageUris = encodedUris
                .split(",")
                .mapNotNull { encoded -> Uri.decode(encoded).takeIf { it.isNotBlank() } }

            val encodedCityPaths = backStackEntry.arguments?.getString("cityPaths") ?: ""
            val cityPaths = encodedCityPaths
                .split(",")
                .mapNotNull { encoded -> Uri.decode(encoded).takeIf { it.isNotBlank() } }

            val encodedTransportationTypes = backStackEntry.arguments?.getString("transportationTypes") ?: ""
            val transportationTypes = encodedTransportationTypes
                .split(",")
                .mapNotNull { encoded -> Uri.decode(encoded).takeIf { it.isNotBlank() } }

            val encodedTripStyles = backStackEntry.arguments?.getString("tripStyles") ?: ""
            val tripStyles = encodedTripStyles
                .split(",")
                .mapNotNull { encoded -> Uri.decode(encoded).takeIf { it.isNotBlank() } }

            val passedEditedResult = backStackEntry.arguments?.getString("passedEditedResult") ?: ""
            val userTask = backStackEntry.arguments?.getString("userTask") ?: ""
            val selectedLanguageIndex = backStackEntry.arguments?.getInt("selectedLanguageIndex") ?: 0
            val originLocation = backStackEntry.arguments?.getString("originLocation") ?: ""
            val oneWay = backStackEntry.arguments?.getBoolean("oneWay") ?: false
            val secretShowAd = backStackEntry.arguments?.getBoolean("secretShowAd") ?: false

            // Nullable values
            val maxBudget = backStackEntry.arguments?.getString("maxBudget")?.toIntOrNull()
            val tripDuration = backStackEntry.arguments?.getString("tripDuration")?.toIntOrNull()
            val travelersNumber = backStackEntry.arguments?.getString("travelersNumber")?.toIntOrNull()

            val currScreen = Screen.Result(
                passedImageUris = passedImageUris,
                passedEditedResult = passedEditedResult,
                userTask = userTask,
                selectedLanguageIndex = selectedLanguageIndex,
                originLocation = originLocation,
                cityPaths = cityPaths,
                transportationTypes = transportationTypes,
                tripStyles = tripStyles,
                oneWay = oneWay,
                secretShowAd = secretShowAd,
                maxBudget = maxBudget,
                tripDuration = tripDuration,
                travelersNumber = travelersNumber
            )
            val route = Screen.Ocr.createRoute()
            ResultScreen(
                sharedViewModel,
                secretShowAd,
                onNavigateToOcrScreen = {
                    navController.navigate(route) {
                        popUpTo(currScreen.createRoute()) { inclusive = true; saveState = true }
                    }
                }
            )
            lastScreensVersions.updateOrInsert(currScreen)
        }

        composable(Screen.Ocr.createRoute()) {
            val currScreen = Screen.Ocr

            OcrScreenContent(
                sharedViewModel,
                onNavigateToResultScreen = { passedProperties ->
                    val resultScreen = lastScreensVersions[Screen.Result.index]
                    check(resultScreen is Screen.Result) { "expected $resultScreen to be Result Screen" }
                    val newResultScreen = resultScreen.copy(passedEditedResult = passedProperties ?: "")
                    val route = newResultScreen.createRoute()
                    navController.navigate(route) {
                        popUpTo(currScreen.createRoute()) { inclusive = true; saveState = true }
                    }
                }
            )
            lastScreensVersions.updateOrInsert(currScreen)
        }
    }

}