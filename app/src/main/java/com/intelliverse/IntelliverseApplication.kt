package com.intelliverse

import android.app.Application
import android.os.Build
import android.webkit.WebView
import com.example.shared.AppId
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.example.shared.ads.InterstitialAdUseCase
import com.example.shared.ads.OpenAdUseCase
import com.example.shared.log.AppLog
import com.example.shared.log.AppLogTree
import com.example.shared.setPropertiesByAppContext
import dagger.hilt.android.HiltAndroidApp
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class IntelliverseApplication : Application() {

    @Inject
    lateinit var openAdUseCase: OpenAdUseCase

    @Inject
    lateinit var interstitialAdUseCase: InterstitialAdUseCase

    @Inject
    lateinit var appLog: AppLog

    override fun onCreate() {
        super.onCreate()

        // Planted first and unconditionally (not just in debug builds): a
        // release or CI-built debug APK is exactly the case with no attached
        // computer to pull logcat from, which is the whole reason this
        // exists -- reachable from the start screen's own overflow menu.
        Timber.plant(AppLogTree(appLog))
        appLog.recordProcessExitIfNotable()
        installGlobalCrashLogger()

        if (BuildConfig.DEBUG) {
            // Take out logs of the release version with this set. logs decrease performance
            Timber.plant(Timber.DebugTree())
        }

        clearCache()

        // For WebView if sdk >= 28
        setWebViewDataDirectorySuffix()

        initializeAds()

        try {
            setPropertiesByAppContext(AppId.INTELLIVERSE)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set app-wide properties")
        }

        // preloading ads
        //todo: right now OpenAd doesn't work well
        //openAdUseCase.load()
        try {
            interstitialAdUseCase.load()
        } catch (e: Exception) {
            Timber.e(e, "Failed to preload the interstitial ad")
        }

        // Initialize AppMetrica in a background thread.
        initializeAppMetrica()

    }

    /**
     * Catches an uncaught exception on any thread at the moment it happens
     * and logs it via Timber (so AppLogTree writes it to AppLog's on-disk
     * file synchronously, before the process actually dies), then always
     * hands off to whatever default handler was already installed --
     * Android's own crash dialog and process teardown still happen exactly
     * as they would have otherwise. This only adds a log line; it never
     * suppresses or changes how the crash itself plays out.
     */
    private fun installGlobalCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Timber.e(throwable, "Uncaught exception on thread '${thread.name}'")
            } catch (loggingFailure: Throwable) {
                // Never let a failure in the logger itself swallow the
                // original crash silently.
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun clearCache() {
        try {
            val cacheDir = applicationContext.cacheDir
            if (cacheDir.isDirectory) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear the cache directory")
        }
    }

    private fun setWebViewDataDirectorySuffix() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
            val processName = getProcessName()
            if (!packageName.equals(processName)) {
                WebView.setDataDirectorySuffix(processName)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set the WebView data directory suffix")
        }
    }

    private fun initializeAds() {
        try {
            MobileAds.initialize(this@IntelliverseApplication) {}

            /*// Ensure Family-Safe Ads are enforced
            val requestConfiguration = RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE) // COPPA Compliance
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G) // Family-friendly ads only
                .build()

            MobileAds.setRequestConfiguration(requestConfiguration)*/

            MobileAds.setAppMuted(true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize MobileAds")
        }
    }

    private fun initializeAppMetrica() = CoroutineScope(Dispatchers.IO).launch {
        try {
            // Init FirebaseApp for all processes
            FirebaseApp.initializeApp(this@IntelliverseApplication)

            val apiKey = BuildConfig.app_metrica_api_key
            if (apiKey.isNullOrBlank() || apiKey == "null") {
                // No local.properties entry (e.g. a CI or contributor build with
                // no AppMetrica project of its own) -- BuildConfig then holds the
                // literal text "null", which AppMetricaConfig.newConfigBuilder
                // rejects immediately as an invalid key format, crashing every
                // launch. Analytics not being configured is not a reason to
                // crash the whole app.
                Timber.w("AppMetrica API key is not configured; skipping AppMetrica initialization.")
                return@launch
            }

            Timber.d("Creating an extended library configuration.")
            val config = AppMetricaConfig
                .newConfigBuilder(apiKey)
                .withLocationTracking(true)
                .withSessionsAutoTrackingEnabled(true)
                .build()

            Timber.d("Initializing the AppMetrica SDK.")
            AppMetrica.activate(applicationContext, config)
            // Automatic tracking of user activity.
            // Probably doesn't work for older api.
            AppMetrica.enableActivityAutoTracking(this@IntelliverseApplication)

            // A confirmation, not just an error path: a key that's present but
            // wrong (typo, wrong console project, trailing whitespace from a
            // copy-paste into the GitHub secret) activates without throwing --
            // AppMetrica.activate() doesn't validate against the server
            // synchronously -- so "nothing in the log" would otherwise look
            // identical to "activated fine, dashboard just hasn't caught up
            // yet." This line at least confirms which of those it is, and
            // the key's last 4 characters are enough to tell whether it's
            // the one actually configured in the AppMetrica console.
            Timber.i(
                "AppMetrica activated for package=$packageName, key ending in ...${apiKey.takeLast(4)}"
            )
        } catch (e: Exception) {
            // This runs unsupervised (no parent Job to catch an uncaught
            // failure), and it's called synchronously from onCreate(), so any
            // exception here -- a malformed key, a Firebase misconfiguration --
            // used to crash the app on every single launch instead of just
            // leaving analytics off.
            Timber.e(e, "Failed to initialize AppMetrica")
        }
        /* Devs recommended to send events manually if metrica data isn't
           updated consistently, however seems like it doesn't work. */
        //AppMetrica.sendEventsBuffer()
    }
}
