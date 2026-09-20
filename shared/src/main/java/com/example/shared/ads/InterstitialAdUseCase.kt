package com.example.shared.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.example.shared.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterstitialAdUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var isLoaded = false

    fun load() {
        if (BuildConfig.is_advertisement_disabled
            || isLoading || isLoaded
        ) {
            return
        }

        Timber.d("Loading interstitial ad.")
        isLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            interstitialAdId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                    isLoaded = false
                    Timber.d("Adds server is not available. ${adError.message}")
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                    isLoaded = true
                    Timber.d("Interstitial ad was successfully loaded.")
                }
            }
        )
    }


    fun show(context: Context): Boolean {

        if (interstitialAd == null) {
            Timber.d("Interstitial ad is null.")
            return false
        }

        Timber.d("Attempt to show interstitial ad.")
        interstitialAd!!.show(context as Activity)

        interstitialAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                isLoaded = false
                Timber.d("Interstitial ad was dismissed. Loading new ad.")
                load()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                isLoaded = false
            }

            override fun onAdShowedFullScreenContent() {
                interstitialAd = null
                isLoaded = false
            }
        }
        return true
    }

}


