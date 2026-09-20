package com.matterofchoice

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.shared.presentation.AppTopBar
import com.example.shared.presentation.common.ApplicationScaffold
import com.matterofchoice.screens.AnalysisScreen

import com.matterofchoice.screens.Game
import com.matterofchoice.screens.MainScreen
import com.matterofchoice.screens.Result
import com.matterofchoice.screens.Settings
import com.matterofchoice.ui.theme.MatterofchoiceTheme
import com.matterofchoice.viewmodel.AIViewModel


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MatterofchoiceTheme {
                Surface {
                    val context = LocalContext.current.applicationContext
                    val sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                    val isOpen = sharedPreferences.getBoolean("firstOpen",true)
                    val editor = sharedPreferences.edit()
                    editor.apply {
                        putInt("rounds",0)
                        putInt("userScore",0)
                        putInt("totalScore",0)
                    }.apply()
                    editor.putBoolean("isFirst", true)
                    editor.apply()
                    val navController = rememberNavController()
                    AppNavHost(navController, isOpen)
                }
            }
        }
    }
}
@Composable
fun AppNavHost(navController: NavHostController, isFirstOpen:Boolean) {
    NavHost(navController = navController, startDestination = if(isFirstOpen) Screens.OnboardingScreen.screen else Screens.GameScreen.screen) {
        composable(Screens.OnboardingScreen.screen) {
            WelcomeFunction(navController)
        }
        composable(Screens.GameScreen.screen) {
            MainScreen()
        }
    }
}
@Composable
fun Navigation(parentNavController: NavHostController?,aiViewModel: AIViewModel = viewModel() ) {
    val navController = rememberNavController()


   ApplicationScaffold(
        isShowed = true,
        content = {
            NavHost(
                navController = navController,
                startDestination = Screens.SettingsScreen.screen,

            ) {
                composable(Screens.GameScreen.screen) { Game(navController, aiViewModel) }
                composable(Screens.ResultScreen.screen) { Result() }
                composable(Screens.AnalysisScreen.screen) { AnalysisScreen(navController=navController, viewModel = aiViewModel) }
                composable(Screens.SettingsScreen.screen) { Settings(navController = navController, aiViewModel) }
            }
        },
        bottomBar = {
            BottomNav(navController)
        },
        topBar = {
            AppTopBar(parentNavController, stringResource(R.string.app_name_matter_of_choice), stringResource(R.string.matterofchoice_info))
        }
    )

}
@Preview(showBackground = true)
@Composable
fun Preview() {
    MatterofchoiceTheme {
        val navController = rememberNavController()
        WelcomeFunction(navController)
    }
}