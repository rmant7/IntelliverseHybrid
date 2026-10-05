package com.intelliverse.models

import ai.localstudio.app.llama.LocalModelEngine
import ai.localstudio.sdk.LocalAi
import android.content.Context
import com.example.shared.log.AppLog
import com.intelliverse.localai.IntelliverseLocalAi
import com.intelliverse.localai.LocalChecks
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

    /**
     * rmant7/AI's on-device model engine (:llama-runtime): one RuntimeManager
     * admitting every load against free RAM and measured costs, Auto weights
     * loading, RAM measured on every load. Its log lines (LOCAL_LOAD,
     * LOCAL_GENERATE, RAM_MANAGER, RAM_MEASURE) land in this app's Log.
     */
    @Provides
    @Singleton
    fun provideLocalModelEngine(@ApplicationContext context: Context, log: AppLog): LocalModelEngine =
        LocalModelEngine(context, runtimeVersion = LocalChecks.RUNTIME_VERSION, log = log::record)

    /** The SDK every screen and sub-app asks local models through; [IntelliverseLocalAi] is this app's implementation of it. */
    @Provides
    @Singleton
    fun provideLocalAi(impl: IntelliverseLocalAi): LocalAi = impl
}
