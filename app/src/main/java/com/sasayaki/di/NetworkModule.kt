package com.sasayaki.di

import com.sasayaki.data.api.ApiClientFactory
import com.sasayaki.data.api.LocalCleartextGuard
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        // Cleartext is allowed for local servers, so no server may redirect a recording
        // elsewhere; OpenAI-compatible APIs answer in place.
        .followRedirects(false)
        .followSslRedirects(false)
        .addNetworkInterceptor(LocalCleartextGuard())
        .applyDebugLogging()
        .build()

    @Provides
    @Singleton
    fun provideApiClientFactory(client: OkHttpClient): ApiClientFactory = ApiClientFactory(client)
}
