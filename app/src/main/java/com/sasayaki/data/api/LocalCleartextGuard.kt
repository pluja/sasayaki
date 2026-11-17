package com.sasayaki.data.api

import java.io.IOException
import java.net.Proxy
import okhttp3.Interceptor
import okhttp3.Response

class CleartextOutsideLocalNetworkException :
    IOException("Plain http only reaches servers on your local network, and this address leads outside it.")

/**
 * LocalCleartextGuard refuses plain http unless the connection it is about to write to goes
 * straight to a local network address. The address check in EndpointPolicy trusts names, and a
 * name can resolve anywhere; this runs after connecting and before any byte is sent.
 */
class LocalCleartextGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.isHttps) return chain.proceed(request)
        val route = chain.connection()?.route() ?: throw CleartextOutsideLocalNetworkException()
        val isDirect = route.proxy.type() == Proxy.Type.DIRECT
        if (!isDirect || !isLocalNetworkAddress(route.socketAddress.address)) throw CleartextOutsideLocalNetworkException()
        return chain.proceed(request)
    }
}
