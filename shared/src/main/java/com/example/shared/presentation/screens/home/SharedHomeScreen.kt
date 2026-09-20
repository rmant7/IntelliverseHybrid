package com.example.shared.presentation.screens.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import com.example.shared.presentation.common.dialog.EnlargedImageDialog
import com.example.shared.presentation.common.dialog.PermissionMessageRationale
import com.example.shared.presentation.common.dialog.PermissionRequestDialog
import com.example.shared.presentation.permissions.PermissionSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

fun Activity.openAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}

@Composable
fun HandleAppOpenAd(viewModel: BaseHomeViewModel) {
    val context = LocalContext.current
    val isAppOpened by viewModel.isAppOpened.collectAsState()

    LaunchedEffect(isAppOpened) {
        if (!isAppOpened) {
            if (viewModel.openAdUseCase.appOpenAd != null) {
                // If the ad is ready, show it immediately
                viewModel.showAd(context)
            } else {
                // Register a callback to show the ad when it’s ready
                viewModel.openAdUseCase.setAdLoadedCallback {
                    viewModel.showAd(context)
                }
            }
            viewModel.markAppOpened()
        }
    }
}

@Composable
fun HandlePermissions(
    viewModel: BaseHomeViewModel,
    setLaunchGallery: (Boolean?) -> Unit,
    setLaunchCamera: (Boolean?) -> Unit,
    updateIsHomeScreenUIShowed: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val selectedUploadMethodOption = viewModel.selectedUploadMethodOption.collectAsState()
    val dialogQueueList = viewModel.permissionDialogQueueList

    /** Permission Launcher */
    val permissionResultLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->

            // Notify ViewModel about each permission result
            perms.forEach { (permission, granted) ->
                viewModel.onPermissionResult(permission, granted)
            }

            val allGranted = perms.values.all { it }

            // Only camera flow goes through this launcher now
            if (allGranted &&
                selectedUploadMethodOption.value == UploadFileMethodOptions.TAKE_A_PICTURE
            ) {
                setLaunchCamera(true)
            }

            // Reset the selection after handling permissions
            viewModel.updateSelectedUploadMethodOption(UploadFileMethodOptions.NO_OPTION)
        }


    /** Button Cases */
    when (selectedUploadMethodOption.value) {

        UploadFileMethodOptions.TAKE_A_PICTURE -> {
            val cameraPermissionSet = PermissionSet().getCameraPermissionSet()
            permissionResultLauncher.launch(cameraPermissionSet)
        }

        UploadFileMethodOptions.UPLOAD_AN_IMAGE -> {
            setLaunchGallery(true)
            viewModel.updateSelectedUploadMethodOption(UploadFileMethodOptions.NO_OPTION)
        }

        UploadFileMethodOptions.UPLOAD_A_FILE -> {
            viewModel.updateSelectedUploadMethodOption(UploadFileMethodOptions.NO_OPTION)
            Toast.makeText(
                context,
                "Function is not ready yet",
                Toast.LENGTH_SHORT
            ).show()
        }

        UploadFileMethodOptions.PROVIDE_A_LINK -> {
            viewModel.updateSelectedUploadMethodOption(UploadFileMethodOptions.NO_OPTION)
            Toast.makeText(
                context,
                "Function is not ready yet",
                Toast.LENGTH_SHORT
            ).show()
        }

        UploadFileMethodOptions.NO_OPTION -> {
            updateIsHomeScreenUIShowed(true)
        }
    }

    dialogQueueList
        .reversed()
        .forEach { permission ->
            PermissionRequestDialog(
                isPermanentlyDeclined = !shouldShowRequestPermissionRationale(
                    context as Activity,
                    permission
                ),
                onDismiss = viewModel::onDismissPermissionDialog,
                onGoToAppSettings = { context.openAppSettings() },
                onConfirm = {
                    permissionResultLauncher.launch(
                        arrayOf(permission)
                    )
                },
                permissionMessageRationale = when (permission) {
                    Manifest.permission.CAMERA -> {
                        PermissionMessageRationale.CameraPermissionMessage()
                    }

                    else -> return@forEach
                }
            )
        }
}

@Composable
fun HandleGalleryLauncher(
    viewModel: BaseHomeViewModel,
    launchGallery: Boolean?,
    setLaunchGallery: (Boolean?) -> Unit,
    somethingWentWrong: String,
    failToLoadUri: String
) {
    val context = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            try {
                viewModel.insertImagesOnTheList(uris)
            } catch (e: Exception) {
                Toast.makeText(context, somethingWentWrong, Toast.LENGTH_SHORT).show()
            }
        }
    )

    LaunchedEffect(launchGallery) {
        if (launchGallery == true) {
            try {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (e: Exception) {
                Toast.makeText(context, failToLoadUri, Toast.LENGTH_SHORT).show()
            }
            setLaunchGallery(null)
        }
    }
}

@Composable
fun HandleCameraLauncher(
    viewModel: BaseHomeViewModel,
    viewModelScope: CoroutineScope,
    launchCamera: Boolean?,
    imageFailedToLoad: String,
    cameraFailedToOpen: String,
    setLaunchCamera: (Boolean?) -> Unit
) {
    val context = LocalContext.current

    /** Launcher for the Camera */
    val cameraLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModelScope.launch {
                    val uri = viewModel.getCameraSavedImageUri()
                    uri?.let { viewModel.insertImagesOnTheList(listOf(it)) } ?: run {
                        Toast.makeText(context, imageFailedToLoad, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    LaunchedEffect(key1 = launchCamera) {
        if (launchCamera == true) {
            val photoUri = viewModel.createImageUri()
            if (photoUri == null) {
                Toast.makeText(context, cameraFailedToOpen, Toast.LENGTH_SHORT).show()
                setLaunchCamera(null)
                return@LaunchedEffect // Exit early if URI creation fails
            }
            // instruct the camera where to save the image
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            try {
                cameraLauncher.launch(takePictureIntent)
            } catch (e: Exception) {
                Timber.w(e, "camera launcher activity failed")
                Toast.makeText(context, cameraFailedToOpen, Toast.LENGTH_SHORT).show()
            }
            setLaunchCamera(null)
        }
    }
}

@Composable
fun HandleImageEnlargement(viewModel: BaseHomeViewModel) {
    val enlargedImage = viewModel.enlargedImage.collectAsState()
    if (enlargedImage.value != null) {
        EnlargedImageDialog(
            image = enlargedImage.value,
            isImageEnlarged = true,
            onDismiss = { viewModel.updateIsImageEnlarged(null) }
        )
    }
}