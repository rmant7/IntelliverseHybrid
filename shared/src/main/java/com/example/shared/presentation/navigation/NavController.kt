package com.example.shared.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

// Using Material 3 instead of Material for less apk size.
@Composable
fun BottomNavigationItem(
    label: String = "",
    icon: Any,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {

    val iconPainter: Painter = when (icon) {
        is Int -> painterResource(icon)
        is ImageVector -> rememberVectorPainter(icon)
        else -> rememberVectorPainter(Icons.Default.Clear)
    }

    IconButton(
        modifier = Modifier.size(35.dp),
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = label,
            modifier = Modifier.fillMaxSize(),
            tint = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }

}

@Composable
fun <T: IScreen> BottomNavigationBar(
    navController: NavHostController,
    lastScreensVersions: MutableList<T>
) {

    val currentDestination = navController.currentBackStackEntryAsState().value?.destination?.route
    // for devices with low API level, padding is required
    val insets = WindowInsets.systemBars
    val bottomPadding = with(LocalDensity.current) { insets.getBottom(this).toDp() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        lastScreensVersions.forEach { screen ->
            val isSelected = currentDestination == screen.templateRoute()
            val route = screen.createRoute()
            BottomNavigationItem(
                icon = screen.imageVector,
                isSelected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }

}