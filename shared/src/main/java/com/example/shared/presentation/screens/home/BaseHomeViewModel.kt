package com.example.shared.presentation.screens.home

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.example.shared.ads.OpenAdUseCase
import com.example.shared.data.repositories.DeleteFileRepository
import com.example.shared.data.repositories.SaveFileRepository
import com.example.shared.domain.usecases.SpeechConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.Locale

abstract class BaseHomeViewModel(
    private val saveFileRepository: SaveFileRepository,
    private val deleteFileRepository: DeleteFileRepository,
    val openAdUseCase: OpenAdUseCase,
    private val speechConverter: SpeechConverter
) : ViewModel() {

    private val _isAppOpened = MutableStateFlow(false)
    val isAppOpened: StateFlow<Boolean> = _isAppOpened

    fun markAppOpened() {
        _isAppOpened.value = true
    }

    /* A secret way to disable ads- click the solve button 3 times
    in a short period of time.
    Mainly applicable if no image/user task was chosen, because otherwise you will
    be immediately directed to the ResultScreen. */
    private val _firstSolveClick = MutableStateFlow<Long?>(null)

    private val _secondSolveClick = MutableStateFlow<Long?>(null)

    private val _secretShowAd = MutableStateFlow(true)
    val secretShowAd: StateFlow<Boolean> = _secretShowAd

    abstract fun resetSettings()

    fun onSolve() {
        val currTime = System.currentTimeMillis() / 1000
        if (_firstSolveClick.value == null || currTime - _firstSolveClick.value!! > 3) {
            _firstSolveClick.value = currTime
            _secondSolveClick.value = null
        } else {
            if (_secondSolveClick.value == null) {
                _secondSolveClick.value = currTime
            } else {
                toggleAd()
                _firstSolveClick.value = null
                _secondSolveClick.value = null
            }
        }
    }

    fun updateUserTextTask(userTextTask: String) {
        _userTextTask.update { userTextTask }
    }

    fun showAd(context: Context) {
        openAdUseCase.showOpenAppAd(context)
    }

    /** All images uris user selected from gallery */
    private val _allImageUris = MutableStateFlow<MutableList<Uri>>(mutableListOf())
    val allImageUris: StateFlow<MutableList<Uri>> = _allImageUris

    fun insertImagesOnTheList(newImages: List<Uri>?) {
        if (newImages.isNullOrEmpty()) return

        _allImageUris.update {
            _allImageUris.value.toMutableList().apply {
                if (this.isEmpty()) toggleSelectedUri(newImages[0])
                this.addAll(newImages)
            }
        }
    }

    fun removeImageFromTheList(imageToDelete: Uri) {
        _allImageUris.update {
            _allImageUris.value.toMutableList().apply {
                this.remove(imageToDelete)
            }
        }
    }

    /** The last image uri user selected in HomeScreen */
    private val _selectedUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedUris: StateFlow<List<Uri>> = _selectedUris

    fun toggleSelectedUri(uri: Uri) {
        _selectedUris.update { current ->
            if (current.contains(uri)) current - uri else current + uri
        }
    }

    fun removeUriFromSelection(uri: Uri) {
        _selectedUris.update { it - uri }
    }

    fun clearSelection() {
        _selectedUris.value = emptyList()
    }

    /** SelectedUploadMethod: pick from gallery, take a picture etc */
    private val _selectedUploadMethodOption = MutableStateFlow(UploadFileMethodOptions.NO_OPTION)
    val selectedUploadMethodOption: StateFlow<UploadFileMethodOptions> = _selectedUploadMethodOption

    fun updateSelectedUploadMethodOption(selectedUploadMethodOption: UploadFileMethodOptions) {
        if (this.selectedUploadMethodOption.value != selectedUploadMethodOption)
            _selectedUploadMethodOption.update { selectedUploadMethodOption }
    }

    /** Should show dialog with enlarged image view or not */
    private val _enlargedImage = MutableStateFlow<Uri?>(null)
    val enlargedImage: StateFlow<Uri?> = _enlargedImage

    fun updateIsImageEnlarged(enlargedImage: Uri?) {
        _enlargedImage.update { enlargedImage }
    }

    /** Multiple permissions request */
    val permissionDialogQueueList = mutableStateListOf<String>()
    fun onDismissPermissionDialog() {
        permissionDialogQueueList.removeAt(0)
    }

    fun onPermissionResult(
        permission: String,
        isGranted: Boolean
    ) {
        if (!isGranted && !permissionDialogQueueList.contains(permission)) {
            permissionDialogQueueList.add(permission)
        }
    }

    suspend fun saveImage(uri: Uri, bitmap: Bitmap): Uri? {
        return withContext(Dispatchers.IO) {
            saveFileRepository.saveImage(uri, bitmap)
        }
    }

    suspend fun createImageUri(): Uri? {
        return withContext(Dispatchers.IO) {
            saveFileRepository.createImageUri()
        }
    }

    suspend fun getCameraSavedImageUri(): Uri? {
        return withContext(Dispatchers.IO) { saveFileRepository.getCameraSavedImageUri() }
    }

    fun checkUriValidity(uri: Uri): Boolean {
        return deleteFileRepository.checkUriValidity(uri)
    }

    private fun toggleAd() {
        _secretShowAd.value = !_secretShowAd.value
    }

    fun isLanguageSupported(locale: Locale): Boolean = speechConverter.isLanguageSupported(locale)

    private val _userTextTask = MutableStateFlow("")
    val userTextTask: StateFlow<String> = _userTextTask
}