package com.example.shared.log

import android.util.Log
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Forwards INFO+ Timber log lines into [AppLog] so they survive after the
 * process exits and can be read from inside the app itself, on a build with
 * no attached computer to pull logcat from.
 *
 * Plant once, in Application.onCreate(), regardless of build type -- release
 * and CI-built debug APKs are exactly the case with no other way to see
 * what happened. This needs no changes anywhere Timber is already used:
 * every existing `Timber.e(...)`/`Timber.w(...)` call across the codebase
 * (already the convention at every catch block) is captured automatically.
 *
 * INFO, not just WARN/ERROR: a third-party SDK (AppMetrica, MobileAds, ...)
 * that quietly never activates looks identical, from here, to one that
 * activated fine -- there is no exception to catch either way. Without a
 * positive "this succeeded" line at INFO level, the log can only ever say
 * what went wrong, never confirm that something that's supposed to happen
 * silently actually did.
 */
class AppLogTree(private val appLog: AppLog) : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.INFO

    // Timber.Tree.log() is always reached through its own prepareLog(), which
    // -- when a throwable was passed to Timber.e/w/etc. -- already appends
    // Utils.getStackTraceString(t) to `message` itself before any Tree ever
    // sees it. Appending it again here duplicated the entire stack trace
    // (including its "Caused by" chain) in every single logged error, which
    // was most of why the Log screen filled up with repeated text.
    //
    // The remaining bulk (confirmed by the user still seeing walls of
    // near-identical text after the length cap alone) is that full trace
    // itself: the same handful of generic kotlinx.coroutines/dispatcher
    // frames (BaseContinuationImpl.resumeWith, DispatchedTask.run,
    // CoroutineScheduler$Worker.run, ...) repeated in nearly every single
    // error this app logs, on top of the actual substance. Every real fix
    // found from this app's logs this whole session came from an
    // exception's own message text (an HTTP error body, a decode error's
    // path, a rate-limit reason, ...), never from a specific JDK/Kotlin
    // stack frame -- so those frames are cut entirely, keeping only each
    // exception's class + message down its "Caused by" chain, plus (if any)
    // the first frame outside the JDK/Kotlin/Android platform to still say
    // roughly where it was thrown.
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val body = if (t != null) {
            val plainMessage = stripDefaultStackTrace(message, t)
            val summary = condensedThrowable(t)
            if (plainMessage.isBlank()) summary else "$plainMessage\n$summary"
        } else {
            message
        }
        appLog.record(tag ?: "App", truncate(body))
    }

    /** Undoes prepareLog()'s own append (a long-standing, stable Timber behavior) to recover the plain message. */
    private fun stripDefaultStackTrace(message: String, t: Throwable): String {
        val trace = timberStackTraceString(t)
        return when {
            message == trace -> ""
            message.endsWith("\n$trace") -> message.removeSuffix("\n$trace")
            else -> message // Unrecognized shape -- leave it alone rather than risk corrupting it.
        }
    }

    // Deliberately NOT android.util.Log.getStackTraceString(t): confirmed on
    // a real device that this call produced a fully untouched, uncondensed
    // multi-thousand-character UnknownHostException entry -- traced to
    // android.util.Log's own implementation specifically returning "" for
    // any throwable with an UnknownHostException anywhere in its cause chain
    // (a deliberate Android quirk, to reduce log spew for "network
    // unavailable"). Timber's own Utils.getStackTraceString has no such
    // special case -- it's a plain t.printStackTrace() into a StringWriter,
    // reproduced exactly here -- so comparing against Log's version silently
    // failed to match for that one exception type, leaving `message`
    // untouched and appending the condensed summary on top of the full
    // original trace instead of replacing it.
    private fun timberStackTraceString(t: Throwable): String {
        val sw = StringWriter(256)
        val pw = PrintWriter(sw, false)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    private fun condensedThrowable(t: Throwable): String = buildString {
        var current: Throwable? = t
        val seen = mutableSetOf<Throwable>()
        var isFirst = true
        while (current != null && seen.add(current)) {
            if (!isFirst) append("\nCaused by: ")
            append(current.javaClass.name)
            current.message?.let { append(": ").append(it) }
            current.stackTrace
                .firstOrNull { frame -> NOISE_PACKAGE_PREFIXES.none { frame.className.startsWith(it) } }
                ?.let { append("\n\tat ").append(it) }
            isFirst = false
            current = current.cause
        }
    }

    // A real device log hit an okhttp ConnectException with 7 Suppressed
    // sub-exceptions (one failed IP per Google endpoint tried), each with
    // its own full nested stack trace -- one single log entry, several
    // thousand characters, most of it identical boilerplate repeated 7
    // times. Kept as a final safety net alongside the trace condensing
    // above, for any oversized entry that isn't a stack trace at all (e.g. a
    // large raw JSON response body logged directly).
    private fun truncate(message: String): String {
        if (message.length <= MAX_ENTRY_CHARS) return message
        val omitted = message.length - MAX_ENTRY_CHARS
        return message.take(MAX_ENTRY_CHARS) + "\n... [$omitted more characters truncated]"
    }

    private companion object {
        const val MAX_ENTRY_CHARS = 1500

        val NOISE_PACKAGE_PREFIXES = listOf(
            "java.", "javax.", "kotlin.", "kotlinx.", "android.", "com.android.", "dalvik.", "libcore.",
        )
    }
}
