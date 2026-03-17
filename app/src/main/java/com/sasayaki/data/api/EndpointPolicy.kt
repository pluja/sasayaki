package com.sasayaki.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object EndpointPolicy {
    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmedBaseUrl = baseUrl.trim()
        require(trimmedBaseUrl.isNotEmpty()) { "Base URL is required." }

        val normalizedUrl = if (trimmedBaseUrl.endsWith("/")) {
            trimmedBaseUrl
        } else {
            "$trimmedBaseUrl/"
        }

        val parsedUrl = normalizedUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Base URL is invalid.")

        require(parsedUrl.isHttps) { "Only HTTPS base URLs are allowed." }

        return parsedUrl.toString()
    }
}
