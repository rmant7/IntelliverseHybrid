package com.intelliverse.localai

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocalAiEntryPoint {
    fun localChat(): LocalChatProvider
}

/** For code outside Hilt's reach (MatterOfChoice builds its own objects): the same singletons everything else gets injected. */
object LocalAiAccess {
    fun localChat(context: Context): LocalChatProvider =
        EntryPointAccessors.fromApplication(context.applicationContext, LocalAiEntryPoint::class.java).localChat()
}
