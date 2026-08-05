package com.kuyermqi.quotawidget.codex

import com.kuyermqi.quotawidget.domain.SessionExpiredException
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CodexAuthErrorsTest {
    @Test
    fun classify_unsupportedCountry_isRegionRestricted() {
        val body =
            """{"error":{"message":"Country, region, or territory not supported","type":"request_forbidden","code":"unsupported_country_region_territory"}}"""
        assertEquals(
            CodexAuthErrors.Kind.RegionRestricted,
            CodexAuthErrors.classify(HttpStatusCode.Forbidden, body),
        )
    }

    @Test
    fun classify_htmlForbiddenPage_isTransient() {
        val body = """
            <html>
              <head><title>Access denied</title></head>
              <body><div class="container">unsupported</div></body>
            </html>
        """.trimIndent()
        assertEquals(
            CodexAuthErrors.Kind.Transient,
            CodexAuthErrors.classify(HttpStatusCode.Forbidden, body),
        )
    }

    @Test
    fun classify_bareForbiddenJson_isTransient() {
        assertEquals(
            CodexAuthErrors.Kind.Transient,
            CodexAuthErrors.classify(HttpStatusCode.Forbidden, "{}"),
        )
    }

    @Test
    fun classify_accessDeniedSubstring_isTransient() {
        assertEquals(
            CodexAuthErrors.Kind.Transient,
            CodexAuthErrors.classify(
                HttpStatusCode.Forbidden,
                """{"error":{"message":"access_denied by policy"}}""",
            ),
        )
    }

    @Test
    fun classify_refreshTokenReused_isSessionExpired() {
        val body =
            """{"error":{"message":"Your refresh token has already been used","type":"invalid_request_error","code":"refresh_token_reused"}}"""
        assertEquals(
            CodexAuthErrors.Kind.SessionExpired,
            CodexAuthErrors.classify(HttpStatusCode.BadRequest, body),
        )
    }

    @Test
    fun classify_invalidGrant_isSessionExpired() {
        val body =
            """{"error":"invalid_grant","error_description":"Unknown or invalid refresh token."}"""
        assertEquals(
            CodexAuthErrors.Kind.SessionExpired,
            CodexAuthErrors.classify(HttpStatusCode.BadRequest, body),
        )
    }

    @Test
    fun classify_invalidToken_isSessionExpired() {
        assertEquals(
            CodexAuthErrors.Kind.SessionExpired,
            CodexAuthErrors.classify(
                HttpStatusCode.Forbidden,
                """{"error":{"code":"invalid_token"}}""",
            ),
        )
    }

    @Test
    fun classify_unauthorized_isSessionExpired() {
        assertEquals(
            CodexAuthErrors.Kind.SessionExpired,
            CodexAuthErrors.classify(HttpStatusCode.Unauthorized, ""),
        )
    }

    @Test
    fun classify_serverError_isTransient() {
        assertEquals(
            CodexAuthErrors.Kind.Transient,
            CodexAuthErrors.classify(HttpStatusCode.ServiceUnavailable, "upstream down"),
        )
    }

    @Test
    fun throwForHttpFailure_region_usesShortMessageAndCause() {
        val body =
            """{"error":{"message":"Country, region, or territory not supported","type":"request_forbidden","code":"unsupported_country_region_territory"}}"""
        val cause = IllegalArgumentException("http")
        val error = assertFailsWith<CodexRegionUnavailableException> {
            CodexAuthErrors.throwForHttpFailure(
                HttpStatusCode.Forbidden,
                body,
                source = "oauth",
                cause = cause,
            )
        }
        assertEquals(CodexAuthErrors.REGION_UNAVAILABLE_MESSAGE, error.message)
        assertSame(cause, error.cause)
    }

    @Test
    fun throwForHttpFailure_htmlForbidden_usesTransientMessage() {
        val body = "<html><body>blocked</body></html>"
        val error = assertFailsWith<IllegalStateException> {
            CodexAuthErrors.throwForHttpFailure(
                HttpStatusCode.Forbidden,
                body,
                source = "usage",
            )
        }
        assertEquals(CodexAuthErrors.TEMPORARILY_UNAVAILABLE_MESSAGE, error.message)
        assertNull(error.cause)
    }

    @Test
    fun throwForHttpFailure_invalidGrant_throwsSessionExpiredShortMessage() {
        val body =
            """{"error":"invalid_grant","error_description":"Unknown or invalid refresh token."}"""
        val error = assertFailsWith<SessionExpiredException> {
            CodexAuthErrors.throwForHttpFailure(
                HttpStatusCode.BadRequest,
                body,
                source = "oauth",
            )
        }
        assertEquals(CodexAuthErrors.SESSION_EXPIRED_MESSAGE, error.message)
        assertTrue(!error.message.orEmpty().contains("status="))
    }
}
