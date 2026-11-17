package com.sasayaki.domain.provider

import com.sasayaki.data.api.EndpointPolicy

/** serverAddressError returns why [url] cannot be used, or null when it is usable or still empty. */
fun serverAddressError(url: String): String? {
    if (url.isBlank()) return null
    return runCatching { EndpointPolicy.normalizeBaseUrl(url) }.exceptionOrNull()?.message
}

/** isUnencryptedAddress reports whether [url] is a usable address that sends requests over plain http. */
fun isUnencryptedAddress(url: String): Boolean = url.trim().startsWith("http://", ignoreCase = true) && serverAddressError(url) == null
