package com.kuyermqi.quotawidget.codex

import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodexWindowClassifierTest {
    @Test
    fun classify_fiveHourWeeklyMonthly() {
        assertEquals(QuotaWindowKind.FIVE_HOUR, classifyWindow(18_000))
        assertEquals(QuotaWindowKind.WEEKLY, classifyWindow(604_800))
        assertEquals(QuotaWindowKind.MONTHLY, classifyWindow(2_592_000))
        assertNull(classifyWindow(null))
        assertNull(classifyWindow(0))
    }

    @Test
    fun usageResponse_mapsWindowsByDuration() {
        val dto = CodexUsageResponse(
            planType = "plus",
            rateLimit = CodexRateLimitDto(
                primaryWindow = CodexWindowDto(
                    usedPercent = 10.0,
                    limitWindowSeconds = 604_800,
                    resetAfterSeconds = 100,
                ),
                secondaryWindow = CodexWindowDto(
                    usedPercent = 40.0,
                    limitWindowSeconds = 2_592_000,
                    resetAfterSeconds = 200,
                ),
            ),
        )
        val windows = dto.toWindows()
        assertEquals(2, windows.size)
        assertEquals(QuotaWindowKind.WEEKLY, windows[0].kind)
        assertEquals(10.0, windows[0].usedPercent)
        assertEquals(QuotaWindowKind.MONTHLY, windows[1].kind)
        assertEquals(40.0, windows[1].usedPercent)
    }

    @Test
    fun usageResponse_freeMonthlyOnly() {
        val dto = CodexUsageResponse(
            planType = "free",
            rateLimit = CodexRateLimitDto(
                primaryWindow = CodexWindowDto(
                    usedPercent = 55.0,
                    limitWindowSeconds = 2_592_000,
                    resetAfterSeconds = 999,
                ),
                secondaryWindow = null,
            ),
        )
        val windows = dto.toWindows()
        assertEquals(listOf(QuotaWindowKind.MONTHLY), windows.map { it.kind })
    }

    @Test
    fun parseJwtClaims_extractsAccountId() {
        // {"https://api.openai.com/auth":{"chatgpt_account_id":"acc-1","chatgpt_plan_type":"plus"},"email":"a@b.c"}
        val payload =
            "eyJodHRwczovL2FwaS5vcGVuYWkuY29tL2F1dGgiOnsiY2hhdGdwdF9hY2NvdW50X2lkIjoiYWNjLTEiLCJjaGF0Z3B0X3BsYW5fdHlwZSI6InBsdXMifSwiZW1haWwiOiJhQGIuYyJ9"
        val claims = CodexOAuth.parseClaimsJson(
            """{"https://api.openai.com/auth":{"chatgpt_account_id":"acc-1","chatgpt_plan_type":"plus"},"email":"a@b.c"}""",
        )
        assertEquals("acc-1", claims.chatgptAccountId)
        assertEquals("plus", claims.chatgptPlanType)
        assertEquals("a@b.c", claims.email)
        // ensure JWT wrapper path also works
        val jwt = "x.$payload.y"
        val fromJwt = CodexOAuth.parseJwtClaims(jwt)
        assertEquals("acc-1", fromJwt.chatgptAccountId)
    }

    @Test
    fun pkce_challenge_isS256Base64Url() {
        val verifier = "a".repeat(128) // hex-like length
        val challenge = CodexOAuth.generateCodeChallenge(verifier)
        assertEquals(43, challenge.length) // SHA-256 → 32 bytes → 43 base64url no pad
        assertEquals(false, challenge.contains('='))
        assertEquals(false, challenge.contains('+'))
        assertEquals(false, challenge.contains('/'))
    }
}
