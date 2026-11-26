package com.kuyermqi.quotawidget.domain

/**
 * Why a refresh failed. Persisted as a code instead of a human-readable message so every
 * surface renders it in the app language rather than the language of the build that stored it.
 */
sealed interface WidgetErrorKind {
    data object SessionExpired : WidgetErrorKind

    /** The platform answered, but nothing displayable came back; only the user can fix it. */
    data object QuotaUnavailable : WidgetErrorKind

    data class HttpStatus(val code: Int) : WidgetErrorKind

    /** Connection failure or timeout. */
    data object Network : WidgetErrorKind

    /** The configured base URL cannot be turned into a request. */
    data object InvalidBaseUrl : WidgetErrorKind

    /** The response could not be decoded. */
    data object ParseFailure : WidgetErrorKind

    /**
     * Anything else. [legacyText] holds a message persisted by a build that stored raw text:
     * it is kept for diagnostics only and must never be rendered as body text.
     */
    data class Unknown(val legacyText: String? = null) : WidgetErrorKind

    fun toStorage(): String = when (this) {
        SessionExpired -> STORAGE_SESSION_EXPIRED
        QuotaUnavailable -> STORAGE_QUOTA_UNAVAILABLE
        is HttpStatus -> "$STORAGE_HTTP_STATUS_PREFIX$code"
        Network -> STORAGE_NETWORK
        InvalidBaseUrl -> STORAGE_INVALID_BASE_URL
        ParseFailure -> STORAGE_PARSE_FAILURE
        is Unknown -> STORAGE_UNKNOWN
    }

    companion object {
        /** Never throws: anything unrecognized is kept as [Unknown.legacyText]. */
        fun fromStorage(value: String?): WidgetErrorKind {
            val raw = value?.trim().orEmpty()
            return when {
                raw == STORAGE_SESSION_EXPIRED -> SessionExpired
                raw == STORAGE_QUOTA_UNAVAILABLE -> QuotaUnavailable
                raw == STORAGE_NETWORK -> Network
                raw == STORAGE_INVALID_BASE_URL -> InvalidBaseUrl
                raw == STORAGE_PARSE_FAILURE -> ParseFailure
                raw == STORAGE_UNKNOWN -> Unknown()
                raw.startsWith(STORAGE_HTTP_STATUS_PREFIX) ->
                    raw.removePrefix(STORAGE_HTTP_STATUS_PREFIX)
                        .toIntOrNull()
                        ?.takeIf { it in 100..599 }
                        ?.let { HttpStatus(it) }
                        ?: Unknown()
                raw.isEmpty() -> Unknown()
                else -> Unknown(legacyText = raw)
            }
        }
    }
}

private const val STORAGE_SESSION_EXPIRED = "session_expired"
private const val STORAGE_QUOTA_UNAVAILABLE = "quota_unavailable"
private const val STORAGE_HTTP_STATUS_PREFIX = "http_status:"
private const val STORAGE_NETWORK = "network"
private const val STORAGE_INVALID_BASE_URL = "invalid_base_url"
private const val STORAGE_PARSE_FAILURE = "parse_failure"
private const val STORAGE_UNKNOWN = "unknown"
