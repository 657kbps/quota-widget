package com.kuyermqi.quotawidget.network

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.Sender
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.utils.EmptyContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.takeFrom

enum class RedirectDecision {
    Follow,
    FollowWithoutSensitiveHeaders,
    Reject,
}

/** Raised when a response redirects an HTTPS request to plain HTTP. */
class InsecureRedirectException : IllegalStateException(
    "拒绝不安全的重定向：HTTPS 请求被重定向到明文 HTTP，凭据与数据可能被截获",
)

class TooManyRedirectsException(limit: Int) : IllegalStateException("重定向次数超过 $limit 次")

internal const val MAX_REDIRECTS = 5

private val SENSITIVE_HEADERS = listOf(HttpHeaders.Authorization, "X-Api-Key", HttpHeaders.Cookie)
private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

/**
 * Decides whether a redirect may be followed.
 *
 * - HTTPS to plain HTTP is refused outright: the next request would expose the
 *   credential it was about to carry.
 * - Every other hop is followed; credentials survive only while the target keeps
 *   the same host and port (an http to https upgrade of that same authority
 *   counts), because anything else would hand them to a different party.
 */
fun classifyRedirect(fromUrl: String, toUrl: String): RedirectDecision {
    val from = parseHttpUrl(fromUrl) ?: return RedirectDecision.Reject
    val to = parseHttpUrl(toUrl) ?: return RedirectDecision.Reject
    if (from.isSecure && !to.isSecure) return RedirectDecision.Reject
    val sameAuthority = from.host == to.host && from.explicitPort == to.explicitPort
    return if (sameAuthority) {
        RedirectDecision.Follow
    } else {
        RedirectDecision.FollowWithoutSensitiveHeaders
    }
}

/**
 * Follows redirects manually, at most [MAX_REDIRECTS] hops. The engine must be
 * configured with its own redirect following disabled (`followRedirects = false`)
 * so that every hop passes [classifyRedirect] first.
 */
fun HttpClient.followRedirectsSafely(): HttpClient {
    plugin(HttpSend).intercept { request -> executeFollowingRedirects(request) }
    return this
}

private suspend fun Sender.executeFollowingRedirects(initial: HttpRequestBuilder): HttpClientCall {
    var current = initial
    var hops = 0
    while (true) {
        val call = execute(current)
        val statusCode = call.response.status.value
        if (statusCode !in REDIRECT_STATUS_CODES) return call
        val location = call.response.headers[HttpHeaders.Location]
        if (location.isNullOrBlank()) return call

        val currentUrl = current.url.buildString()
        val nextUrl = resolveRedirectUrl(currentUrl, location)
            ?: throw IllegalStateException("重定向目标无效，已停止请求")
        if (parseHttpUrl(nextUrl) == null) throw IllegalStateException("重定向目标无效，已停止请求")
        val decision = classifyRedirect(currentUrl, nextUrl)
        if (decision == RedirectDecision.Reject) throw InsecureRedirectException()
        if (hops >= MAX_REDIRECTS) throw TooManyRedirectsException(MAX_REDIRECTS)

        current = redirectRequest(
            current = current,
            url = nextUrl,
            statusCode = statusCode,
            dropSensitiveHeaders = decision == RedirectDecision.FollowWithoutSensitiveHeaders,
        )
        hops++
    }
}

private fun redirectRequest(
    current: HttpRequestBuilder,
    url: String,
    statusCode: Int,
    dropSensitiveHeaders: Boolean,
): HttpRequestBuilder {
    val next = HttpRequestBuilder().apply { takeFrom(current) }
    next.url.takeFrom(url)
    if (statusCode == 301 || statusCode == 302 || statusCode == 303) {
        next.method = if (current.method == HttpMethod.Head) HttpMethod.Head else HttpMethod.Get
        next.setBody(EmptyContent)
    }
    if (dropSensitiveHeaders) {
        for (name in SENSITIVE_HEADERS) next.headers.remove(name)
    }
    return next
}
