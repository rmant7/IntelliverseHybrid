package com.intelliverse

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.intelliverse.models.PurposeFilter
import com.intelliverse.presentation.ChatScreen
import com.intelliverse.presentation.LogScreen
import com.intelliverse.presentation.ModelsScreen
import com.intelliverse.presentation.SettingsScreen
import com.intelliverse.presentation.StartScreen


@Composable
fun Navigation() {
    val dietTracker = stringResource(com.diettracker.R.string.app_name_diet_tracker)
    val schoolKiller = stringResource(com.schoolkiller.R.string.app_name_schoolkiller)
    val styleTranslator = stringResource(com.styletranslator.R.string.app_name_styletranslator)
    val oneClickTrip = stringResource(com.oneclicktrip.R.string.app_name_oneclicktrip)
    val matterOfChoice = stringResource(com.matterofchoice.R.string.app_name_matter_of_choice)
    val navController = rememberNavController()
    // A mini-app's "get this model" offer (LocalModelAdviceDialog) opens the Models screen on it.
    androidx.compose.runtime.LaunchedEffect(navController) {
        com.intelliverse.localai.LocalModelNavigation.requests.collect { route -> navController.navigate(route) }
    }

    // Map app names to descriptions
    val appDescriptions = mapOf(
        oneClickTrip to stringResource(com.oneclicktrip.R.string.oneclicktrip_info),
        schoolKiller to stringResource(com.schoolkiller.R.string.schoolkiller_info),
        dietTracker to stringResource(com.diettracker.R.string.diet_tracker_info),
        "CheapTrip" to stringResource(R.string.cheaptrip_info),
        matterOfChoice to stringResource(com.matterofchoice.R.string.matterofchoice_info),
        styleTranslator to stringResource(com.styletranslator.R.string.styletranslator_info)
    )

    NavHost(navController = navController, startDestination = "start") {
        composable("start") { StartScreen(navController, appDescriptions) }
        composable("log") { LogScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
        // "models", "models?purpose=CHAT" (Chat's gear), or "models?purpose=VISION&model=<id>" (a mini-app's offer: that model's card).
        composable(
            "models?purpose={purpose}&model={model}",
            arguments = listOf(
                navArgument("purpose") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("model") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val purpose = entry.arguments?.getString("purpose")?.let { name -> PurposeFilter.entries.firstOrNull { it.name == name } }
            ModelsScreen(navController, initialPurpose = purpose, initialModelId = entry.arguments?.getString("model"))
        }
        composable("chat") { ChatScreen(navController) }
        composable(schoolKiller) {
            com.schoolkiller.presentation.navigation.Navigation(
                navController
            )
        }
        composable(dietTracker) {
            com.diettracker.presentation.navigation.Navigation(
                navController
            )
        }
        composable(styleTranslator) {
            com.styletranslator.presentation.navigation.Navigation(
                navController
            )
        }
        composable(oneClickTrip) {
            com.oneclicktrip.presentation.navigation.Navigation(
                navController
            )
        }
        composable(matterOfChoice) {
            com.matterofchoice.Navigation(navController)
        }
    }
}