package com.matterofchoice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.shared.presentation.navigation.BottomNavigationItem

@Composable
fun BottomNav(navController: NavHostController) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavigationItem(
                label = "Home",
                icon = Icons.Filled.Home,
                isSelected = currentRoute == Screens.SettingsScreen.screen,
                onClick = {
                    navController.navigate(Screens.SettingsScreen.screen) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
            BottomNavigationItem(
                label = "Simulation",
                icon = R.drawable.game1,
                isSelected = currentRoute == Screens.GameScreen.screen,
                onClick = {
                    navController.navigate(Screens.GameScreen.screen) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
            BottomNavigationItem(
                label = "Analysis",
                icon = R.drawable.analysis,
                isSelected = currentRoute == Screens.AnalysisScreen.screen,
                onClick = {
                    navController.navigate(Screens.AnalysisScreen.screen) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
