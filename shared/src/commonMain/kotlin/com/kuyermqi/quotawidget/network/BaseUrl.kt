package com.kuyermqi.quotawidget.network

/**
 * How risky a user-supplied base URL is. Plain HTTP is still allowed — self-hosted
 * servers on a LAN are a supported setup — but a publicly reachable plain HTTP
 * address sends the API key and the response in the clear.
 */
enum class BaseUrlClass {
    Invalid,
    PlainHttpLoopback,
    PlainHttpPrivate,
    PlainHttpPublic,
    Https,
}

/** Thrown instead of building a request from a malformed base URL. */
class InvalidBaseUrlException :
    IllegalArgumentException("Base URL 无效：需要以 http:// 或 https:// 开头且包含主机名")

fun classifyBaseUrl(baseUrl: String): BaseUrlClass {
    val parts = parseHttpUrl(baseUrl) ?: return BaseUrlClass.Invalid
    if (parts.isSecure) return BaseUrlClass.Https
    return when {
        parts.isLoopbackHost -> BaseUrlClass.PlainHttpLoopback
        parts.isPrivateHost -> BaseUrlClass.PlainHttpPrivate
        else -> BaseUrlClass.PlainHttpPublic
    }
}

/** Returns [baseUrl] unchanged, or throws when a request cannot be built from it. */
fun requireValidBaseUrl(baseUrl: String): String {
    if (classifyBaseUrl(baseUrl) == BaseUrlClass.Invalid) throw InvalidBaseUrlException()
    return baseUrl
}
