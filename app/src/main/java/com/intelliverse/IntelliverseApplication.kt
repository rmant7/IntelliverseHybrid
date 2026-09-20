package com.intelliverse

import android.app.Application
import android.os.Build
import android.webkit.WebView
import com.example.shared.AppId
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.example.shared.ads.InterstitialAdUseCase
import com.example.shared.ads.OpenAdUseCase
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

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            /*
            // Initialize the logger
            Logger.initialize(applicationContext)

            // Preserve the original handler
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

            // Set up a global crash handler
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    Logger.log("CrashHandler", "Uncaught exception in thread ${thread.name}: ${throwable.message}")
                    Logger.log("CrashHandler", throwable.stackTraceToString())
                } catch (e: Exception) {
                    // If logging fails, print the stack trace to standard error
                    e.printStackTrace()
                } finally {
                    // Use the original handler to let the app crash
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            }*/

            // Take out logs of the release version with this set. logs decrease performance
            Timber.plant(Timber.DebugTree())
        }

        clearCache()

        // For WebView if sdk >= 28
        setWebViewDataDirectorySuffix()

        MobileAds.initialize(this@IntelliverseApplication) {}

        /*// Ensure Family-Safe Ads are enforced
        val requestConfiguration = RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE) // COPPA Compliance
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G) // Family-friendly ads only
            .build()

        MobileAds.setRequestConfiguration(requestConfiguration)*/

        MobileAds.setAppMuted(true)

        setPropertiesByAppContext(AppId.INTELLIVERSE)

        // preloading ads
        //todo: right now OpenAd doesn't work well
        //openAdUseCase.load()
        interstitialAdUseCase.load()


        // Initialize AppMetrica in a background thread.
        initializeAppMetrica()

    }

    private fun clearCache() {
        try {
            val cacheDir = applicationContext.cacheDir
            if (cacheDir.isDirectory) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setWebViewDataDirectorySuffix() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val processName = getProcessName()
        if (!packageName.equals(processName)) {
            WebView.setDataDirectorySuffix(processName)
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