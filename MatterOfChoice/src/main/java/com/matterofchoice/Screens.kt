package com.matterofchoice

sealed class Screens(val screen: String) {
    data object GameScreen: Screens("Game")
    data object ResultScreen: Screens("Result")
    data object AnalysisScreen: Screens("Analysis")
    data object SettingsScreen: Screens("Settings")
    data object OnboardingScreen: Screens("OnBoarding")

}