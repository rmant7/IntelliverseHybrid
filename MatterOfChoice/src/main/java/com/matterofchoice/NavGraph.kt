package com.matterofchoice


import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

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
        BottomNavItem(
            modifier = Modifier.weight(1f),
            isSelected = currentRoute == Screens.GameScreen.screen,
            selectedIcon = R.drawable.game1,
            unselectedIcon = R.drawable.game2,
            onClick = {
                navController.navigate(Screens.GameScreen.screen) {
                    popUpTo(Screens.SettingsScreen.screen) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
        BottomNavItem(
            modifier = Modifier.weight(1f),
            isSelected = currentRoute == Screens.SettingsScreen.screen,
            selectedIcon =  R.drawable.settings_2,
            unselectedIcon = R.drawable.settings_1,
            onClick = {
                navController.navigate(Screens.SettingsScreen.screen) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )

        BottomNavItem(
            modifier = Modifier.weight(1f),
            isSelected = currentRoute == Screens.AnalysisScreen.screen,
            selectedIcon = R.drawable.analysis2,
            unselectedIcon = R.drawable.analysis,
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

@Composable
fun BottomNavItem(
    isSelected: Boolean,
    selectedIcon: Int,
    unselectedIcon: Int,
    onClick: () -> Unit,
    modifier: Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(if (isSelected) selectedIcon else unselectedIcon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
