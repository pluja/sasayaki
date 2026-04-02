package com.sasayaki.di

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

internal fun OkHttpClient.Builder.applyDebugLogging(): OkHttpClient.Builder {
    return addInterceptor(
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    )
}
