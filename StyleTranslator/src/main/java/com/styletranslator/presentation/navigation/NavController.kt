package com.styletranslator.presentation.navigation

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
import com.styletranslator.R
import com.example.shared.presentation.common.ApplicationScaffold
import com.styletranslator.presentation.screens.home.HomeScreen
import com.styletranslator.presentation.screens.output.ocr.OcrScreenContent
import com.styletranslator.presentation.screens.output.result.ResultScreen
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
            AppTopBar(parentNavController, stringResource(R.string.app_name_styletranslator), stringResource(R.string.styletranslator_info))
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
                onNavigateToResultScreen = { uriList, userTask, transformationLevel, selectedLanguageIndex, secretShowAd, sourceGender, targetGender, sourceAge, targetAge, category, style, mentality, tonePreference, translationScale ->
                    // removes the option to navigate to the ocr screen, as it becomes invalid
                    // when new results are computed
                    val ocrScreen = lastScreensVersions.find { it is Screen.Ocr }
                    ocrScreen?.let { lastScreensVersions.remove(it) }
                    sharedViewModel.reset()

                    val nextScreen = Screen.Result(
                        uriList.map { it.toString() },
                        passedEditedResult = "",
                        userTask,
                        transformationLevel,
                        selectedLanguageIndex,
                        secretShowAd,
                        sourceGender,
                        targetGender,
                        sourceAge,
                        targetAge,
                        category,
                        style,
                        mentality,
                        tonePreference,
                        translationScale
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
            route = "${Screen.Result.prefixRoute}/{passedImageUris}/{passedEditedResult}/{userTask}/{transformationLevel}/{selectedLanguageIndex}/{secretShowAd}" +
                    "?sourceGender={sourceGender}" +
                    "&targetGender={targetGender}" +
                    "&sourceAge={sourceAge}" +
                    "&targetAge={targetAge}" +
                    "&category={category}" +
                    "&style={style}" +
                    "&mentality={mentality}" +
                    "&tonePreference={tonePreference}" +
                    "&translationScale={translationScale}",
            arguments = listOf(
                navArgument("passedImageUris") { type = NavType.StringType },
                navArgument("passedEditedResult") { type = NavType.StringType },
                navArgument("userTask") { type = NavType.StringType },
                navArgument("transformationLevel") { type = NavType.StringType },
                navArgument("selectedLanguageIndex") { type = NavType.IntType },
                navArgument("secretShowAd") { type = NavType.BoolType },

                navArgument("sourceGender") { type = NavType.StringType; nullable = true },
                navArgument("targetGender") { type = NavType.StringType; nullable = true },
                navArgument("sourceAge") { type = NavType.StringType; nullable = true },
                navArgument("targetAge") { type = NavType.StringType; nullable = true },
                navArgument("category") { type = NavType.StringType; nullable = true },
                navArgument("style") { type = NavType.StringType; nullable = true },
                navArgument("mentality") { type = NavType.StringType; nullable = true },
                navArgument("tonePreference") { type = NavType.StringType; nullable = true },
                navArgument("translationScale") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val encodedUris = backStackEntry.arguments?.getString("passedImageUris") ?: ""
            val passedImageUris = encodedUris
                .split(",")
                .mapNotNull { encoded -> Uri.decode(encoded).takeIf { it.isNotBlank() } }

            val passedEditedResult = backStackEntry.arguments?.getString("passedEditedResult") ?: ""
            val userTask = backStackEntry.arguments?.getString("userTask") ?: ""
            val transformationLevel = backStackEntry.arguments?.getString("transformationLevel") ?: ""
            val selectedLanguageIndex = backStackEntry.arguments?.getInt("selectedLanguageIndex") ?: 0
            val secretShowAd = backStackEntry.arguments?.getBoolean("secretShowAd") ?: false

            // Nullable values
            val sourceGender = backStackEntry.arguments?.getString("sourceGender")
            val targetGender = backStackEntry.arguments?.getString("targetGender")
            val sourceAge = backStackEntry.arguments?.getString("sourceAge")?.toIntOrNull()
            val targetAge = backStackEntry.arguments?.getString("targetAge")?.toIntOrNull()
            val category = backStackEntry.arguments?.getString("category")
            val style = backStackEntry.arguments?.getString("style")
            val mentality = backStackEntry.arguments?.getString("mentality")
            val tonePreference = backStackEntry.arguments?.getString("tonePreference")
            val translationScale = backStackEntry.arguments
                ?.getString("translationScale")
                ?.toFloatOrNull()
            val currScreen = Screen.Result(
                passedImageUris = passedImageUris,
                passedEditedResult = passedEditedResult,
                userTask = userTask,
                transformationLevel = transformationLevel,
                selectedLanguageIndex = selectedLanguageIndex,
                secretShowAd = secretShowAd,
                sourceGender = sourceGender,
                targetGender = targetGender,
                sourceAge = sourceAge,
                targetAge = targetAge,
                category = category,
                style = style,
                mentality = mentality,
                tonePreference = tonePreference,
                translationScale = translationScale
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