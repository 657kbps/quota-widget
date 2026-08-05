package com.kuyermqi.quotawidget.codex

import com.kuyermqi.quotawidget.domain.SessionExpiredException
import io.ktor.http.HttpStatusCode

/**
 * Classifies Codex OAuth / usage HTTP failures so geo blocks are not treated as logout.
 */
internal object CodexAuthErrors {
    const val REGION_UNAVAILABLE_MESSAGE = "地区不可用"
    const val TEMPORARILY_UNAVAILABLE_MESSAGE = "暂时无法访问"
    const val SESSION_EXPIRED_MESSAGE = "Codex 登录已失效，请重新登录"

    fun throwForHttpFailure(
        status: HttpStatusCode,
        body: String,
        source: String,
        cause: Throwable? = null,
    ): Nothing {
        val summary = CodexDebugLog.summarizeOAuthErrorBody(body)
        val kind = classify(status, body)
        CodexDebugLog.w(
            "$source.classified kind=$kind status=$status $summary",
        )
        when (kind) {
            Kind.RegionRestricted ->
                throw CodexRegionUnavailableException(REGION_UNAVAILABLE_MESSAGE, cause)
            Kind.SessionExpired ->
                throw SessionExpiredException(SESSION_EXPIRED_MESSAGE)
            Kind.Transient ->
                throw IllegalStateException(TEMPORARILY_UNAVAILABLE_MESSAGE, cause)
        }
    }

    fun classify(status: HttpStatusCode, body: String): Kind {
        if (isRegionRestricted(body)) return Kind.RegionRestricted
        if (isSessionExpired(status, body)) return Kind.SessionExpired
        return Kind.Transient
    }

    private fun isRegionRestricted(body: String): Boolean {
        val lower = body.lowercase()
        if (lower.contains("unsupported_country_region_territory")) return true
        if (lower.contains("request_forbidden") &&
            (lower.contains("country") || lower.contains("region") || lower.contains("territory"))
        ) {
            return true
        }
        return false
    }

    private fun isSessionExpired(status: HttpStatusCode, body: String): Boolean {
        if (status == HttpStatusCode.Unauthorized) return true
        val lower = body.lowercase()
        return lower.contains("refresh_token_reused") ||
            lower.contains("invalid_grant") ||
            lower.contains("invalid_token")
    }

    enum class Kind {
        RegionRestricted,
        SessionExpired,
        Transient,
    }
}

internal class CodexRegionUnavailableException(
    message: String = CodexAuthErrors.REGION_UNAVAILABLE_MESSAGE,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
