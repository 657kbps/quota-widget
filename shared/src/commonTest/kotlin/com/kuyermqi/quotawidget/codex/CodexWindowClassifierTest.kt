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
    fun usageResponse_resetAtSeconds_fallsBackToRemainingSeconds() {
        val now = 1_700_000_000_000L
        val windows = singleWindow(
            CodexWindowDto(
                usedPercent = 5.0,
                limitWindowSeconds = 18_000,
                resetAt = now / 1000L + 3_600L,
            ),
        ).toWindows(nowEpochMs = now)

        assertEquals(3_600L, windows.single().resetInSec)
    }

    @Test
    fun usageResponse_resetAtMillis_fallsBackToRemainingSeconds() {
        val now = 1_700_000_000_000L
        val windows = singleWindow(
            CodexWindowDto(
                usedPercent = 5.0,
                limitWindowSeconds = 604_800,
                resetAt = now + 90_000L,
            ),
        ).toWindows(nowEpochMs = now)

        assertEquals(90L, windows.single().resetInSec)
    }

    @Test
    fun usageResponse_resetAfterSecondsTakesPrecedence() {
        val now = 1_700_000_000_000L
        val windows = singleWindow(
            CodexWindowDto(
                usedPercent = 5.0,
                limitWindowSeconds = 18_000,
                resetAfterSeconds = 12,
                resetAt = now / 1000L + 3_600L,
            ),
        ).toWindows(nowEpochMs = now)

        assertEquals(12L, windows.single().resetInSec)
    }

    @Test
    fun usageResponse_elapsedReset_clampsToZero() {
        val now = 1_700_000_000_000L
        val fromMillis = singleWindow(
            CodexWindowDto(
                usedPercent = 5.0,
                limitWindowSeconds = 18_000,
                resetAt = now - 60_000L,
            ),
        ).toWindows(nowEpochMs = now)
        val fromNegativeSeconds = singleWindow(
            CodexWindowDto(
                usedPercent = 5.0,
                limitWindowSeconds = 18_000,
                resetAfterSeconds = -5L,
            ),
        ).toWindows(nowEpochMs = now)

        assertEquals(0L, fromMillis.single().resetInSec)
        assertEquals(0L, fromNegativeSeconds.single().resetInSec)
    }

    @Test
    fun usageResponse_withoutResetFields_leavesResetNull() {
        val windows = singleWindow(
            CodexWindowDto(usedPercent = 5.0, limitWindowSeconds = 18_000),
        ).toWindows(nowEpochMs = 1_700_000_000_000L)

        assertNull(windows.single().resetInSec)
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

    private fun singleWindow(window: CodexWindowDto): CodexUsageResponse =
        CodexUsageResponse(rateLimit = CodexRateLimitDto(primaryWindow = window))
}