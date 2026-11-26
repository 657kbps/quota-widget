package com.kuyermqi.quotawidget.network

import kotlin.test.Test
import kotlin.test.assertEquals

class BaseUrlClassificationTest {
    @Test
    fun classifyBaseUrl_acceptsHttps() {
        assertEquals(BaseUrlClass.Https, classifyBaseUrl("https://api.example.com"))
        assertEquals(BaseUrlClass.Https, classifyBaseUrl("  https://api.example.com:8443/  "))
        assertEquals(BaseUrlClass.Https, classifyBaseUrl("HTTPS://API.EXAMPLE.COM"))
    }

    @Test
    fun classifyBaseUrl_flagsPublicPlainHttp() {
        assertEquals(BaseUrlClass.PlainHttpPublic, classifyBaseUrl("http://api.example.com"))
        assertEquals(BaseUrlClass.PlainHttpPublic, classifyBaseUrl("http://8.8.8.8"))
        assertEquals(BaseUrlClass.PlainHttpPublic, classifyBaseUrl("http://172.32.0.1:3000"))
        assertEquals(BaseUrlClass.PlainHttpPublic, classifyBaseUrl("http://[2001:db8::1]"))
    }

    @Test
    fun classifyBaseUrl_recognisesLoopback() {
        assertEquals(BaseUrlClass.PlainHttpLoopback, classifyBaseUrl("http://localhost:3425"))
        assertEquals(BaseUrlClass.PlainHttpLoopback, classifyBaseUrl("http://127.0.0.1:3000"))
        assertEquals(BaseUrlClass.PlainHttpLoopback, classifyBaseUrl("http://127.9.9.9"))
        assertEquals(BaseUrlClass.PlainHttpLoopback, classifyBaseUrl("http://[::1]:8080"))
    }

    @Test
    fun classifyBaseUrl_recognisesPrivateNetworks() {
        assertEquals(BaseUrlClass.PlainHttpPrivate, classifyBaseUrl("http://10.1.2.3"))
        assertEquals(BaseUrlClass.PlainHttpPrivate, classifyBaseUrl("http://172.16.0.1"))
        assertEquals(BaseUrlClass.PlainHttpPrivate, classifyBaseUrl("http://172.31.255.255"))
        assertEquals(BaseUrlClass.PlainHttpPrivate, classifyBaseUrl("http://192.168.1.10"))
        assertEquals(BaseUrlClass.PlainHttpPrivate, classifyBaseUrl("http://169.254.1.1"))
        assertEquals(BaseUrlClass.PlainHttpPrivate, classifyBaseUrl("http://[fd00::1]"))
        assertEquals(BaseUrlClass.PlainHttpPrivate, classifyBaseUrl("http://[fc00::1]"))
        assertEquals(BaseUrlClass.PlainHttpPrivate, classifyBaseUrl("http://[fe80::1]"))
    }

    @Test
    fun classifyBaseUrl_rejectsMalformedInput() {
        assertEquals(BaseUrlClass.Invalid, classifyBaseUrl(""))
        assertEquals(BaseUrlClass.Invalid, classifyBaseUrl("   "))
        assertEquals(BaseUrlClass.Invalid, classifyBaseUrl("api.example.com"))
        assertEquals(BaseUrlClass.Invalid, classifyBaseUrl("ftp://api.example.com"))
        assertEquals(BaseUrlClass.Invalid, classifyBaseUrl("http://"))
        assertEquals(BaseUrlClass.Invalid, classifyBaseUrl("https:///api"))
        assertEquals(BaseUrlClass.Invalid, classifyBaseUrl("http://exa mple.com"))
        assertEquals(BaseUrlClass.Invalid, classifyBaseUrl("http://:8080"))
        assertEquals(BaseUrlClass.Invalid, classifyBaseUrl("http://api.example.com:not-a-port"))
    }

    @Test
    fun classifyBaseUrl_ignoresUserInfoWhenReadingTheHost() {
        assertEquals(BaseUrlClass.PlainHttpLoopback, classifyBaseUrl("http://user:pw@127.0.0.1:3000"))
    }

    @Test
    fun requireValidBaseUrl_throwsOnlyForInvalidUrls() {
        assertEquals("http://10.0.0.5:3000", requireValidBaseUrl("http://10.0.0.5:3000"))
        assertEquals("https://api.example.com", requireValidBaseUrl("https://api.example.com"))
        assertEquals(
            "InvalidBaseUrlException",
            runCatching { requireValidBaseUrl("api.example.com") }.exceptionOrNull()?.let {
                it::class.simpleName
            },
        )
        assertEquals(
            "InvalidBaseUrlException",
            runCatching { requireValidBaseUrl("http://") }.exceptionOrNull()?.let {
                it::class.simpleName
            },
        )
    }
}
