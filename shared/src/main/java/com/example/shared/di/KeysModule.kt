package com.example.shared.di

import com.example.shared.data.keys.ApiKeyRotator
import com.example.shared.data.keys.ApiProviderIds
import com.example.shared.data.keys.BundledApiKeyStore
import com.example.shared.data.keys.BundledApiKeys
import com.example.shared.data.keys.PrefsApiKeyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * One [ApiKeyRotator] per AI provider (Gemini, Groq, GigaChat, ...), each
 * reading the user's own keys from [PrefsApiKeyStore] first and falling back
 * to whichever key(s) `local.properties` baked into this build via
 * [BundledApiKeyStore]/[BundledApiKeys]. Adding a new provider later is one
 * more `@Named` binding here plus one more `<provider>_api_key` property —
 * nothing else in this module changes.
 */
@Module
@InstallIn(SingletonComponent::class)
object KeysModule {

    private fun rotatorFor(
        providerId: String,
        prefsStore: PrefsApiKeyStore,
        bundledStore: BundledApiKeyStore,
    ): ApiKeyRotator {
        BundledApiKeys.sync(bundledStore, providerId)
        return ApiKeyRotator(store = prefsStore, providerId = providerId, bundledStore = bundledStore)
    }

    @Provides
    @Singleton
    @Named(ApiProviderIds.GEMINI)
    fun provideGeminiKeyRotator(prefsStore: PrefsApiKeyStore, bundledStore: BundledApiKeyStore): ApiKeyRotator =
        rotatorFor(ApiProviderIds.GEMINI, prefsStore, bundledStore)

    @Provides
    @Singleton
    @Named(ApiProviderIds.GROQ)
    fun provideGroqKeyRotator(prefsStore: PrefsApiKeyStore, bundledStore: BundledApiKeyStore): ApiKeyRotator =
        rotatorFor(ApiProviderIds.GROQ, prefsStore, bundledStore)

    @Provides
    @Singleton
    @Named(ApiProviderIds.GIGACHAT)
    fun provideGigaChatKeyRotator(prefsStore: PrefsApiKeyStore, bundledStore: BundledApiKeyStore): ApiKeyRotator =
        rotatorFor(ApiProviderIds.GIGACHAT, prefsStore, bundledStore)
}
