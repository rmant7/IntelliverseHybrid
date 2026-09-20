package com.example.shared

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A logger which intended to log errors which cause crashes
 * into the Documents folder in the device's internal storage.
 * Useful if someone get crashes on his physical devices, but the crashes
 * do not reproduce on your emulators/device.
 * Currently the use site of this logger is commented out, should be used
 * for debugging purposes.
 */
object Logger {
    private var logFile: File? = null

    private fun refreshMediaScanner(context: Context, file: File) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null
        ) { path, uri ->
            println("File scanned: $path, URI: $uri")
        }
    }

    fun initialize(context: Context) {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

        // Ensure the directory exists
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }

        // Create the log file inside the Documents directory
        logFile = File(documentsDir, "app_crash_logs.txt")
        if (!logFile!!.exists()) {
            val created = logFile!!.createNewFile()
            if (created) {
                refreshMediaScanner(context, logFile!!)
            }
        }
    }

    fun log(tag: String, message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "$timestamp [$tag]: $message\n"
            logFile!!.appendText(logEntry)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
