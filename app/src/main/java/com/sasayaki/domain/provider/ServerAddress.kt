package com.sasayaki.domain.provider

import com.sasayaki.data.api.EndpointPolicy
import com.sasayaki.data.api.InvalidServerAddressException
import com.sasayaki.data.api.ServerAddressProblem

/** serverAddressError returns why [url] cannot be used, or null when it is usable or still empty. */
fun serverAddressError(url: String): ServerAddressProblem? {
    if (url.isBlank()) return null
    return try {
        EndpointPolicy.normalizeBaseUrl(url)
        null
    } catch (e: InvalidServerAddressException) {
        e.problem
    }
}

/** isUnencryptedAddress reports whether [url] is a usable address that sends requests over plain http. */
fun isUnencryptedAddress(url: String): Boolean = url.trim().startsWith("http://", ignoreCase = true) && serverAddressError(url) == null
