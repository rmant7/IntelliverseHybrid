package com.example.shared.di


import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient {
        // OkHttp, not Android: see shared/build.gradle.kts's own comment on
        // this same dependency for why -- the Android engine's ancient
        // bundled HTTP stack doesn't reliably detect a stale pooled
        // keep-alive connection before reusing it, which read as
        // Gemini-specific network flakiness until a real device log made
        // clear it wasn't (confirmed-good connectivity, still failing).
        return HttpClient(OkHttp)
        {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}

