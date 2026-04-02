package com.sasayaki.di

import com.sasayaki.data.api.ApiClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .applyDebugLogging()
            .build()
    }

    @Provides
    @Singleton
    fun provideApiClientFactory(client: OkHttpClient): ApiClientFactory {
        return ApiClientFactory(client)
    }
}
