package com.example.shared.domain.usecases

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject


class ImageUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun convertUriToByteArray(imageUri: Uri): ByteArray? {
        var byteArrayOutputStream: ByteArrayOutputStream? = null
        return try {
            val bitmap = decodeDownsampledBitmap(imageUri) ?: return null

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
            Timber.e(e, "Image unreadable: no permission for ${describe(imageUri)}")
            null
        } catch (e: NullPointerException) {
            Timber.e(e, "Image unreadable: ${describe(imageUri)}")
            null
        } catch (e: Exception) {
            Timber.e(e, "Image unreadable: ${describe(imageUri)}")
            null
        } finally {
            byteArrayOutputStream?.close()
        }

    }

    /**
     * Decodes [imageUri] at roughly [MAX_DIMENSION_PX] on its longer side
     * instead of a modern phone camera's full resolution (commonly 4000px+/
     * 12MP+) -- Google Play's own pre-launch report flagged the previous
     * plain BitmapFactory.decodeStream call (no Options at all) as a real
     * memory-usage risk, and none of this app's cloud vision calls need
     * more detail than this to read text or identify objects in a photo.
     * Two passes, same URI opened twice: inJustDecodeBounds first reads
     * only the image header (no pixel memory allocated) to compute
     * inSampleSize, then the real decode uses it.
     */
    private fun decodeDownsampledBitmap(imageUri: Uri): Bitmap? {
        // Each null below used to be silent: a photo that could not be read
        // reached the providers as "no image" with nothing in the log (device
        // build #145, DietTracker). Every one now says which step and what
        // the system knows about the file.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: run {
            Timber.w("Image unreadable: no input stream for ${describe(imageUri)}")
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Timber.w("Image unreadable: no decodable header (mime ${bounds.outMimeType}) in ${describe(imageUri)}")
            return null
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
        }
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        if (bitmap == null) {
            Timber.w(
                "Image unreadable: decode failed for ${bounds.outWidth}x${bounds.outHeight} " +
                    "${bounds.outMimeType} (sample ${options.inSampleSize}) in ${describe(imageUri)}"
            )
        }
        return bitmap
    }

    /** Scheme, provider and MIME type -- enough to tell a picker, camera or document URI apart; no file contents. */
    private fun describe(uri: Uri): String {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return "${uri.scheme}://${uri.authority} (type $mime)"
    }

    /** inSampleSize only downsamples by powers of 2 -- the coarsest one that still keeps the longer side at or above [maxDimension]. */
    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var longerSide = maxOf(width, height)
        while (longerSide / 2 >= maxDimension) {
            sampleSize *= 2
            longerSide /= 2
        }
        return sampleSize
    }

    private companion object {
        // Generous for OCR/vision-model quality -- well above what any
        // cloud vision API needs to read text or identify objects in a
        // photo, while still cutting memory for a modern phone's 12MP+
        // camera output by a real, meaningful factor.
        const val MAX_DIMENSION_PX = 2048
    }
}