package com.example.shared.log

import android.util.Log
import timber.log.Timber

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
    // is most of why the Log screen filled up with repeated text.
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        appLog.record(tag ?: "App", truncate(message))
    }

    // A real device log hit an okhttp ConnectException with 7 Suppressed
    // sub-exceptions (one failed IP per Google endpoint tried), each with
    // its own full nested stack trace -- one single log entry, several
    // thousand characters, most of it identical boilerplate repeated 7
    // times. Regardless of what specific exception shape produces the next
    // oversized entry, a flat cap here is more robust than special-casing
    // every verbose exception type as they turn up one at a time.
    private fun truncate(message: String): String {
        if (message.length <= MAX_ENTRY_CHARS) return message
        val omitted = message.length - MAX_ENTRY_CHARS
        return message.take(MAX_ENTRY_CHARS) + "\n... [$omitted more characters truncated]"
    }

    private companion object {
        const val MAX_ENTRY_CHARS = 1500
    }
}
