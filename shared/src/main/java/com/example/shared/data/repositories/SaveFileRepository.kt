package com.example.shared.data.repositories

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@ViewModelScoped
class SaveFileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var getUri: Uri? = null

    /**
     * Creates an image and stores it in the device's storage.
     * Returns a Uri reference to the stored location.
     */
    suspend fun createImageUri(): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val timeInMillis = System.currentTimeMillis()
        val fileName = "${timeInMillis}_image.jpg"
        val directoryPicturesPath = Environment.DIRECTORY_PICTURES

        val mediaPath: String
        val folderPath: String
        val imageCollection: Uri

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imageCollection = MediaStore.Images.Media
                .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            mediaPath = MediaStore.MediaColumns.RELATIVE_PATH
            folderPath = "$directoryPicturesPath/SchoolKiller"
        } else {
            imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mediaPath = MediaStore.MediaColumns.DATA
            folderPath = File(
                getSchoolKillerFolder(directoryPicturesPath), fileName
            ).absolutePath
        }

        val imageContentValues = ContentValues().apply {
            put(mediaPath, folderPath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
            put(MediaStore.MediaColumns.DATE_TAKEN, timeInMillis)
        }

        return@withContext resolver.insert(imageCollection, imageContentValues)?.also {
            getUri = it
        }
    }

    /**
     * Currently unused, may be useful in cases where it is required
     * to save a Bitmap to storage (e.g., for edited or processed images).
     */
    suspend fun saveImage(uri: Uri, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            resolver.openOutputStream(uri)?.let { outputStream ->
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG, 100, outputStream
                )
            }
            return@withContext uri
        } catch (e: Exception) {
            Timber.d(e)
            resolver.delete(uri, null, null)
            return@withContext null // Return null if an error occurs
        } finally {
            bitmap.recycle()
        }
    }

    // Define the file path manually as APIs below lower than 29
    // doesn't have relative paths (MediaStore.RELATIVE_PATH)
    private fun getSchoolKillerFolder(directoryPicturesPath: String): File {
        val picturesDir = Environment.getExternalStoragePublicDirectory(directoryPicturesPath)
        val folder = File(picturesDir, "SchoolKiller")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }

    fun getCameraSavedImageUri(): Uri? {
        return getUri
    }
}
