package com.example.shared.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.example.shared.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class OpenAdUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val isAdDisabled = BuildConfig.is_advertisement_disabled
    var appOpenAd: AppOpenAd? = null

    // var openAdLoadTime: Long = 0L
    var openAdLastAdShownTime: Long = 0L
    private val openAdCooldown = 60 * 1000 // 1 minute
    var isOpenAdLoading: Boolean = false

    private var adLoadedCallback: (() -> Unit)? = null

    // used to ensure we call [showOpenAppAd] only after [appOpenAd] is initialized
    @Synchronized
    fun setAdLoadedCallback(callback: () -> Unit) {
        if (appOpenAd != null) {
            // If the ad is already loaded, invoke the callback immediately
            callback.invoke()
        } else {
            // Otherwise, store the callback for later invocation
            adLoadedCallback = callback
        }
    }

    fun load() {

        if (isAdDisabled || (isOpenAdLoading && appOpenAd != null))
            return

        isOpenAdLoading = true
        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            openAdId,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    synchronized(this@OpenAdUseCase) {
                        appOpenAd = ad
                        isOpenAdLoading = false
                        adLoadedCallback?.invoke()
                        adLoadedCallback = null
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Timber.d(loadAdError.message)
                    isOpenAdLoading = false
                    appOpenAd = null
                    adLoadedCallback = null
                }
            }
        )
    }

    fun showOpenAppAd(context: Context): Boolean {

        /*
        val timeSinceLoad = System.currentTimeMillis() - openAdLoadTime

        // isn't expired yet
        val expirationCheck = timeSinceLoad <= 4 * 60 * 60 * 1000 // 4 hour expiration check

        // appOpenAd isn't loaded or time out and cooldown isn't over yet
        if (appOpenAd == null || (expirationCheck && !cooldownCheck)) return
        */

        // cooldown is over
        val timeSinceLastShown = System.currentTimeMillis() - openAdLastAdShownTime
        val cooldownCheck = timeSinceLastShown >= openAdCooldown

        if (appOpenAd == null || !cooldownCheck) {
            Timber.d("Open app ad is null.")
            return false
        }

        appOpenAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                load()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
            }

            override fun onAdShowedFullScreenContent() {
                appOpenAd = null
                openAdLastAdShownTime = System.currentTimeMillis()
            }
        }
        Timber.d("Attempt to show open app ad.")
        appOpenAd!!.show(context as Activity)
        return true
    }

}