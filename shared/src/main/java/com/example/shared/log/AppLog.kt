package com.example.shared.log

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A small on-disk log of errors the app has hit, entirely separate from
 * logcat — which nobody testing a debug build off a GitHub Actions artifact
 * has any ordinary way to read. The whole point is a report a user CAN get
 * out: open the start screen's own overflow menu -> "Log", read what
 * happened, tap "Copy" and paste it wherever it needs to go for someone
 * else to actually diagnose it. Ported from rmant7/AI's own AppLog.
 *
 * Deliberately flat, human-readable text rather than structured data — this
 * is read by a person, not parsed by code.
 */
@Singleton
class AppLog @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, "app-log.txt")
    private val prefs = context.getSharedPreferences("app-log", Context.MODE_PRIVATE)
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun record(tag: String, message: String) {
        runCatching {
            file.appendText("[${format.format(Date())}] $tag: $message\n")
            trimIfTooLarge()
        }
    }

    fun readAll(): String = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull().orEmpty()

    fun clear() {
        runCatching { file.delete() }
    }

    /**
     * The one entry point here that can see a crash after the fact — an
     * uncaught exception (or a native one, in a future release build with
     * NDK code) can kill the process before any of our own Kotlin code gets
     * a chance to write anything at that moment.
     * [ActivityManager.getHistoricalProcessExitReasons] (API 30+) is
     * Android's own record of why the previous process instance actually
     * died, queried fresh on the next cold start — call this once from
     * Application.onCreate().
     */
    fun recordProcessExitIfNotable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val last = runCatching {
            activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
        }.getOrNull() ?: return

        // Android keeps this history around across many launches -- without
        // remembering which one was already reported, every single cold
        // start would re-log the same old crash forever.
        val lastSeen = prefs.getLong(KEY_LAST_EXIT_TIMESTAMP, 0L)
        if (last.timestamp <= lastSeen) return
        prefs.edit().putLong(KEY_LAST_EXIT_TIMESTAMP, last.timestamp).apply()

        val reason = describeExitReason(last.reason) ?: return // ordinary exits aren't worth logging
        val description = last.description?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        record("PROCESS_EXIT", "$reason$description")
    }

    private fun describeExitReason(reason: Int): String? = when (reason) {
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_CRASH -> "crash (uncaught exception)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "killed by the system: low memory"
        ApplicationExitInfo.REASON_ANR -> "ANR (app not responding)"
        else -> null
    }

    private fun trimIfTooLarge() {
        if (file.length() <= MAX_BYTES) return
        val lines = file.readLines().takeLast(MAX_LINES)
        file.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private companion object {
        const val KEY_LAST_EXIT_TIMESTAMP = "lastExitTimestamp"

        // ~200KB / 1000 lines is generous for a human-readable error log and
        // still small enough that "copy" and "paste into a chat" stay fast.
        const val MAX_BYTES = 200_000L
        const val MAX_LINES = 1_000
    }
}
