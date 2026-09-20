package com.example.shared.log

import android.util.Log
import timber.log.Timber

/**
 * Forwards WARN/ERROR Timber log lines into [AppLog] so they survive after
 * the process exits and can be read from inside the app itself, on a build
 * with no attached computer to pull logcat from.
 *
 * Plant once, in Application.onCreate(), regardless of build type -- release
 * and CI-built debug APKs are exactly the case with no other way to see
 * what happened. This needs no changes anywhere Timber is already used:
 * every existing `Timber.e(...)`/`Timber.w(...)` call across the codebase
 * (already the convention at every catch block) is captured automatically.
 */
class AppLogTree(private val appLog: AppLog) : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val full = if (t != null) "$message\n${Log.getStackTraceString(t)}" else message
        appLog.record(tag ?: "App", full)
    }
}
