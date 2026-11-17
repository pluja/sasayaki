package com.sasayaki.domain.provider

import android.content.res.Resources
import com.sasayaki.R
import com.sasayaki.data.api.CleartextOutsideLocalNetworkException
import com.sasayaki.data.api.InvalidServerAddressException
import com.sasayaki.domain.transcription.MissingTranscriptException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import retrofit2.HttpException

fun describeConnectionError(error: Throwable, resources: Resources): String = when (error) {
    is InvalidServerAddressException -> resources.getString(error.problem.message)

    is IllegalArgumentException -> resources.getString(R.string.error_address_invalid)

    is CleartextOutsideLocalNetworkException -> resources.getString(R.string.error_cleartext_outside_local_network)

    is MissingTranscriptException -> resources.getString(R.string.error_no_transcript)

    is UnknownHostException -> resources.getString(R.string.error_unknown_host)

    is SocketTimeoutException -> resources.getString(R.string.error_timeout)

    is SSLException -> resources.getString(R.string.error_tls)

    is ConnectException -> resources.getString(R.string.error_connect)

    is HttpException -> when (error.code()) {
        401, 403 -> resources.getString(R.string.error_api_key_rejected)
        404 -> resources.getString(R.string.error_not_found)
        429 -> resources.getString(R.string.error_rate_limited)
        in 500..599 -> resources.getString(R.string.error_server, error.code())
        else -> resources.getString(R.string.error_http, error.code())
    }

    else -> error.message ?: resources.getString(R.string.error_generic)
}
