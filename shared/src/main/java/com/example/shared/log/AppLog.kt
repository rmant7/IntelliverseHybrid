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
     * Set by [recordProcessExitIfNotable] when it just logged a genuinely
     * abnormal exit -- a UI layer reads and clears this once (see
     * [consumePendingCrashNotice]) to offer sending the log right away,
     * rather than the user having to know to go find the Log screen
     * themselves after something visibly went wrong.
     */
    @Volatile
    private var pendingCrashNotice: String? = null

    fun consumePendingCrashNotice(): String? {
        val notice = pendingCrashNotice
        pendingCrashNotice = null
        return notice
    }

    /**
     * The one entry point here that can see a crash after the fact — an
     * uncaught exception (or a native one, e.g. the llama.cpp JNI bridge)
     * can kill the process before any of our own Kotlin code gets a chance
     * to write anything at that moment.
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
        val entry = "$reason$description"
        record("PROCESS_EXIT", entry)
        // Only an unambiguous crash/hang pops the interruptive dialog --
        // see isDisruptiveExit. Everything else describeExitReason covers
        // is still written to the log above either way.
        if (isDisruptiveExit(last.reason)) {
            pendingCrashNotice = entry
        }
    }

    // REASON_LOW_MEMORY/REASON_OTHER/REASON_EXCESSIVE_RESOURCE_USAGE/
    // REASON_DEPENDENCY_DIED are routine background-process kills, not
    // something a user would call "the app crashed" -- this app loads
    // multi-GB local models, so Android reclaiming a backgrounded process
    // under memory pressure happens often and is expected. Popping the
    // "app closed unexpectedly" dialog for those meant it kept showing up
    // on ordinary relaunches with no actual crash, which is what widening
    // describeExitReason (see its own comment) to catch REASON_SIGNALED
    // ended up dragging in along with it. Narrowed back down to the
    // reasons that are genuinely a crash or a hang.
    private fun isDisruptiveExit(reason: Int): Boolean = when (reason) {
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_SIGNALED,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> true
        else -> false
    }

    // Widened from the original 4 codes -- a real device report of "there
    // was a crash but the log had nothing" traced (most likely) to a
    // reason code landing here as null and being silently dropped, not to
    // this mechanism failing outright. REASON_SIGNALED in particular is
    // what an unhandled native signal (SIGSEGV, SIGABRT -- exactly what a
    // JNI use-after-free produces) can surface as instead of
    // REASON_CRASH_NATIVE on some OEM builds/Android versions; better to
    // over-report a genuinely abnormal exit than risk missing one.
    // REASON_EXIT_SELF/REASON_USER_REQUESTED/REASON_USER_STOPPED/
    // REASON_PERMISSION_CHANGE deliberately excluded -- ordinary,
    // user- or system-intended exits, not failures.
    private fun describeExitReason(reason: Int): String? = when (reason) {
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_CRASH -> "crash (uncaught exception)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "killed by the system: low memory"
        ApplicationExitInfo.REASON_ANR -> "ANR (app not responding)"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by an unhandled signal (likely a native crash)"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to initialize"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed by the system: excessive resource usage"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "killed: a required system dependency died"
        ApplicationExitInfo.REASON_OTHER -> "exited abnormally (system-classified as \"other\")"
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
