package com.sasayaki.data.api

import androidx.annotation.StringRes
import com.sasayaki.R
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class ServerAddressProblem(@StringRes val message: Int) {
    EMPTY(R.string.error_address_empty),
    INVALID(R.string.error_address_invalid),
    CLEARTEXT_OUTSIDE_LOCAL_NETWORK(R.string.error_address_cleartext)
}

class InvalidServerAddressException(val problem: ServerAddressProblem) : IllegalArgumentException(problem.name)

object EndpointPolicy {
    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmedBaseUrl = baseUrl.trim()
        if (trimmedBaseUrl.isEmpty()) throw InvalidServerAddressException(ServerAddressProblem.EMPTY)

        // Request paths already start with v1/, but providers document their base URL
        // with it included, so a pasted ".../v1" would otherwise become ".../v1/v1/".
        val normalizedUrl = trimmedBaseUrl.trimEnd('/').removeSuffix("/v1") + "/"

        val parsedUrl = normalizedUrl.toHttpUrlOrNull()
            ?: throw InvalidServerAddressException(ServerAddressProblem.INVALID)

        if (!parsedUrl.isHttps && !isLocalNetworkHost(parsedUrl.host)) {
            throw InvalidServerAddressException(ServerAddressProblem.CLEARTEXT_OUTSIDE_LOCAL_NETWORK)
        }

        return parsedUrl.toString()
    }
}
