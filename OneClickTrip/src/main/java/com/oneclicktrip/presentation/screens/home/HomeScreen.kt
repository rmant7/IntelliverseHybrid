package com.oneclicktrip.presentation.screens.home

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.example.shared.presentation.screens.home.HandleAppOpenAd
import com.example.shared.presentation.screens.home.HandleCameraLauncher
import com.oneclicktrip.R
import com.example.shared.presentation.screens.home.HandleGalleryLauncher
import com.example.shared.presentation.screens.home.HandleImageEnlargement
import com.example.shared.presentation.screens.home.HandlePermissions


@Composable
fun HomeScreen(
    onNavigateToResultScreen: (List<Uri>, String, Int, String, List<String>, List<String>, List<String>, Boolean, Boolean, Int?, Int?, Int?) -> Unit
) {
    val viewModel: HomeViewModel = hiltViewModel()
    var isHomeScreenUIShowed by remember { mutableStateOf(false) }
    var launchGallery by remember { mutableStateOf<Boolean?>(null) }
    var launchCamera by remember { mutableStateOf<Boolean?>(null) }

    HandleAppOpenAd(viewModel = viewModel)

    HandlePermissions(
        viewModel = viewModel,
        setLaunchGallery = { launchGallery = it },
        setLaunchCamera = { launchCamera = it },
        updateIsHomeScreenUIShowed = { isHomeScreenUIShowed = it }
    )

    HandleGalleryLauncher(
        viewModel = viewModel,
        launchGallery = launchGallery,
        setLaunchGallery = { launchGallery = it },
        somethingWentWrong = stringResource(R.string.something_went_wrong),
        failToLoadUri = stringResource(R.string.fail_to_load_Uri)
    )

    HandleCameraLauncher(
        viewModel = viewModel,
        viewModelScope = viewModel.viewModelScope,
        launchCamera = launchCamera,
        imageFailedToLoad = stringResource(R.string.fail_to_load_Uri),
        cameraFailedToOpen = stringResource(R.string.camera_fail_to_open)
    ) { launchCamera = it }

    HandleImageEnlargement(viewModel)

    BackHandler {}

    /** Home Screen main content */
    HomeScreenContent(
        viewModel = viewModel,
        onNavigateToResultScreen = onNavigateToResultScreen,
    )


}



