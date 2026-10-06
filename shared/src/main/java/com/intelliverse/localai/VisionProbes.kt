package com.intelliverse.localai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/** A picture with one right answer -- the same pictures rmant7/AI's check shows a model. */
sealed interface ProbeImage {
    data class Digit(val digit: Int) : ProbeImage {
        init { require(digit in 0..9) }
    }

    /** A filled circle of [rgb] (0xRRGGBB). */
    data class Disc(val rgb: Int) : ProbeImage {
        companion object {
            const val RED = 0xE00000
            const val BLUE = 0x0030E0
        }
    }

    companion object {
        const val SIZE_PX = 448

        /**
         * [image] as a PNG: square, white background, the subject large and
         * centred -- nothing a vision encoder could plausibly misread, so a
         * wrong answer is the model's, not the picture's.
         */
        fun png(image: ProbeImage): ByteArray {
            val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val centre = SIZE_PX / 2f
                when (image) {
                    is Digit -> {
                        val text = image.digit.toString()
                        paint.color = Color.BLACK
                        paint.typeface = Typeface.DEFAULT_BOLD
                        paint.textSize = SIZE_PX * 0.75f
                        paint.textAlign = Paint.Align.CENTER
                        val bounds = Rect()
                        paint.getTextBounds(text, 0, text.length, bounds)
                        canvas.drawText(text, centre, centre - bounds.exactCenterY(), paint)
                    }
                    is Disc -> {
                        paint.color = Color.BLACK or image.rgb
                        canvas.drawCircle(centre, centre, SIZE_PX * 0.35f, paint)
                    }
                }
                return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }
    }
}
