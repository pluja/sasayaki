package com.sasayaki.di

import okhttp3.OkHttpClient

internal fun OkHttpClient.Builder.applyDebugLogging(): OkHttpClient.Builder = this
