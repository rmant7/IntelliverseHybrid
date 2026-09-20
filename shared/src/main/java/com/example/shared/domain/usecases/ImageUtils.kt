package com.example.shared.domain.usecases

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject


class ImageUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun convertUriToByteArray(imageUri: Uri): ByteArray? {
        var inputStream: InputStream? = null
        var byteArrayOutputStream: ByteArrayOutputStream? = null
        return try {
            // Open an InputStream from the URI
            inputStream = context.contentResolver.openInputStream(imageUri)
            // Decode the input stream to a Bitmap
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Compress the Bitmap into a ByteArrayOutputStream
            byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                100,
                byteArrayOutputStream
            )

            // Convert the ByteArrayOutputStream to a ByteArray
            val byteArray = byteArrayOutputStream.toByteArray()
            byteArrayOutputStream.close()
            return byteArray
        } catch (e: SecurityException) {
            Timber.e(e)
            null
        } catch (e: NullPointerException) {
            Timber.e(e)
            null
        } catch (e: Exception) {
            Timber.e(e)
            null
        } finally {
            inputStream?.close()
            byteArrayOutputStream?.close()
        }

    }

}