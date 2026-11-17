package com.sasayaki.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
    @Test
    fun `server root gains a trailing slash`() {
        assertEquals("https://api.example.com/", EndpointPolicy.normalizeBaseUrl("https://api.example.com"))
    }

    @Test
    fun `documented v1 suffix is dropped so paths are not doubled`() {
        assertEquals("https://api.example.com/", EndpointPolicy.normalizeBaseUrl("https://api.example.com/v1"))
        assertEquals("https://api.example.com/", EndpointPolicy.normalizeBaseUrl(" https://api.example.com/v1/ "))
        assertEquals("https://api.example.com/openai/", EndpointPolicy.normalizeBaseUrl("https://api.example.com/openai/v1"))
    }

    @Test
    fun `paths that only contain v1 are kept`() {
        assertEquals("https://example.com/v1beta/", EndpointPolicy.normalizeBaseUrl("https://example.com/v1beta"))
    }

    @Test
    fun `plain http is accepted for servers on the local network`() {
        listOf(
            "http://192.168.1.20:8000",
            "http://10.0.0.5",
            "http://172.20.1.1",
            "http://127.0.0.1:8080/v1",
            "http://100.101.102.103",
            "http://[fd12:3456::1]:8000",
            "http://[fe80::1]",
            "http://localhost:8000",
            "http://speaches",
            "http://asr.home.lan",
            "http://whisper.local",
            "http://gpu.home.arpa",
            "http://llm.internal",
            "http://169.254.10.1",
            "http://100.64.0.1",
            "http://WHISPER.LOCAL.",
            "http://Speaches"
        ).forEach { url ->
            assertTrue(url, EndpointPolicy.normalizeBaseUrl(url).startsWith("http://"))
        }
    }

    @Test
    fun `plain http is rejected for public servers`() {
        listOf(
            "http://example.com",
            "http://8.8.8.8",
            "http://172.32.0.1",
            "http://172.15.0.1",
            "http://100.63.255.255",
            "http://100.128.0.1",
            "http://[2001:db8::1]",
            "http://[::ffff:8.8.8.8]",
            "http://999.1.1.1",
            "http://134744072"
        ).forEach { url ->
            assertThrows(url, IllegalArgumentException::class.java) { EndpointPolicy.normalizeBaseUrl(url) }
        }
    }

    @Test
    fun `cleartext and malformed urls are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { EndpointPolicy.normalizeBaseUrl("http://example.com") }
        assertThrows(IllegalArgumentException::class.java) { EndpointPolicy.normalizeBaseUrl("not a url") }
        assertThrows(IllegalArgumentException::class.java) { EndpointPolicy.normalizeBaseUrl("  ") }
    }
}
