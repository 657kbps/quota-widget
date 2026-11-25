package com.kuyermqi.quotawidget.domain

import io.ktor.http.HttpStatusCode

/**
 * A platform endpoint answered with [statusCode] and the client could not turn it
 * into a snapshot. The code travels with the exception so the refresh pipeline can
 * classify retries from data instead of matching message text.
 */
class HttpStatusException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    override fun toString(): String =
        "${this::class.simpleName}(statusCode=$statusCode, message=$message)"
}

/** Retryable statuses: request timeout, rate limit, upstream failure. */
fun isRetryableHttpStatus(statusCode: Int): Boolean =
    statusCode == HttpStatusCode.RequestTimeout.value ||
        statusCode == HttpStatusCode.TooManyRequests.value ||
        statusCode in 500..599

/**
 * Maps a failed response onto the refresh pipeline vocabulary: missing or invalid
 * credentials surface as [SessionExpiredException] (the widget asks for re-login),
 * every other status keeps its code in [HttpStatusException].
 *
 * Clients that can prove a 403 means "entitlement not provisioned" throw
 * [QuotaUnavailableException] themselves instead of calling this.
 */
internal fun httpFailure(
    statusCode: Int,
    sessionExpiredMessage: String,
    failureMessage: String,
    cause: Throwable? = null,
): Exception = when (statusCode) {
    HttpStatusCode.Unauthorized.value,
    HttpStatusCode.Forbidden.value,
    -> SessionExpiredException(sessionExpiredMessage)
    else -> HttpStatusException(statusCode, failureMessage, cause)
}
