package com.intelliverse

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.intelliverse.presentation.StartScreen


@Composable
fun Navigation() {
    val dietTracker = stringResource(com.diettracker.R.string.app_name_diet_tracker)
    val schoolKiller = stringResource(com.schoolkiller.R.string.app_name_schoolkiller)
    val styleTranslator = stringResource(com.styletranslator.R.string.app_name_styletranslator)
    val oneClickTrip = stringResource(com.oneclicktrip.R.string.app_name_oneclicktrip)
    val navController = rememberNavController()

    // Map app names to descriptions
    val appDescriptions = mapOf(
        oneClickTrip to stringResource(com.oneclicktrip.R.string.oneclicktrip_info),
        schoolKiller to stringResource(com.schoolkiller.R.string.schoolkiller_info),
        dietTracker to stringResource(com.diettracker.R.string.diet_tracker_info),
        "CheapTrip" to stringResource(R.string.cheaptrip_info),
        "Matter Of Choice" to stringResource(R.string.matterofchoice_info),
        styleTranslator to stringResource(com.styletranslator.R.string.styletranslator_info)
    )

    NavHost(navController = navController, startDestination = "start") {
        composable("start") { StartScreen(navController, appDescriptions) }
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
    }
}