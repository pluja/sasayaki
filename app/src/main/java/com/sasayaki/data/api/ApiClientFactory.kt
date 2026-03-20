package com.sasayaki.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiClientFactory @Inject constructor(
    private val baseClient: OkHttpClient
) {
    fun <T> create(serviceClass: Class<T>, baseUrl: String, apiKey: String): T {
        val normalizedUrl = EndpointPolicy.normalizeBaseUrl(baseUrl)
        val client = baseClient.newBuilder()
            .addInterceptor(authInterceptor(apiKey.trim()))
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(serviceClass)
    }

    private fun authInterceptor(apiKey: String) = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        chain.proceed(request)
    }
}
