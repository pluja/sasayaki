package com.sasayaki.data.api

import com.sasayaki.di.NetworkModule
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class LocalCleartextGuardTest {
    private lateinit var server: HttpServer
    private val baseUrl get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/ok") { exchange ->
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.createContext("/moved") { exchange ->
            exchange.responseHeaders.add("Location", "http://example.com/stolen")
            exchange.sendResponseHeaders(307, -1)
            exchange.close()
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `plain http reaches a server on the local network`() {
        val response = NetworkModule.provideOkHttpClient().newCall(Request.Builder().url("$baseUrl/ok").build()).execute()

        assertEquals(200, response.code)
    }

    @Test
    fun `redirects are not followed`() {
        val response = NetworkModule.provideOkHttpClient().newCall(Request.Builder().url("$baseUrl/moved").build()).execute()

        assertEquals(307, response.code)
    }

    @Test
    fun `plain http through a proxy is refused before anything is sent`() {
        val client = NetworkModule.provideOkHttpClient().newBuilder()
            .proxy(Proxy(Proxy.Type.HTTP, server.address))
            .build()

        assertThrows(CleartextOutsideLocalNetworkException::class.java) {
            client.newCall(Request.Builder().url("http://speaches/ok").build()).execute()
        }
    }
}
