package com.intelliverse.localai

import ai.localstudio.sdk.LocalImage
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Pictures for an on-device model: a phone photo shrunk to what a vision encoder takes anyway. */
object LocalImages {
    /** Longest side a picture is sent at: vision projectors resize to well under this, and a 12 MP photo only costs memory. */
    private const val MAX_SIDE_PX = 1024

    /** [uri] as a JPEG no larger than [MAX_SIDE_PX] on its longest side; null when it cannot be read as an image. */
    fun fromUri(context: Context, uri: Uri): LocalImage? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = MAX_SIDE_PX.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true).also { decoded.recycle() }
        } else {
            decoded
        }
        try {
            LocalImage(ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray(), "image/jpeg")
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    /** JPEG bytes already in hand (a mini-app's photo) as a picture for a model. */
    fun jpeg(bytes: ByteArray): LocalImage = LocalImage(bytes, "image/jpeg")
}
