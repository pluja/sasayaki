package com.sasayaki.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiClientFactory @Inject constructor(
    private val baseClient: OkHttpClient
) {
    private data class CacheKey(val baseUrl: String, val apiKey: String)

    private val retrofitCache = ConcurrentHashMap<CacheKey, Retrofit>()

    fun <T> create(serviceClass: Class<T>, baseUrl: String, apiKey: String): T {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val key = CacheKey(normalizedUrl, apiKey)

        val retrofit = retrofitCache.getOrPut(key) {
            val client = baseClient.newBuilder()
                .addInterceptor(authInterceptor(apiKey))
                .build()

            Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }

        return retrofit.create(serviceClass)
    }

    fun invalidate() {
        retrofitCache.clear()
    }

    private fun authInterceptor(apiKey: String) = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        chain.proceed(request)
    }
}
