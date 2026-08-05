package com.kuyermqi.quotawidget.codex

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodexDebugLogTest {
    @Test
    fun tokenFp_redactsValue() {
        val fp = CodexDebugLog.tokenFp("abcdefghijklmnopqrstuvwxyz0123456789")
        assertEquals("len=36 tail=6789", fp)
        assertFalse(fp.contains("abcdefgh"))
    }

    @Test
    fun summarizeOAuthErrorBody_extractsNestedCode() {
        val summary = CodexDebugLog.summarizeOAuthErrorBody(
            """{"error":{"message":"Your refresh token has already been used","type":"invalid_request_error","code":"refresh_token_reused"}}""",
        )
        assertContains(summary, "code=refresh_token_reused")
        assertContains(summary, "type=invalid_request_error")
        assertContains(summary, "already been used")
    }

    @Test
    fun summarizeOAuthErrorBody_sanitizesRawJwt() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.signaturepart"
        val summary = CodexDebugLog.summarizeOAuthErrorBody("""{"raw":"$jwt"}""")
        assertContains(summary, "<jwt>")
        assertFalse(summary.contains("eyJhbGciOiJIUzI1NiJ9"))
    }
}
