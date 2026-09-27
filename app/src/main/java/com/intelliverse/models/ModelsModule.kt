package com.intelliverse.models

import android.content.Context
import com.intelliverse.llama.LocalLlamaSession
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
        ModelDownloads(context)

    @Provides
    @Singleton
    fun provideLocalLlamaSession(): LocalLlamaSession = LocalLlamaSession()
}
