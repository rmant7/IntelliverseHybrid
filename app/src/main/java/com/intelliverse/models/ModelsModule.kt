package com.intelliverse.models

import ai.localstudio.sdk.LocalAi
import android.content.Context
import com.intelliverse.llama.LocalLlamaSession
import com.intelliverse.localai.IntelliverseLocalAi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModelsModule {
    @Provides
    @Singleton
    fun provideModelDownloads(@ApplicationContext context: Context): ModelDownloads =
        ModelDownloads(context, onDownloadStarted = { ModelDownloadService.start(context) })

    @Provides
    @Singleton
    fun provideLocalLlamaSession(): LocalLlamaSession = LocalLlamaSession()

    /** The SDK every screen and sub-app asks local models through; [IntelliverseLocalAi] is this app's implementation of it. */
    @Provides
    @Singleton
    fun provideLocalAi(impl: IntelliverseLocalAi): LocalAi = impl
}
