package com.sasayaki.domain.provider

import com.sasayaki.data.api.CleartextOutsideLocalNetworkException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import retrofit2.HttpException

fun describeConnectionError(error: Throwable): String = when (error) {
    is IllegalArgumentException -> error.message ?: "That address is not valid."

    is UnknownHostException -> "Can't find that server. Check the address."

    is CleartextOutsideLocalNetworkException -> "This http address leads outside your local network. Use https:// instead."

    is SocketTimeoutException -> "The server took too long to answer."

    is SSLException -> "Secure connection failed. The server needs a valid HTTPS certificate."

    is ConnectException -> "Couldn't connect to the server."

    is HttpException -> when (error.code()) {
        401, 403 -> "The server rejected the API key."
        404 -> "Nothing found at this address. Check the URL."
        429 -> "The server is rate limiting requests. Try again shortly."
        in 500..599 -> "The server had an error (${error.code()})."
        else -> "The server answered with error ${error.code()}."
    }

    else -> error.message ?: "Something went wrong."
}
